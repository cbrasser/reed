# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin + Jetpack Compose, native Android (agreed direction, not final). Readium Kotlin toolkit for rendering, text selection, highlight decorations, and stable locators. Room (SQLite) for books, notes, and locators. Android `SpeechRecognizer` for in-app dictation.

## Users

One person: the owner, reading on their own Android phone. Not distributed via the Play Store. Reads a mix of fiction and nonfiction in English and German, and stops to capture reactions and thoughts against specific passages or pages.

## Product Purpose

A personal e-reader whose distinguishing job is attaching notes to passages. Read a book comfortably, select a passage or page, say or type a note, and later browse every passage + note pair for that book and jump back into context.

Success: capturing a note never breaks the reading flow, dictating is one tap away, and the notes list for a book reads as a useful record of what struck you.

## Positioning

Existing readers were evaluated and rejected (ReadEra, Moon+ Reader, KOReader, Google Play Books). ReadEra matched the features best but failed on two points that define Reed:

- A modern, minimal UI is a core goal, not a nice-to-have. Dated, cluttered reader chrome is the anti-reference.
- Dictation is first-class: a dedicated, prominent mic button in the note editor, not the keyboard's mic.

## Operating Context

- Phone only, one-handed reading is the realistic posture; reading happens in varied light, including at night.
- Books and notes are bilingual (English and German). Dictation must switch recognizer language quickly; note language often follows the book's language but not always.
- Notes are casual-to-considered reactions on mixed reading. They stay inside Reed unless the user turns on keeping them in step with the home app through their Nextcloud (below).

## Capabilities and Constraints

- Formats: EPUB and PDF via Readium. MOBI/AZW3 out of scope (convert with Calibre).
- Reading settings: typeface, size, spacing, theme.
- Select a text passage, or a whole page, and attach a note.
- Notes entered by voice (speech-to-text) or typing, in English or German.
- Notes browsable per book, showing selected passage and note together; tapping jumps to the location.
- Private books: a book can be marked private. Private books and their notes are hidden everywhere (library, Reading now, search) until the user authenticates with biometrics or the device PIN/pattern; they re-lock when the app leaves the foreground.
- Local-first storage. One optional connection: **Send notes to Nextcloud** (library menu). Off by default; signing in happens on the Nextcloud's own page (Login Flow v2, an app password Reed seals with an Android Keystore key). Reading itself never needs the network.
  - **Notes, both ways with the home app** (format: home's `docs/reed-format.md`). Reed writes each book's notes as `<folder>/notes/<book id>.json` shortly after notes change, including notes deleted since the last send (`deletedNotes`), and removes a book's file when the book leaves Reed. The home app files each book under `Books/` and writes edits and deletions made there to `<folder>/home/<book id>.json`; Reed applies the ones newer than its own copy. Notes are only created in Reed. Private books stay on the phone unless the user includes them.
  - **Book files (optional, off by default):** each book goes to `<folder>/books/<book id>.<epub|pdf>` with a small `<book id>.json` card, and books there that this phone doesn't have are fetched (keeping their id, so their notes line up). Removing a book here removes its copy there; a book removed on another phone stays here but isn't sent again.
  - Runs a moment after notes change, at app start, and hourly in the background, whenever the phone is online.
- UI language: English (books, notes, and dictation stay bilingual).
- Material You / Dynamic Color: off; Reed uses its own fixed palette.
- PDF: passage notes may be limited by Readium's PDF navigator; page-level notes are an acceptable fallback.
- Dictation: on-device recognition availability per language varies by phone; accepted limitation, with a clear error state.
- Out of MVP: a cross-book notes view.
- Rough MVP estimate: 1–2 weeks.

## Brand Commitments

Working name: Reed. No logo, palette, or type commitments yet.

## Evidence on Hand

None. No sample books, screenshots, or assets are in the repo yet.

## Product Principles

1. Reading comes first; chrome appears only when asked for and leaves quickly.
2. A thought should reach a note in seconds: select, tap mic, speak, done.
3. Passage and note belong together everywhere they appear.
4. Bilingual by default, never an afterthought.
5. Fewer, better settings over exhaustive configuration.
