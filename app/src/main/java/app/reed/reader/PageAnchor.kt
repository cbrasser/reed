package app.reed.reader

import app.reed.data.ReadingSettings
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

/** Settings that reflow the text, so the page has to be found again afterwards. */
fun ReadingSettings.reflowsFrom(other: ReadingSettings): Boolean =
    typeface != other.typeface || fontScale != other.fontScale ||
        lineSpacing != other.lineSpacing || margins != other.margins || layout != other.layout

/**
 * The first words on screen, as a locator Readium finds again by its text. Readium itself keeps
 * only a rough position through a reflow and lands a page or more early; this is exact.
 */
@OptIn(ExperimentalReadiumApi::class)
suspend fun EpubNavigatorFragment.firstVisibleWords(): Locator? {
    val here = currentLocator.value
    val json = evaluateJavascript(FIRST_VISIBLE_WORDS)?.takeIf { it != "null" } ?: return null
    // Depending on the WebView, the result is the JSON object or a string literal holding it.
    val words = runCatching { JSONObject(json) }.getOrNull()
        ?: runCatching { JSONObject(JSONObject("{\"v\":$json}").getString("v")) }.getOrNull()
        ?: return null
    val highlight = words.optString("highlight").takeIf { it.isNotBlank() } ?: return null
    return here.copy(
        text = Locator.Text(
            before = words.optString("before").ifEmpty { null },
            highlight = highlight,
            after = words.optString("after").ifEmpty { null },
        ),
    )
}

/** Where the sentence being read aloud is, relative to the screen. */
enum class SpokenPlacement { NONE, ABOVE, VISIBLE, RUNS_OFF, BELOW }

@OptIn(ExperimentalReadiumApi::class)
suspend fun EpubNavigatorFragment.spokenPlacement(): SpokenPlacement {
    val result = evaluateJavascript(SPOKEN_PLACEMENT)?.trim('"') ?: return SpokenPlacement.NONE
    return SpokenPlacement.entries.firstOrNull { it.name == result } ?: SpokenPlacement.NONE
}

// The spoken-sentence decoration is drawn as one box per line; their extent says where it is.
private const val SPOKEN_PLACEMENT = """
(function () {
  var boxes = document.querySelectorAll('.reed-spoken');
  if (!boxes.length) return 'NONE';
  var w = window.innerWidth, h = window.innerHeight;
  var top = Infinity, bottom = -Infinity, left = Infinity, right = -Infinity;
  boxes.forEach(function (b) {
    var r = b.getBoundingClientRect();
    top = Math.min(top, r.top); bottom = Math.max(bottom, r.bottom);
    left = Math.min(left, r.left); right = Math.max(right, r.right);
  });
  if (bottom <= 0 || right <= 0) return 'ABOVE';
  if (top >= h || left >= w) return 'BELOW';
  if (bottom > h || right > w) return 'RUNS_OFF';
  return 'VISIBLE';
})()
"""

// Finds the first character laid out on screen and returns the words starting there. Text before
// the screen sits left of it in pages (columns scroll sideways) and above it when scrolling.
private const val FIRST_VISIBLE_WORDS = """
(function () {
  var width = window.innerWidth, height = window.innerHeight;
  function rectAt(node, offset) {
    var range = document.createRange();
    range.setStart(node, offset);
    range.setEnd(node, offset + 1);
    var rects = range.getClientRects();
    return rects.length ? rects[0] : null;
  }
  function visible(r) { return r && r.right > 0 && r.left < width && r.bottom > 0 && r.top < height; }
  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
    acceptNode: function (n) { return /\S/.test(n.data) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT; }
  });
  var node;
  while ((node = walker.nextNode())) {
    var whole = document.createRange();
    whole.selectNodeContents(node);
    var rects = Array.prototype.slice.call(whole.getClientRects());
    if (!rects.some(visible)) continue;
    var lo = 0, hi = node.data.length - 1;
    while (lo < hi) {
      var mid = (lo + hi) >> 1, r = rectAt(node, mid);
      if (r && (r.right <= 0 || r.bottom <= 0)) lo = mid + 1; else hi = mid;
    }
    var start = lo;
    while (start > 0 && /\S/.test(node.data[start - 1])) start--;
    var first = rectAt(node, start);
    if (start < lo && first && (first.right <= 0 || first.bottom <= 0)) start = lo;
    var text = node.data;
    return JSON.stringify({
      before: text.slice(Math.max(0, start - 40), start),
      highlight: text.slice(start, start + 40),
      after: text.slice(start + 40, start + 80)
    });
  }
  return null;
})()
"""
