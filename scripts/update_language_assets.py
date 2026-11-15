"""Refresh pinned, bundled language data. Development only; no app downloads."""

from pathlib import Path
import re
from urllib.request import urlopen
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
WORD_REVISION = "525f9b560de45753a5ea01069454e72e9aa541c6"
CLDR_REVISION = "release-48"
EMOJI_VERSION = "16.0"
EMOJI_DATA_URL = f"https://www.unicode.org/Public/emoji/{EMOJI_VERSION}/emoji-test.txt"
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


def update_emoji_catalog():
    source = download(EMOJI_DATA_URL)
    assert f"# Version: {EMOJI_VERSION}" in source
    target = ROOT / "android/app/src/main/kotlin/app/aster/aster_keyboard/ime/EmojiCatalog.kt"
    catalog = target.read_text(encoding="utf-8")
    categories = {
        category: values.split()
        for category, values in re.findall(r'^\s*"([^"]+)" to "([^"]+)"', catalog, re.MULTILINE)
    }
    group_categories = {
        "Smileys & Emotion": "Faces", "People & Body": "People",
        "Animals & Nature": "Nature", "Food & Drink": "Food",
        "Travel & Places": "Travel", "Activities": "Things",
        "Objects": "Things", "Symbols": "Symbols", "Flags": "Flags",
    }
    known = {emoji for values in categories.values() for emoji in values}
    assert len(known) == sum(map(len, categories.values())), "Duplicate catalog emoji"
    qualified = set()
    group = subgroup = ""
    for line in source.splitlines():
        if line.startswith("# group: "):
            group = line.removeprefix("# group: ")
        elif line.startswith("# subgroup: "):
            subgroup = line.removeprefix("# subgroup: ")
        elif "; fully-qualified" in line:
            emoji = "".join(chr(int(value, 16)) for value in line.split(";")[0].split())
            qualified.add(emoji)
            if any(0x1F3FB <= ord(value) <= 0x1F3FF for value in emoji) or emoji in known:
                continue
            category = group_categories[group]
            if group == "Smileys & Emotion" and subgroup in ("heart", "emotion"):
                category = "Symbols"
            elif subgroup == "sky & weather":
                category = "Nature"
            categories[category].append(emoji)
            known.add(emoji)
    assert known <= qualified, "Catalog contains non-standard emoji sequences"
    # Keep existing category positions and order; append missing standard emoji.
    catalog = re.sub(
        r'^(\s*)"([^"]+)" to "[^"]+"',
        lambda match: f'{match[1]}"{match[2]}" to "{" ".join(categories[match[2]])}"',
        catalog, flags=re.MULTILINE,
    )
    # Only offer variants when every generated sequence is explicitly in Unicode's data.
    skin_tone = [
        emoji for values in categories.values() for emoji in values
        if all(emoji[0] + chr(tone) + emoji[1:].removeprefix("\ufe0f") in qualified
               for tone in range(0x1F3FB, 0x1F400))
    ]
    catalog = re.sub(
        r'    private val skinTone = setOf\([^\n]+\)',
        '    private val skinTone = setOf(' + ', '.join(f'"{emoji}"' for emoji in skin_tone) + ')',
        catalog,
    )
    target.write_text(catalog, encoding="utf-8", newline="\n")
    print(f"{len(known)} catalog emoji; {len(skin_tone)} support five skin tones")


def update_emoji():
    update_emoji_catalog()
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
        "Emoji catalog, names and search keywords\n\n"
        f"Unicode Emoji {EMOJI_VERSION}, fully-qualified emoji and skin-tone sequences.\n"
        f"{EMOJI_DATA_URL}\n"
        "Adapted into EmojiCatalog.kt: base emoji grouped for Aster and supported skin-tone variants.\n\n"
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
