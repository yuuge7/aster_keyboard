"""Refresh pinned, bundled language data. Development only; no app downloads."""

from pathlib import Path
import re
from urllib.request import urlopen
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
WORD_REVISION = "525f9b560de45753a5ea01069454e72e9aa541c6"
CLDR_REVISION = "release-48"
RAW = "https://raw.githubusercontent.com"


def download(url):
    with urlopen(url, timeout=120) as response:
        return response.read().decode("utf-8")


def update_words():
    for language in ("en", "ro"):
        url = f"{RAW}/hermitdave/FrequencyWords/{WORD_REVISION}/content/2018/{language}/{language}_full.txt"
        # Stop after 100k source rows rather than downloading the entire corpus.
        with urlopen(url, timeout=120) as response:
            rows = [next(response).decode("utf-8") for _ in range(100_000)]
        target = ROOT / f"android/app/src/main/assets/dictionaries/{language}.txt"
        previous = target.read_text(encoding="utf-8").splitlines()
        assert [row.rstrip("\r\n") for row in rows[:50_000]] == previous[:50_000]
        target.write_text("".join(rows), encoding="utf-8", newline="\n")
        print(f"{language}: {len(rows)} frequency entries")
    target = ROOT / "android/app/src/main/assets/dictionaries/NOTICE.txt"
    notice = target.read_text(encoding="utf-8")
    notice = notice.replace(
        "Files: content/2018/en/en_50k.txt and content/2018/ro/ro_50k.txt\nThe bundled source wordlists are unmodified and licensed CC BY-SA 4.0.",
        "Files: content/2018/en/en_full.txt and content/2018/ro/ro_full.txt\n"
        "Adaptation: the first 100,000 source entries of each full frequency list.\n"
        "The entries retain their source spelling, counts and order, under CC BY-SA 4.0.",
    )
    target.write_text(notice, encoding="utf-8", newline="\n")
    (ROOT / "assets/licenses/wordlists.txt").write_text(notice, encoding="utf-8", newline="\n")


def update_emoji():
    annotations = {}
    for language in ("en", "ro"):
        entries = {}
        for folder in ("annotations", "annotationsDerived"):
            url = f"{RAW}/unicode-org/cldr/{CLDR_REVISION}/common/{folder}/{language}.xml"
            for element in ET.fromstring(download(url)).iter("annotation"):
                emoji = element.attrib["cp"].replace("\ufe0f", "")
                entry = entries.setdefault(emoji, {"name": "", "keywords": []})
                if element.get("type") == "tts":
                    entry["name"] = element.text or ""
                elif element.text:
                    entry["keywords"].extend(element.text.split(" | "))
        annotations[language] = entries
    catalog = (ROOT / "android/app/src/main/kotlin/app/aster/aster_keyboard/ime/EmojiCatalog.kt").read_text(encoding="utf-8")
    rows = []
    for category, values in re.findall(r'^\s*"([^"]+)" to "([^"]+)"', catalog, re.MULTILINE):
        for emoji in values.split():
            key = emoji.replace("\ufe0f", "")
            en = annotations["en"][key]
            ro = annotations["ro"][key]
            aliases = en["keywords"] + ro["keywords"] + [category]
            rows.append("\t".join((emoji, en["name"], ro["name"], " | ".join(dict.fromkeys(aliases)))))
    target = ROOT / "android/app/src/main/assets/emoji/search.tsv"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(rows) + "\n", encoding="utf-8", newline="\n")
    license_text = download(f"{RAW}/unicode-org/cldr/{CLDR_REVISION}/LICENSE")
    notice = (
        "Emoji names and search keywords\n\n"
        f"Unicode CLDR {CLDR_REVISION}, English and Romanian annotations and annotationsDerived.\n"
        f"https://github.com/unicode-org/cldr/tree/{CLDR_REVISION}/common\n"
        "Adapted into search.tsv: selected Aster emoji, names and combined keywords.\n"
        "The data is bundled; the app never downloads it.\n\n"
    ) + license_text
    (target.parent / "NOTICE.txt").write_text(notice, encoding="utf-8", newline="\n")
    (ROOT / "assets/licenses/emoji.txt").write_text(notice, encoding="utf-8", newline="\n")
    print(f"{len(rows)} emoji with English and Romanian search keywords")


if __name__ == "__main__":
    update_words()
    update_emoji()
