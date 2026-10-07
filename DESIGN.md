---
name: Reed
description: A personal Android e-reader where your notes are graphite in the margin of the book.
colors:
  paper: "#FAFAF8"
  ink: "#1A1A1A"
  graphite: "#5E6268"
  graphite-light: "#8E9298"
  graphite-mid: "#80848B"
  rule: "#D9DBDE"
  pencil-yellow: "#F2C230"
  pencil-yellow-soft: "#FBEDB8"
  tonal-grey: "#E6E7E8"
  on-tonal-grey: "#2B2E33"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#F4F4F2"
  surface-container: "#EFEFED"
  surface-container-high: "#E9E9E7"
  surface-container-highest: "#E3E3E1"
  surface-dim: "#DCDCDA"
  inverse-surface: "#2E3033"
  error: "#B3261E"
  night-paper: "#141517"
  night-ink: "#E8E6E1"
  night-graphite: "#A4A8AE"
  night-graphite-light: "#6F7379"
  night-rule: "#34363A"
  night-tonal-grey: "#2D2E32"
  night-surface-container-low: "#191A1C"
  night-pencil-container: "#4A3C0A"
  night-error: "#F2B8B5"
  black-paper: "#000000"
  black-ink: "#C9C7C2"
typography:
  display:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "34sp"
    fontWeight: 600
    lineHeight: "40sp"
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "26sp"
    fontWeight: 600
    lineHeight: "32sp"
    letterSpacing: "-0.015em"
  headline-small:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
    letterSpacing: "-0.01em"
  title-large:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "22sp"
    letterSpacing: "-0.005em"
  title-small:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "20sp"
  body:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  body-medium:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  body-small:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
    letterSpacing: "0.005em"
  label:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.005em"
  label-medium:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.01em"
  label-small:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: "14sp"
    letterSpacing: "0.02em"
  note-entry:
    fontFamily: "Schibsted Grotesk, sans-serif"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "26sp"
  passage:
    fontFamily: "Literata, Source Serif 4, Atkinson Hyperlegible Next, serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  passage-sheet:
    fontFamily: "Literata, Source Serif 4, Atkinson Hyperlegible Next, serif"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "26sp"
rounded:
  cover: "3dp"
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "20dp"
  xl: "28dp"
  full: "9999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  tick-gap: "14dp"
  lg: "16dp"
  gutter: "20dp"
  sheet: "24dp"
  xl: "32dp"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "8dp 24dp"
    height: "40dp"
  fab-add-book:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
    typography: "{typography.label}"
    rounded: "{rounded.lg}"
    height: "56dp"
  button-tonal:
    backgroundColor: "{colors.tonal-grey}"
    textColor: "{colors.on-tonal-grey}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: "40dp"
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: "48dp"
  mic-rest:
    backgroundColor: "{colors.pencil-yellow-soft}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    size: "68dp"
  mic-listening:
    backgroundColor: "{colors.pencil-yellow}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    size: "68dp"
  choice-selected:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
  choice-unselected:
    backgroundColor: "transparent"
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
  note-field:
    backgroundColor: "transparent"
    textColor: "{colors.graphite}"
    typography: "{typography.note-entry}"
  note-sheet:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xl}"
    padding: "0 24dp 16dp"
  note-row:
    backgroundColor: "transparent"
    textColor: "{colors.graphite}"
    typography: "{typography.body}"
    padding: "14dp 24dp 16dp 20dp"
  book-cover:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.ink}"
    rounded: "{rounded.cover}"
  reader-chrome:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.title}"
    height: "64dp"
  progress-line:
    backgroundColor: "{colors.rule}"
    textColor: "{colors.ink}"
    height: "2dp"
---

# Design System: Reed

## Overview

**Creative North Star: "Pencil Marginalia"**

Reed treats the book as printed ink and the reader as a pencil. The book's words are ink (#1A1A1A on a neutral paper white); everything the reader writes is graphite, set beside the text and never on top of it. The two hands stay legible as two hands on every screen: in the reader margin, in the note sheet, in the notes list, and in the library's note counts. One mark carries this through the whole app: a thin graphite stroke in the margin, the margin tick, whose line form (solid, dashed, doubled) is the only way note state is expressed.

The surface is flat, neutral and quiet. Paper is not cream, type is not sepia, the UI is not serif. Schibsted Grotesk carries every piece of chrome and every word the reader writes; the book's own face appears only for the book's words. Chrome is summoned by a centre tap and leaves the page full-bleed when dismissed. Colour is nearly absent: one pencil-yellow accent exists, and it belongs to the dictation button alone, so the one-tap mic is the only coloured thing on a note sheet.

The system explicitly refuses three category defaults: coloured highlighter blocks laid over book text, toolbar-heavy reader chrome, and the cream-sepia-serif "cosy reader" look.

**Key Characteristics:**
- Two hands: ink for the book and UI, graphite for the reader's own words.
- The margin tick is the signature mark; state lives in its line form, never in hue.
- Pencil yellow exists for dictation only.
- Flat paper with Material tonal containers; only book covers cast shadows.
- One grotesk for UI, the reader's chosen book face for book words.
- Selection is a line, never a fill.

## Colors

A neutral paper-and-ink palette with a graphite scale for the reader's hand and a single pencil-yellow accent held in reserve. Three reading themes (Paper, Night, Black, plus Auto) swap the whole set; the app chrome follows the reading theme, not only the system.

### Primary
- **Ink** (`ink`): book text, UI text, primary buttons (Save, Add book), the active progress line, selected-state lines. On Night it becomes **Warm Night Ink** (`night-ink`) and on Black a dimmer **Ash Ink** (`black-ink`), so pure black never carries pure white text.

### Secondary
- **Graphite** (`graphite`): the reader's hand. Every note body, the note being typed, its cursor, and note counts are graphite. Also `onSurfaceVariant`, so secondary metadata (chapter names, dates, positions, placeholders) shares the pencil's tone. Night: **Night Graphite** (`night-graphite`).
- **Light Graphite** (`graphite-light`): the margin tick, the in-book underline and margin mark, and the reader's text selection (at 35% alpha). Night: `night-graphite-light`.
- **Mid Graphite** (`graphite-mid`): selection handles in the reader, chosen to read on Paper, Night and Black alike.

### Tertiary
- **Pencil Yellow** (`pencil-yellow`): the mic while listening, and its voice-reactive halo (28% fill, 60% ring). Nothing else.
- **Soft Pencil** (`pencil-yellow-soft`): the mic at rest on Paper. On Night and Black the resting mic is pencil yellow at 14% with a 1.5dp pencil-yellow ring and a yellow glyph.

### Neutral
- **Paper** (`paper`): background and surface on the Paper theme; the reader page itself. Night: **Night Paper** (`night-paper`); Black: true black (`black-paper`).
- **Rule** (`rule`): hairline dividers, progress-line tracks, unselected choice outlines, cover borders (at 60%). Night: `night-rule`.
- **Tonal Grey** (`tonal-grey` with `on-tonal-grey`): filled tonal buttons (Note page, size steppers) and surface variants. Night: `night-tonal-grey`.
- **Surface containers** (`surface-container-low` … `surface-container-highest`, `surface-dim`): sheets and scrolled bars. The note sheet sits on `surface-container-low`; covers without art sit on `surface-container-high`. On Night and Black, containers are the paper lifted by a fixed step (Night +0.035, Black +0.045 per channel at 1.0×; 0.6×, 1.5×, 2.0× for low, high, highest); `night-surface-container-low` is the note-sheet value.
- **Error** (`error` / `night-error`): dictation and import failures only, as text with a recovery action.

### Named Rules
**The Two Hands Rule.** Book words and UI are ink; anything the reader wrote is graphite. A note body is never set in ink, and a quoted passage is never set in graphite.

**The Pencil Yellow Rule.** Pencil yellow appears on the dictation button and its halo, and nowhere else: not highlights, not selection, not badges, not focus, not branding. If a second yellow thing appears on a screen, one of them is wrong.

**The Fixed Palette Rule.** Dynamic Color is off. Reed's palette is fixed and the reading theme drives app chrome; never derive colours from the wallpaper.

## Typography

**UI Font:** Schibsted Grotesk (variable, weights 400 to 700), bundled.
**Book Faces:** Literata (default), Source Serif 4, Atkinson Hyperlegible Next, or the publisher's original; each with its italic, bundled and served to the reader.

**Character:** A crisp, slightly condensed newsroom grotesk for chrome and handwriting-by-proxy, against a literary book face for the text itself. The contrast between the two is part of the two-hands idea.

### Hierarchy
- **Display** (600, 34/40sp, -0.02em): reserved; not in daily use.
- **Headline** (600, 26/32sp, -0.015em): empty-state statements ("Your shelf is empty").
- **Headline Small** (600, 22/28sp, -0.01em): the "Reed" wordmark in the library top bar.
- **Title Large** (600, 20/26sp): sheet titles ("Notes").
- **Title** (600, 16/22sp): library section headers ("Reading now", "All books" with its count in graphite), the book title in reader chrome, the size readout.
- **Title Small** (600, 14/20sp): book titles under covers.
- **Body** (400, 16/24sp): note bodies in lists, empty-state copy.
- **Body Medium / Small** (400, 14/20 and 12/16sp): dictation status and partial transcript; author lines.
- **Label** (500, 14/20sp): buttons, setting names, chapter headers in the notes list, reader position.
- **Label Medium / Small** (500, 12/16 and 11/14sp, +0.01 to +0.02em): passage meta ("Letter 1 · 3%"), note dates and positions, progress percentages, note counts on covers. Labels are sentence case, never uppercase.
- **Note Entry** (400, 17/26sp): the note text field, in graphite.
- **Passage** (book face, 400, 16/24sp in lists; 17/26sp in the note sheet): book words quoted outside the reader, in ink.

### Named Rules
**The One Grotesk Rule.** Schibsted Grotesk sets all UI and all reader-written text. The book face appears only for the book's words (the reader page, quoted passages, typeface previews). No serif in chrome; no book face for notes.

**The Sentence Case Rule.** No uppercase labels, no tracked-out kickers. Hierarchy comes from weight (400/500/600) and the ink/graphite split.

## Layout

Phone only, portrait, one-handed. Screens use a 20dp side gutter (library grid, notes list, chapter headers, reader bottom chrome); bottom sheets use 24dp. The library is an adaptive cover grid (minimum cell 104dp, 16dp column gap, 24dp row gap) under a horizontal "Reading now" row of larger 136dp covers with a 2dp progress line; the extended "Add book" FAB sits bottom-right and the grid reserves 96dp beneath it.

Passage + note pairs share one indent grammar: the tick, a 14dp gap, then the passage; the note body indents 16dp from the row's start so it hangs under the passage, with position and date beneath it in label small, joined by a spaced middle dot ("3%  ·  7 Oct"). Rows are separated by space (14dp top, 16dp bottom), not dividers; a 1dp rule separates chapters only.

The reader is full-bleed. A centre tap brings top chrome (64dp: back, title over chapter, note count, reading settings) and bottom chrome (2dp progress line over a 72dp row: position and "Note page"), each separated from the page by a hairline rule. Minimum touch targets are 48dp.

**The Chrome On Request Rule.** The reader page shows only the book. Chrome enters on a centre tap (fade 160ms with a one-third slide over 200ms) and leaves faster (140/160ms). Nothing persistent overlays the text.

## Elevation & Depth

Reed is flat. Depth comes from Material tonal containers (the `surface-container` family), hairline rules and scrims, not from shadows. Reader chrome has zero tonal and zero shadow elevation; it is separated from the page by a 1dp rule. Bottom sheets sit on `surface-container-low` with the standard sheet scrim; the reading-settings sheet uses a light 12% black scrim so theme and size changes are visible on the page behind it as they apply.

### Shadow Vocabulary
- **Cover lift** (2dp, 3dp in Reading now; ambient, unclipped, on a 3dp-radius shape): book covers are physical objects lying on the paper, the only content that casts a shadow.
- **FAB** (Material default): the "Add book" extended FAB keeps Material's standard elevation.

### Named Rules
**The Flat Paper Rule.** Surfaces are flat. Only book covers (and the FAB's stock elevation) cast shadows; never add shadow to cards, rows, sheets or chrome.

## Shapes

Soft, unfussy corners from Material's scale, tightened at the extremes: 4dp extra small, 8dp small, 12dp medium (typeface options), 20dp large (FAB), 28dp extra large (sheet tops). Buttons and segmented controls are full pills; the mic and theme swatches are circles. Book covers are nearly square-cornered (3dp) with a 1dp rule border at 60%, so they read as printed objects rather than UI cards. Lines carry meaning: 1dp for rules and resting outlines, 1.5dp for selection and the in-book underline, 2dp for the tick and progress line, with round caps on ticks and butt caps on progress.

## Components

### Buttons
- **Shape:** full pill (`rounded.full`); FAB uses `rounded.lg`.
- **Primary:** ink fill, paper text (Save in the note sheet, "Add book" FAB and empty-state button). One primary per screen.
- **Tonal:** tonal grey fill (Note page, text size steppers).
- **Text:** ink text on transparent ("Show whole note", "Lock", "Try again", "Settings"), with a 48dp minimum height.
- **Icon buttons:** Material Outlined icons in ink.

### Choice controls (segmented buttons, typeface options, theme swatches)
- **Style:** transparent container. Selected: 1.5dp ink line, ink text. Unselected: 1dp rule line, graphite text. Segmented buttons show no check icon.
- **Theme swatches:** 44dp circles in each theme's paper with "Aa" in its ink (Auto is split paper/night), selected by a 1.5dp ink ring 4dp outside the swatch.
- **Typeface options:** 12dp-radius tiles with "Ag" in the face at 22sp over its name in label small.

**The One Selected Line Rule.** Every choice control shows selection as a 1.5dp ink line on a transparent container (`selectedLine()`, `reedSegmentedColors()`). Never a tonal fill, never a check mark, never colour.

### Note sheet
Opens from a text-selection "Note" action or "Note page". On `surface-container-low`, 28dp top corners, 24dp sides. Top: the passage in the book face (17/26sp, four lines, tap to expand) beside a doubled tick (dashed for a page note), then chapter and position in label medium. Then a borderless, transparent text field in graphite note-entry type with the placeholder "Say or type your note", a polite live-region status line for dictation, and a bottom row: EN/DE segmented toggle (left), mic (centre), Save (right). Dismissing the sheet saves. Delete appears as an outlined trash icon only for existing notes.

### Dictation mic (signature)
A 68dp circle with a 30dp outlined mic glyph. At rest: soft pencil fill (Night/Black: 14% yellow with a 1.5dp yellow ring). Listening: full pencil yellow, the glyph crossfades (120ms) to a stop square, and a halo breathes with voice level (spring, damping 0.7, stiffness 400; fill radius up to 1.45×, outer 1.5dp ring at 60%). The language toggle locks while listening. Errors appear as error-coloured text with one recovery action.

### Margin tick (signature)
A graphite stroke (2dp, round caps, `graphite-light`) beside the words it belongs to, stretching to the height of its content. Forms: **solid** for a passage note, **dashed** (5dp dash, 4dp gap) for a page note, **doubled** (two strokes, 6dp total width) for the note that is open or was just filed. As a count, a 12dp tick, 6dp gap and the number in graphite ("| 3"), announced as "3 notes".

**The State-In-Form Rule.** Note state is expressed only by the tick's line form (solid, dashed, doubled), never by hue, weight or fill.

### In-book decorations (Readium)
- **Pencil line:** a 1.5px graphite-light underline under each noted passage; doubled (second line 4px above) while the note is open or just filed.
- **Pencil mark:** a 2px, 1px-radius stroke in the page margin beside the passage, inside a 22px-wide tap target that opens the note (`night-graphite` on dark themes).
- **Selection:** graphite-light at 35%, replacing the WebView's blue.

**The Beside Not On Rule.** Notes never paint a block behind book text. Passages are marked with an underline and a margin stroke only.

**The Gradient Line Rule.** Readium's night mode forces `border-color` to the text colour on every element, so decoration lines are drawn with `linear-gradient` backgrounds (or pseudo-element fills), never with borders.

### Notes list
Grouped by chapter in reading order under sticky chapter headers (label, graphite, 1dp rule above every header after the first). Each row: tick + passage in the book face (ink, five lines), note body in graphite body type (eight lines, then "Show whole note"), then position and date. Page notes replace the passage with "Page N" in label graphite beside a dashed tick. Tapping a row opens the book at that location. The same list renders full-screen and inside the reader's Notes sheet.

### Library
Top bar: "Reed" in headline small with search, sort and overflow icons; it tints to `surface-container` on scroll, and search replaces it with a transparent borderless field. Book cells: cover, title (title small, two lines), author (body small, graphite), then progress ("New", "37%", "Finished") left and the tick count right in graphite. Private books show a 24dp lock badge on a 92% paper circle at the cover's top-right corner.

### Book cover
2:3 cover art, 3dp corners, 1dp rule border at 60%, cover lift shadow. Books without art get a type cover: the title in title small at 13/17sp on `surface-container-high`, the author pinned to the foot in label small graphite.

### Progress line
A 2dp ink line on a rule track, butt caps, no gap, no stop indicator. Used for book progress (Reading now, reader bottom chrome) and the indeterminate loading bar under the status bar.

## Do's and Don'ts

### Do:
- **Do** set every reader-written word in graphite (`graphite` / `night-graphite`) and Schibsted Grotesk.
- **Do** quote book passages in the reader's chosen book face, in ink, beside a margin tick.
- **Do** express note state through the tick's line form: solid passage, dashed page, doubled open or just filed.
- **Do** show selection with `selectedLine()`: 1.5dp ink on transparent, 1dp rule when unselected.
- **Do** draw any in-book line with background gradients, since Readium night mode overrides borders.
- **Do** keep pencil yellow on the mic and its halo only, with the Night/Black resting variant (14% fill, 1.5dp ring).
- **Do** separate chrome from the page with 1dp rules and tonal containers instead of shadows.
- **Do** swap the whole palette with the reading theme (Paper, Night, Black, Auto), including app chrome.

### Don't:
- **Don't** lay coloured highlighter blocks over book text, in any colour.
- **Don't** use pencil yellow for selection, highlights, badges, focus or brand.
- **Don't** use `outline` (`graphite-light`) as a text colour; secondary text is `onSurfaceVariant` (graphite).
- **Don't** use the margin tick as ornament (headings, empty states, dividers); it marks notes and note counts.
- **Don't** mix selected-state treatments (fills, check icons, tonal chips); there is one ink line.
- **Don't** add persistent toolbars or overlays to the reader page.
- **Don't** use cream or sepia paper, or a serif in UI chrome.
- **Don't** add shadows to anything but book covers.
- **Don't** enable Dynamic Color.
