package app.reed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScanCleanupTest {

    @Test
    fun joinsWordsHyphenatedAtAPrintLineEnd() {
        assertEquals(
            "Er konnte nicht älter als neunzehn gewesen sein. Sie steckten ihn in eine Zelle",
            ScanCleanup.clean("Er konnte nicht älter als neunzehn gewe-  sen sein. Sie steckten ihn in eine Zelle"),
        )
    }

    @Test
    fun keepsTheHyphenOfACompoundBrokenAtTheLineEnd() {
        assertEquals("die Quidditch-Weltmeisterschaft", ScanCleanup.clean("die Quidditch-  Weltmeisterschaft"))
    }

    @Test
    fun keepsSharedWordParts() {
        assertEquals("Hin-  und Rückweg", ScanCleanup.clean("Hin-  und Rückweg"))
    }

    @Test
    fun dropsAPageNumberMidSentence() {
        assertEquals(
            "geschafft zu haben«, fuhr er fort",
            ScanCleanup.clean("geschafft zu haben«,<br/>\n<br/>\n\t\t552<br/>\n<br/>\n\t\tfuhr er fort"),
        )
    }

    @Test
    fun dropsAPageNumberRunOntoTheLastLine() {
        assertEquals(
            "aussah, traf der Untersuchungsbericht",
            ScanCleanup.clean("aussah, traf  7<br/>\n<br/>\n\t\tder Untersuchungsbericht"),
        )
    }

    @Test
    fun keepsNumbersInRunningText() {
        val html = "Er war 17 Jahre alt.<br/>\n<br/>\nIm Jahr 1994<br/>"
        assertSame(html, ScanCleanup.clean(html))
    }

    @Test
    fun keepsTheParagraphBreakWhenThePageEndedWithOne() {
        assertEquals(
            "begraben.«<br/>\n<br/>\nSirius warf",
            ScanCleanup.clean("begraben.«<br/>\n<br/>\n553<br/>\n<br/>\nSirius warf"),
        )
    }

    @Test
    fun joinsAWordBrokenAcrossAPage() {
        assertEquals("hat abgenommen.", ScanCleanup.clean("hat abge-<br/>\n<br/>\n554<br/>\n<br/>\nnommen."))
    }

    @Test
    fun leavesOrdinaryBooksAlone() {
        val html = "<p>A well-made book,\n  with indented source.</p>\n<p>Chapter 12</p>"
        assertSame(html, ScanCleanup.clean(html))
    }
}
