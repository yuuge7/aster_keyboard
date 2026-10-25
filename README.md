# Aster Keyboard

A private, offline Android keyboard with a Flutter companion app. The keyboard runs as a native Kotlin input method, so typing does not require a Flutter engine. The companion helps you enable Aster, choose it as your keyboard, and try it in test fields.

**Platform:** Android 7.0 or newer (API 24+)  
**Application ID:** `app.aster.aster_keyboard`

## Features

- Compact English and Romanian QWERTY, with accented-letter popups and quick language switching. Romanian has no extra diacritics row; hold A for `ă/â`, I for `î`, S for `ș`, and T for `ț`.
- Offline suggestions and autocorrect using bundled 50,000-entry frequency lists for each language, with immediate Backspace undo and an explicit personal dictionary.
- Romanian typing suggests nearby Romanian and English words together, including both personal dictionaries, without switching layouts.
- System, light, and dark appearance in both the keyboard and companion, plus your own photo wallpaper with adjustable dimming.
- An emoji panel with categories, recent emoji, flags, and skin-tone variants for supported gestures.
- One-shot Shift, caps lock, and configurable automatic sentence capitalization.
- Two number/symbol pages, long-press number shortcuts, and repeating deletion.
- Numeric and phone layouts, plus email and URL shortcuts.
- Editor-specific actions such as Search, Send, Next, and Done.
- Cursor movement, hold-to-select, select all, keyboard switching, and dismissal.
- Sensitive-field handling and a compact landscape layout.
- Guided activation, live keyboard status, a typing playground, native Android test fields, and privacy/license information.
- Controls for punctuation spacing, double-space periods, a number row, key height/spacing/corner radius, key borders, secondary labels, hold timing, sound, and vibration.

Autocorrect is a conservative, local dictionary system for spelling mistakes and common forms, rather than grammar or sentence rewriting. Device/OEM compatibility and accessibility testing remain ongoing work.

## Privacy

Aster does not persist conversations, access the clipboard, include analytics or advertising, or request Internet access in release builds. Settings, a selected wallpaper, explicitly added personal words, and recent emoji stay in private app storage. There is no automatic learning from your typing. Debug/profile builds use Flutter's development networking. The native service holds the current composing word transiently and reads limited context for Unicode deletion, correction undo, and punctuation in ordinary fields; sensitive fields use delete key events without reading their contents. Sensitive-field detection relies on the receiving app's editor metadata.

Android displays its standard warning when you enable any third-party keyboard. Other apps can handle the text you enter according to their own policies.

## Install and activate

1. Download the APK from this repository's **Releases** page and install it. If prompted, allow your browser or file manager to install the package.
2. Open **Aster Keyboard** and tap **Open keyboard settings**. Enable Aster in Android's keyboard list.
3. Return to Aster, tap **Choose Aster**, and select **Aster Keyboard**.
4. Confirm **Aster is ready**, then try typing in another app. **Try it** also provides Flutter and native Android test fields.

With Android platform-tools installed, you can instead run `adb install -r path/to/Aster-Keyboard-v1.2.apk`.

Updates must be signed with the same key as the installed app. An older debug-signed build must be uninstalled before installing the first release signed with the new key. To switch keyboards later, use Aster's keyboard-picker key or Android's system keyboard selector.

## Make Aster your own

Open **Settings** in the companion, or tap the keyboard's gear button. Preferences are saved on the device and applied to the native keyboard immediately.

- **Appearance:** choose System, Light, or Dark. Choose a wallpaper with Android's image picker, adjust its dimming, or remove it. A resized, metadata-free private copy is saved; broad photo/storage access is not requested.
- **Languages:** enable English and/or Romanian, keeping at least one enabled. Tap the language name on the keyboard, or hold Space, to cycle enabled languages. Hold the language name to open Android's keyboard picker. Romanian uses comma-below `ș` and `ț`; legacy cedilla forms are normalized in dictionary lookups.
- **Autocorrect:** type a space or punctuation to accept a likely correction; press Backspace immediately to restore your original spelling. Tap a suggestion to insert it explicitly. Romanian diacritic restoration can be disabled separately. Unknown names can be added manually to the personal dictionary, with one word per line, up to 500 per language.
- **Mixed Romanian/English typing:** while Romanian is selected, suggestions search both bundled dictionaries and both personal dictionaries, even if the English layout is disabled. Nearby spellings and matching word prefixes compete for the same suggestion slots. Lookup ignores Romanian accents and recognizes swapped letters; personal words and frequency help rank matches, with Romanian winning equal matches. The typed word stays available, duplicate suggestions are removed, and capitalization is preserved. Tap English spelling suggestions to accept them; automatic corrections still use Romanian rules. Recognized English words are protected from replacement, while Romanian accent restoration (such as `buna` → `bună`) stays available when enabled. English mode keeps its existing suggestion behavior. Everything runs offline.
- **Typing:** independently control suggestions, autocorrect, automatic capitals, spaces after `, ; : ! ?`, and double-space periods. Numeric separators such as `1,5` and `12:30` stay intact. Hold quote or dash keys on the symbols page for typographic forms, including Romanian `„…”` quotes. Automatic assistance is disabled in password/private fields, email addresses, URLs, numeric editors, and editors requesting no suggestions.
- **Layout:** enable the number row and adjust key height (42–66 dp), horizontal key spacing (0–5 dp per side), and corner radius (0–18 dp). Toggle key borders and small secondary labels. The appearance preview reflects these controls while adjusting them. Romanian uses the same compact rows as English, with diacritics on long press.
- **Feedback and holds:** choose sound/vibration feedback and a 200–800 ms hold delay for accented-letter popups, language switching, selection and the start of repeating deletion. A normal tap remains immediate on release, and releasing Delete stops repetition.
- **Emoji:** tap **☺**, choose a category, and scroll the grid. Supported hand gestures offer skin tones on long press. **ABC** returns to letters. Recent emoji are saved only when selected in ordinary text fields.

The **Try it** screen includes English/Romanian examples and native test fields. Wallpaper and settings survive app restarts; uninstalling clears this private data.

## Set up a development machine

Install [Git](https://git-scm.com/downloads), the [Flutter SDK](https://docs.flutter.dev/install), a **JDK 17**, and [Android Studio / Android SDK tools](https://docs.flutter.dev/platform-integration/android/setup). Add Flutter and Java to `PATH`; set `JAVA_HOME` to your JDK 17 installation if needed. Python 3.10+ is needed only for the signing/release helper scripts and their tests.

The project and release workflow use these versions:

| Component | Version |
| --- | --- |
| Flutter | 3.47.0 stable |
| Dart | 3.13.0, bundled with Flutter |
| JDK / bytecode target | 17 |
| Android compile / target SDK | 36 |
| Android SDK Build-Tools | 36.0.0 |
| Android NDK | 28.2.13676358 |
| Android Gradle plugin | 9.1.0 |
| Gradle wrapper | 9.3.1 |
| Kotlin Gradle plugin | 2.4.0 |

Install SDK Platform 36, Build-Tools 36.0.0, platform-tools, Android SDK Command-line Tools, and NDK 28.2.13676358 in Android Studio's SDK Manager. An emulator or Android phone with USB debugging is needed to run the app. Use the committed Gradle wrapper rather than a separate Gradle installation.

Clone your fork or this repository using its **Code** URL, open the `aster_keyboard` directory, and run:

```sh
git clone <repository-url>
cd aster_keyboard
flutter --version
flutter config --jdk-dir="<path-to-jdk-17>"
flutter doctor -v
flutter doctor --android-licenses
flutter pub get --enforce-lockfile
flutter devices
flutter run -d <android-device-id>
```

Replace the angle-bracket placeholders with your values. Flutter generates `android/local.properties` for your machine; do not copy someone else's SDK paths. Missing Chrome or desktop toolchains in `flutter doctor` do not block this Android-only project.

**Contributors do not need the maintainer's release key.** `flutter run` and `flutter build apk --debug` use a local debug identity. Native Kotlin changes require restarting the Android app; Flutter hot reload updates Dart UI only.

## Project structure

```text
.github/
  workflows/release.yml          Push-to-main release pipeline
  scripts/                      Version allocation and its tests
android/
  app/build.gradle.kts           Android build and release signing
  app/src/main/kotlin/app/aster/aster_keyboard/
    MainActivity.kt             Flutter platform-channel endpoint
    ime/                        Keyboard, editor profiles, input dispatch
    settings/                   Shared native preferences and private photo import
    diagnostics/                Native editor test activity
  app/src/test/                 Kotlin unit tests
  app/src/androidTest/          Instrumented Android editor tests
  gradle/wrapper/               Pinned Gradle distribution and wrapper JAR
  app/src/main/assets/dictionaries/  Offline English/Romanian word frequencies
lib/
  app/aster_app.dart             Companion screens and onboarding
  services/keyboard_bridge.dart Flutter-to-Android channel
  services/keyboard_settings.dart Shared settings contract
assets/fonts/                   Roboto fonts and license
test/                           Flutter widget and golden tests
scripts/signing.py              Private signing-file creation/restoration
pubspec.yaml                    Dependencies and release-version baseline
pubspec.lock                    Locked Dart dependencies
```

The platform channel is `app.aster/keyboard`; it carries activation status, preferences, wallpaper selection, and navigation. `SettingsStore` is the source of truth for Flutter and the IME. Keyboard events are handled through Android's `InputMethodService` and `InputConnection`, independently of the Flutter companion. Dictionary loading and suggestions run on a worker thread. `TypingSession` and `WordLexicon` isolate typing policies for JVM tests without a device.

## Contribute and validate changes

Create a feature branch in your fork, keep changes focused, and submit a pull request describing the behavior, relevant screenshots, and checks you ran. Add focused tests when changing input behavior or fixing a bug. Keep public setup and feature documentation in this README.

Run these checks from the project root:

```sh
flutter analyze
flutter test
python -m unittest discover -s .github/scripts -p "test_*.py"
```

Run native checks from `android/`:

```powershell
# Windows PowerShell
.\gradlew.bat testDebugUnitTest lintRelease assembleDebugAndroidTest
# With a connected and authorized Android device/emulator:
.\gradlew.bat connectedDebugAndroidTest
```

On macOS/Linux, replace `.\gradlew.bat` with `bash ./gradlew`. Only regenerate golden images after reviewing intentional UI changes: `flutter test --update-goldens`.

If Windows lint reports `PropertyEscape` in the generated `android/local.properties`, escape the drive colon in both SDK paths (for example, `C\:\\Android\\sdk`). Flutter can regenerate those local paths; this does not affect Linux CI.

Before reporting keyboard changes as tested, check actual typing in other apps, password/PIN fields, numeric/email/URL editors, action keys, Unicode deletion, selection, rotation, and switching keyboards. Widget tests do not exercise a system IME inside another app. Include the Android version, device, reproduction steps, and observed/expected behavior in bug reports; do not include private typed text.

## Release signing and moving to another machine

The maintainer's signing files live **directly in the project root**:

| Private file | Purpose |
| --- | --- |
| `aster-release.jks` | RSA 4096-bit release signing key; alias `aster` |
| `key.properties` | Keystore path, alias, store password, and key password |

The original release certificate's public SHA-256 fingerprint is:

```text
0a87829fd1d6b67a61b7fd21c9e241078aa1811d88aec3a5fc7a0121ddb68027
```

These files are ignored by Git. **Back up both files together** in encrypted storage, with another independent recovery copy. Keep the passwords in a password manager as well. GitHub Actions secrets are not a substitute for a recoverable backup. Losing the key prevents signing compatible updates to APKs already installed with it.

To continue releasing from another machine:

1. Clone the repository and complete the development setup above.
2. Securely copy the original `aster-release.jks` and `key.properties` into the new checkout's root, beside `pubspec.yaml`.
3. Keep `storeFile=aster-release.jks` in `key.properties`. That relative path works across Windows, macOS, and Linux.
4. Confirm the certificate matches your backup record, entering the store password when prompted:

   ```sh
   keytool -list -v -keystore aster-release.jks -alias aster
   ```

5. Build with `flutter build apk --release`. The signed APK is written to `build/app/outputs/flutter-apk/app-release.apk`.

Release builds require these private files and never fall back to a debug certificate. Debug builds remain available without them. To locally update an installed CI release, pass version/build numbers higher than that release using `--build-name` and `--build-number`; the checked-in baseline is lower than CI releases.

For a **new, independent distribution identity only**, run `python scripts/signing.py generate` once with JDK `keytool` available. It creates a fresh keystore and random passwords, stores the passwords in `key.properties`, and prints the public certificate SHA-256 fingerprint. It refuses to overwrite existing signing files. **Restore your existing files when moving machines; generating another key changes the identity.**

Flutter's [Android release/signing guide](https://docs.flutter.dev/deployment/android) explains the signing model. This workflow publishes signed APKs to GitHub; publishing through Google Play is a separate setup.

## Configure automatic GitHub releases

Before the first push to `main`, create these repository secrets under **Settings > Secrets and variables > Actions > New repository secret**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the original `aster-release.jks` |
| `ANDROID_STORE_PASSWORD` | `storePassword` from root `key.properties` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` from root `key.properties` |
| `ANDROID_KEY_ALIAS` | `aster` (the `keyAlias` value) |

On Windows, copy the encoded keystore directly to your clipboard, then paste it into the secret value:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path (Get-Location) 'aster-release.jks'))) | Set-Clipboard
```

On macOS, use `base64 < aster-release.jks | tr -d '\n' | pbcopy`. On Linux, use `base64 -w 0 aster-release.jks > aster-release.jks.b64`, transfer its contents into the secret securely, then remove that temporary file. Base64 encodes the private key; treat it like the keystore itself.

Allow GitHub Actions to run in the repository. The release job requests `contents: write` to create tags and releases using the built-in `GITHUB_TOKEN`; a personal access token is not needed. Organization policies or rules protecting release tags must permit the workflow to create those tags.

Every push to `main` starts `.github/workflows/release.yml`. After checks pass, it builds and verifies a signed universal APK, then creates a release titled **Aster Keyboard vx.y**, with its own `vx.y` tag, APK, `version.txt`, SHA-256 checksums, and generated change notes. A push containing multiple commits produces one release. You can also start a release from the workflow's **Run workflow** button on `main`.

### Automatic versioning

The checked-in `pubspec.yaml` version (`1.1.0+2`) is the baseline. The workflow adds its unique [GitHub run number](https://docs.github.com/en/actions/reference/workflows-and-actions/variables) to the minor version and Android build number:

| Workflow run | Release title / tag | APK version name | Android version code |
| --- | --- | --- | --- |
| 1 | Aster Keyboard v1.2 / `v1.2` | `1.2.0` | 3 |
| 2 | Aster Keyboard v1.3 / `v1.3` | `1.3.0` | 4 |
| 3 | Aster Keyboard v1.4 / `v1.4` | `1.4.0` | 5 |

The version is updated in the runner's `pubspec.yaml` and passed explicitly into the APK build. Source commits keep the baseline; the workflow does not commit version bumps back to `main`. The APK and each release's `version.txt` contain the effective version.

Run numbers increase even when a build fails, so gaps are expected. Rerunning a failed run uses its original version; rerunning a published run cannot overwrite its release. Tags pointing at another commit cause a failure, and the workflow never force-moves tags or replaces release assets. Closely spaced pushes run independently without cancellation of pending runs.

Keep the workflow file and baseline stable. Before renaming/resetting the workflow, moving to a repository with existing releases, or changing the major/baseline version, plan offsets above all published versions and Android build numbers. A release collision fails safely rather than replacing anything.

## Repository hygiene

Commit application sources, fonts/license, `pubspec.lock`, `.github/`, and the complete Gradle wrapper, including its JAR and both launch scripts. Build output, local SDK paths, signing keys/passwords, editor files, and release APKs are excluded.

The ignore rules intentionally include `*.md` and `!README.md`. This keeps local `AGENTS.md`, `claude.md`, and other Markdown notes out of Git; this README is the public contribution guide. Ignore rules do not untrack files already committed, so inspect staged files before publishing an existing repository.

## Dependencies and third-party licenses

Flutter is the only Dart runtime dependency. The app bundles Roboto and Material icons; Roboto's license is in `assets/fonts/roboto_license.txt`. English/Romanian word frequencies come from [FrequencyWords by Hermit Dave](https://github.com/hermitdave/FrequencyWords), derived from OpenSubtitles 2018 and licensed **CC BY-SA 4.0**. Full attribution, source revision, and license text are in `assets/licenses/wordlists.txt` and alongside the native dictionaries. The lists are bundled with the app and never downloaded at runtime. Licenses are available in the companion's Privacy tab.

`flutter_test`, `flutter_lints`, JUnit, and AndroidX Test support development and testing. No application source-code license has been selected; the repository owner should choose one before offering redistribution under specific terms.
