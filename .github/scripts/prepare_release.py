"""Apply a unique, increasing release version to this CI checkout only."""

import argparse
import os
from pathlib import Path
import re


VERSION_LINE = re.compile(r"^version: (\d+)\.(\d+)\.(\d+)\+(\d+)[ \t]*$", re.MULTILINE)
MAX_ANDROID_VERSION_CODE = 2_100_000_000


def release_version(pubspec: str, run_number: int) -> dict[str, str]:
    if run_number < 1:
        raise ValueError("The GitHub workflow run number must be positive.")
    matches = list(VERSION_LINE.finditer(pubspec))
    if len(matches) != 1:
        raise ValueError("Expected exactly one version: major.minor.patch+build in pubspec.yaml.")
    major, minor, _patch, baseline_build = map(int, matches[0].groups())
    build_number = baseline_build + run_number
    if build_number > MAX_ANDROID_VERSION_CODE:
        raise ValueError("Android versionCode would exceed 2,100,000,000.")
    display_version = f"{major}.{minor + run_number}"
    return {
        "version_name": f"{display_version}.0",
        "build_number": str(build_number),
        "tag": f"v{display_version}",
        "title": f"Aster Keyboard v{display_version}",
        "apk_name": f"Aster-Keyboard-v{display_version}.apk",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-number", type=int, default=os.getenv("GITHUB_RUN_NUMBER"))
    parser.add_argument("--pubspec", type=Path, default=Path("pubspec.yaml"))
    args = parser.parse_args()
    if args.run_number is None:
        parser.error("--run-number or GITHUB_RUN_NUMBER is required")
    source = args.pubspec.read_bytes().decode("utf-8").replace("\r\n", "\n")
    values = release_version(source, args.run_number)
    version = f"{values['version_name']}+{values['build_number']}"
    args.pubspec.write_text(VERSION_LINE.sub(f"version: {version}", source), encoding="utf-8")
    if output_path := os.getenv("GITHUB_OUTPUT"):
        with open(output_path, "a", encoding="utf-8") as output:
            for key, value in values.items():
                output.write(f"{key}={value}\n")
    print(f"{values['title']} (Android {version})")


if __name__ == "__main__":
    main()
