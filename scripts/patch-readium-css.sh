#!/usr/bin/env bash
# Writes Reed's copy of Readium CSS's ReadiumCSS-after.css, which replaces the one in the Readium
# navigator (an app asset at the same path wins). Run again after upgrading Readium.
#
# With publisher styles off (needed for Reed's line spacing and hyphenation), Readium CSS forces
# every p and div to body size and puts h1–h6 on its own type scale. Books that style chapter
# titles as p or div classes lose them, and real headings shrink. The patch keeps the body-size
# rule for unclassed p and div only, drops the heading scale, and hides printed page-break markers.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(sed -n 's/^readium = "\(.*\)"/\1/p' gradle/libs.versions.toml)
AAR=$(find "$HOME/.gradle/caches/modules-2" -name "readium-navigator-$VERSION.aar" | head -1)
[[ -n "$AAR" ]] || { echo "readium-navigator-$VERSION.aar not in the Gradle cache; build once first."; exit 1; }

OUT=app/src/main/assets/readium/readium-css/ReadiumCSS-after.css
mkdir -p "$(dirname "$OUT")"
unzip -p "$AAR" assets/readium/readium-css/ReadiumCSS-after.css | python3 -c '
import re, sys
css = sys.stdin.read()
on = ":root[style*=readium-advanced-on] "
body = on + "dd," + on + "div," + on + "li," + on + "p," + on + "pre{font-size:1rem!important}"
assert body in css, "body-size rule not found; Readium CSS changed, update this script"
css = css.replace(body, body.replace(on + "div,", on + "div:not([class]),").replace(on + "p,", on + "p:not([class]),"))
css, headings = re.subn(re.escape(on) + r"h[1-3]\{font-size:[^}]*\}", "", css)
assert headings == 3, f"expected 3 heading rules, found {headings}"
small = on + "h4," + on + "h5," + on + "h6{font-size:1rem!important}"
assert small in css, "h4-h6 rule not found; Readium CSS changed, update this script"
css = css.replace(small, "")
css += "\n/* Reed: printed page-break markers are not part of the text. */\n"
css += "[*|type~=pagebreak],[epub\\:type~=pagebreak],[role~=doc-pagebreak]{display:none!important}\n"
sys.stdout.write(css)
' > "$OUT"
echo "Wrote $OUT from Readium $VERSION."
