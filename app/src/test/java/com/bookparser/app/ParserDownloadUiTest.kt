package com.bookparser.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParserDownloadUiTest {
    private fun parserSource(): String {
        val candidates = listOf(
            File("src/main/html_sources/parser.html"),
            File("app/src/main/html_sources/parser.html")
        )
        return candidates.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
            ?: error("parser.html not found from ${File(".").absolutePath}")
    }

    @Test
    fun downloadMenuOffersAllThreeModesAndOldPublicationSelectorIsGone() {
        val html = parserSource()

        assertTrue(html.contains("downloadAllBooks('fb2')"))
        assertTrue(html.contains("downloadAllBooks('epub')"))
        assertTrue(html.contains("downloadAllBooks('both')"))
        assertFalse(html.contains("name=\"publishFormat\""))
        assertFalse(html.contains("downloadZip()"))
    }

    @Test
    fun generatedEpubHasRequiredPackageFilesAndStoredMimetype() {
        val html = parserSource()

        assertTrue(html.contains("epub.file('mimetype', 'application/epub+zip', { compression: 'STORE' })"))
        assertTrue(html.contains("META-INF/container.xml"))
        assertTrue(html.contains("OEBPS/content.opf"))
        assertTrue(html.contains("OEBPS/nav.xhtml"))
        assertTrue(html.contains("properties=\"cover-image\""))
    }

    @Test
    fun preparedDownloadsUseExpectedArchiveNamesAndPreserveOriginalEpub() {
        val html = parserSource()

        assertTrue(html.contains("baseName + '.fb2.zip'"))
        assertTrue(html.contains("baseName + '.epub.zip'"))
        assertTrue(html.contains("baseName + '.pdf.zip'"))
        assertTrue(html.contains("ext === 'epub' && book.originalBuffer"))
        assertTrue(html.contains("new Uint8Array(book.originalBuffer)"))
    }
}
