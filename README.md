# Reed

A personal Android e-reader for EPUB and PDF, built around notes in the margin: select a passage, say or type a thought, and find every passage + note pair for a book in one list.

- EPUB and PDF (via [Readium](https://github.com/readium/kotlin-toolkit)); MOBI/AZW3 aren't supported, convert them with Calibre.
- Passage notes (EPUB) and page notes (EPUB and PDF), drawn as a graphite stroke in the margin.
- Dictation in English or German, through the phone's speech service or an app like FUTO Voice Input.
- Paper, Night and Black themes; four typefaces; size, spacing and margins.
- Private books, hidden with their notes until you unlock with fingerprint or screen lock.

Requires Android 11 or newer.

## Install with Obtainium

1. In [Obtainium](https://github.com/ImranR98/Obtainium), tap **Add app**.
2. Enter `https://github.com/cbrasser/reed` and tap **Add**.
3. Install. Obtainium checks this repo's releases and offers updates.

Every release is signed with the same key, so updates install over each other and keep your notes. If Reed is already installed from a build signed with a different key, uninstall it once first (this deletes its notes).

## Releasing

```bash
scripts/release.sh 0.2.0
```

Tags `v0.2.0`, builds a signed release APK, checks the signature, pushes the tag and publishes a GitHub release with `reed-0.2.0.apk` attached. The version name comes from the tag and the version code from the commit count, so commit before releasing. The signing key stays on your machine (see `CLAUDE.md`).

## Building

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug
```

JDK 21 and the Android SDK (compileSdk 37) are required.

Bundled fonts (Schibsted Grotesk, Literata, Source Serif 4, Atkinson Hyperlegible Next) are under the SIL Open Font License; their licence files are in `app/src/main/assets/fonts/`.
