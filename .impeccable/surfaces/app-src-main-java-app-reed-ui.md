---
version: 1
slug: "app-src-main-java-app-reed-ui"
primary_target: "app/src/main/java/app/reed/ui"
related_targets: []
---

# Reed app surfaces

Scope: the whole phone app. Library, Notes and the note/settings sheets are Operate; the reader page itself is Read. Native Android, Compose + Material 3, Dynamic Color off, English UI.

Job: capture a thought against a passage in seconds without losing your place; review passage + note pairs per book and jump back. Private books hidden behind biometric/device-credential unlock.

## Direction contract

THESIS: Notes are graphite beside the text. Every note is drawn in one graphite ink, always next to the book's words and never on top of them, so "theirs" (ink) and "mine" (graphite) read apart on every screen. Refuses the category default of coloured highlighter blocks, toolbar-heavy reader chrome and cream-sepia-serif.

OWN-WORLD: Neutral paper white #FAFAF8, ink #1A1A1A, graphite #5E6268 for all user-authored text, light graphite rules #8E9298, pencil yellow #F2C230 reserved for dictation only. Flat surfaces, no texture, Material tonal elevation. Schibsted Grotesk carries all UI; passages set in the reader's chosen book face. State lives in line form: solid underline noted, dashed page note, doubled open/just filed.

STORY: The reader sees only the book. Select a passage, tap Note, tap the mic, speak in English or German, save; a graphite tick appears in the margin. Later, the notes list reads as the book's marginalia, passage over note, tap to return.

FIRST VIEWPORT: Library: top bar "Reed" with search and overflow; Reading now row of large covers with progress; adaptive cover grid, every cell cover, title, author, "37% · ✓3" (tick glyph drawn, not unicode); extended FAB "Add book" bottom-right. Reader: full-bleed text, chrome on centre tap.

FORM: Pencil Marginalia (Impeccable's pick, rank 1 of 7 grounded candidates; seed key c4133a83). Signature move: the graphite margin tick, shared by reader margin, notes list rows and library counts.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions

- Cross-book notes view deferred past MVP.
- PDF gets page notes only if Readium's PDF navigator lacks selection.
