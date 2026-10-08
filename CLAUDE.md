# Reed — Android e-reader with passage notes

Personal Android e-reader app. MVP is built (library, reader, passage/page notes with dictation, notes list, reading settings, private books). Product facts live in `PRODUCT.md`; the visual direction ("Pencil Marginalia") lives in `.impeccable/surfaces/` and `DESIGN.md`.

## Build and run

- JDK 21 is required and set via `org.gradle.java.home` in `gradle.properties` (Homebrew `openjdk@21`). If the wrapper says "Unable to locate a Java Runtime", export `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- `./gradlew :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- AGP 9 (built-in Kotlin), compileSdk 37 (Readium 3.4 requires it), minSdk 30, core library desugaring on.
- Test books can be added without the file picker: push to `/sdcard/Download`, rescan MediaStore, then `adb shell am start -a android.intent.action.VIEW -d content://media/external/file/<id> -t application/epub+zip --grant-read-uri-permission -n app.reed/.MainActivity`.
- Signing: debug and release builds both use the personal key `~/.android/reed-release.jks`; its credentials live in `~/.gradle/gradle.properties` (`REED_KEYSTORE_*`, `REED_KEY_*`), never in the repo. Without them the build falls back to the default debug key, and installing over a key-signed app then fails unless the app is uninstalled, which deletes all notes. Back up the keystore and that properties file together.
- Release build: `./gradlew :app:assembleRelease` (R8-shrunk, ~29 MB; smoke-tested on the emulator). Publish with `scripts/release.sh X.Y.Z`; users install and update through Obtainium from GitHub releases. versionName comes from the latest `vX.Y.Z` tag, versionCode from `git rev-list --count HEAD`.
- The default emulator image has no speech recognizer and no screen lock, so dictation and private-book unlock only show their error/disabled states there; test them on a real phone.

## Code map

- `data/`: Room entities/DAOs, `Library` (import via Readium, open, notes CRUD), `SettingsStore` (DataStore).
- Dictation: `SpeechRecognizer` service first (placeholder services such as FUTO's TEST-category `DummyService` are ignored), falling back to an app's `RECOGNIZE_SPEECH` screen (FUTO Voice Input on GrapheneOS), with a 3 s watchdog.
- `reader/`: `ReaderActivity` hosts the Readium navigator fragment under a Compose overlay; `Pencil.kt` holds the custom decoration templates (margin stroke + underline) and EPUB preferences; `Dictation.kt` wraps `SpeechRecognizer`.
- `ui/`: theme (palette, Schibsted Grotesk), shared `MarginTick`/`NotesList`, library and notes screens.
- `sync/`: optional sync with the user's Nextcloud for the home app (format: home's `docs/reed-format.md`). `NotesJson` (reed-notes v1 with `deletedNotes`, deterministic so unchanged books aren't resent), `NotesUpload` (send changed, remove gone), `TwoWay.kt` (pure decisions: applying home's `reed-notes-edits`, tombstones for notes deleted here, which book files go up or come down), `Nextcloud.kt` (Login Flow v2, OkHttp WebDAV incl. PROPFIND listing and streamed book files), `SyncStore` (DataStore + Keystore-sealed app password + bookkeeping), `NotesSync` (WorkManager: one unique job, queued after note changes, at start and by an hourly `NotesSyncKick`). Changes that come from the server go through `Library.applyRemote`, which doesn't signal "notes changed", so nothing bounces back. Downloaded books use `Library.importStaged` with their server id. Unit tests in `app/src/test`; `REED_TEST_WEBDAV=http://127.0.0.1:8765/` also runs one against a real WebDAV server (user `u`, password `testpass`).
- `listen/`: read aloud. `ReadAloud` (app-scoped) wraps Readium's `TtsNavigator`: with `PiperEngine` (Reed's own voices: sherpa-onnx running a Piper model, playing through an `AudioTrack`; `Lookahead` replays Readium's sentence split so the next three sentences are synthesised ahead) when `VoiceStore` (`Voices.kt`) has a voice for the book's language, else with the system speech engine, else it emits `NeedsVoice` and the reader offers the download, starting once it's installed. The sherpa-onnx AAR (only on GitHub releases) is fetched by the `fetchSherpa` Gradle task into `app/libs/` (gitignored, SHA-256 pinned); only its arm64 library is packaged. Its fp16 voice builds don't load in that runtime, so the catalog uses full-precision ones. `ReadAloud` saves each sentence as `listenLocator` + `lastLocator`, and holds a wake lock while playing; `ReadAloudService` (media3 `MediaSessionService`) keeps it going with the screen off and provides notification and lock-screen controls. The reader resumes from `listenLocator` only while the page that showed it is still on screen (`ReaderViewModel.trackListenPage`). EPUB only; the default emulator image has no speech engine.
- `privacy/PrivacyLock.kt`: BiometricPrompt (biometric or device credential); relocks when the app goes to the background.
- Readium's night mode forces `border-color` on every element; decorations must draw lines with backgrounds, not borders.
- `app/src/main/assets/readium/readium-css/ReadiumCSS-after.css` overrides Readium's copy (app assets win). It's generated by `scripts/patch-readium-css.sh`: with publisher styles off, Readium CSS forces every `p`/`div` to body size and h1–h6 onto its own scale, flattening chapter titles; the patch limits that to unclassed elements, drops the heading scale and hides page-break markers. Re-run the script after upgrading Readium.
- `data/ScanCleanup.kt` rewrites chapter HTML as it's read (page numbers and print line-end hyphens left in scan conversions). It's hooked in through `PublicationOpener.open(onCreatePublication = …)`: Readium 3.4 ignores the hook passed to the opener's constructor.
- Changing a setting that reflows text (`reflowsFrom`) re-finds the page by its first visible words (`reader/PageAnchor.kt`); Readium alone lands a page or more early.
- Read aloud starts from the page's first visible words (`firstVisibleWords`), not Readium's `firstVisibleElementLocator` (the whole chapter in books with one element per chapter). While it plays, turning or scrolling the page so the spoken sentence leaves the screen (`spokenPlacement`) moves the voice there once the page is at rest; scrolling, the page follows only when the sentence runs off screen. `ReadAloud.go` pauses while stepping to a sentence, since stepping while playing would synthesise every one.
- `ReaderActivity` keeps the screen on while the reader is in front, until 10 minutes without a touch.

## Requirements (from the user)

- Read the most common e-book formats.
- Basic font and style settings (typeface, size, spacing, theme).
- Minimal, clean UI.
- Select a page or a text passage and attach a note to it.
- Browse notes per book, showing both the selected passage and the note.
- Notes can be entered by voice (speech-to-text) or by typing.

## Why build instead of use an existing app

Evaluated existing readers (ReadEra, Moon+ Reader, KOReader, Google Play Books). ReadEra matched the feature list best but was rejected because:

- **The UI looks dated.** A modern, minimal look is a core goal, not a nice-to-have.
- **No dedicated mic button.** Gboard's keyboard mic technically works everywhere, but the user wants a first-class, one-tap dictation button in the note editor.

## Agreed technical direction (not final)

- Kotlin + Jetpack Compose, native Android.
- Readium Kotlin toolkit for rendering, text selection, highlight decorations, and stable locators (positions) for notes.
- Room (SQLite) for local storage of books, notes, and locators.
- Android `SpeechRecognizer` for the in-app dictation button.
- Formats: EPUB + PDF via Readium. MOBI/AZW3 aren't supported by Readium; out of scope for now (convert with Calibre if needed).
- Rough MVP estimate: 1–2 weeks.

## Screens to design

1. **Library**: list/grid of books, import.
2. **Reader**: distraction-free reading, quick access to font/theme settings.
3. **Select → note**: text selection action that opens a note sheet with a prominent mic button plus a text field.
4. **Notes per book**: list of passage + note pairs; tap to jump to the location in the book.

## Notes for design work

- Platform: `android`. Impeccable has an Android reference (`reference/android.md`); its web-oriented rules (type, color, spacing, hierarchy) still apply, with Compose as the implementation target.
- Impeccable is installed at `~/.claude/skills/impeccable` (v4.5.0). Its launcher downloads a helper binary on first run, which is expected.
