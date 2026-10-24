"""Create or restore private Android signing files without overwriting them."""

import argparse
import base64
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
KEYSTORE = ROOT / "aster-release.jks"
PROPERTIES = ROOT / "key.properties"
ALIAS = "aster"


def properties_value(value: str) -> str:
    """Escape Java properties values, including passwords with special characters."""
    escaped = []
    replacements = {"\\": "\\\\", "\n": "\\n", "\r": "\\r", "\t": "\\t", " ": "\\ "}
    for char in value:
        if ord(char) < 128:
            escaped.append(replacements.get(char, char))
        else:
            # Java properties escape UTF-16 code units, including surrogate pairs.
            encoded = char.encode("utf-16-be")
            escaped.extend(
                f"\\u{int.from_bytes(encoded[index:index + 2], 'big'):04x}"
                for index in range(0, len(encoded), 2)
            )
    return "".join(escaped)


def write_properties(store_password: str, key_password: str, alias: str) -> None:
    contents = (
        "# PRIVATE: back up this file together with aster-release.jks.\n"
        "# storeFile is relative to the project root, on every machine.\n"
        "storeFile=aster-release.jks\n"
        f"storePassword={properties_value(store_password)}\n"
        f"keyPassword={properties_value(key_password)}\n"
        f"keyAlias={properties_value(alias)}\n"
    )
    with open(os.open(PROPERTIES, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w", encoding="ascii") as output:
        output.write(contents)


def keytool_path() -> str:
    if java_home := os.getenv("JAVA_HOME"):
        executable = Path(java_home, "bin", "keytool.exe" if os.name == "nt" else "keytool")
        if executable.is_file():
            return str(executable)
    if executable := shutil.which("keytool"):
        return executable
    raise SystemExit("keytool was not found. Install JDK 17 and add its bin directory to PATH or set JAVA_HOME.")


def generate() -> None:
    tool = keytool_path()
    password = secrets.token_urlsafe(36)
    environment = {**os.environ, "ASTER_GENERATED_KEY_PASSWORD": password}
    subprocess.run(
        [tool, "-genkeypair", "-keystore", str(KEYSTORE), "-storetype", "JKS",
         "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
         "-dname", "CN=Aster Keyboard, OU=Android Release, O=Aster Keyboard",
         "-storepass:env", "ASTER_GENERATED_KEY_PASSWORD", "-keypass:env", "ASTER_GENERATED_KEY_PASSWORD",
         "-noprompt"],
        env=environment, check=True, capture_output=True,
    )
    KEYSTORE.chmod(0o600)
    write_properties(password, password, ALIAS)
    certificate = subprocess.run(
        [tool, "-exportcert", "-keystore", str(KEYSTORE), "-alias", ALIAS,
         "-storepass:env", "ASTER_GENERATED_KEY_PASSWORD"],
        env=environment, check=True, capture_output=True,
    ).stdout
    print("Created aster-release.jks and key.properties in the project root.")
    print(f"Certificate SHA-256: {hashlib.sha256(certificate).hexdigest()}")
    print("Back up BOTH private files before publishing. Passwords are in key.properties.")


def restore() -> None:
    names = ("ANDROID_KEYSTORE_BASE64", "ANDROID_STORE_PASSWORD", "ANDROID_KEY_PASSWORD", "ANDROID_KEY_ALIAS")
    missing = [name for name in names if not os.getenv(name)]
    if missing:
        raise SystemExit("Missing GitHub Actions secrets: " + ", ".join(missing))
    try:
        data = base64.b64decode("".join(os.environ[names[0]].split()), validate=True)
    except ValueError:
        raise SystemExit("ANDROID_KEYSTORE_BASE64 is not valid base64.") from None
    if not data:
        raise SystemExit("The decoded signing keystore is empty.")
    with open(os.open(KEYSTORE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as output:
        output.write(data)
    write_properties(*(os.environ[name] for name in names[1:]))
    print("Restored private release signing files from GitHub Actions secrets.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("generate", "restore"))
    args = parser.parse_args()
    if KEYSTORE.exists() or PROPERTIES.exists():
        raise SystemExit("Signing files already exist. Refusing to overwrite a signing identity.")
    try:
        generate() if args.command == "generate" else restore()
    except subprocess.CalledProcessError as error:
        raise SystemExit(f"keytool failed with exit code {error.returncode}. Existing files were preserved.") from None


if __name__ == "__main__":
    main()
