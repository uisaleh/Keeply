package app.keeply

import app.keeply.links.LinkMetadataFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkMetadataTest {
    @Test
    fun prefersOpenGraphTitle() {
        val html = """<html><head><title>Fallback</title><meta property="og:title" content="Shopify UX &amp; Design"></head></html>"""
        assertEquals("Shopify UX & Design", LinkMetadataFetcher.extractTitle(html))
    }

    @Test
    fun collapsesWhitespaceInTitle() {
        assertEquals("Hello World", LinkMetadataFetcher.extractTitle("<title>\n  Hello\n   World </title>"))
    }

    @Test
    fun missingTitleIsNull() {
        assertNull(LinkMetadataFetcher.extractTitle("<html><body>No title</body></html>"))
        assertNull(LinkMetadataFetcher.extractTitle("<title>   </title>"))
    }
}
