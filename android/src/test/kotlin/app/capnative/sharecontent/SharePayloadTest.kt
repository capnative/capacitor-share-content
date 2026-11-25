package app.capnative.sharecontent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharePayloadTest {
    @Test
    fun keepsTextAndHttpsUrl() {
        val prepared = SharePayloadParser.parse(
            mapOf(
                "title" to "  Notes  ",
                "text" to " Hello ",
                "url" to "https://example.com/a",
                "dialogTitle" to "   ",
            ),
        )
        assertEquals("Notes", prepared.title)
        assertEquals("Hello", prepared.text)
        assertEquals("https://example.com/a", prepared.url)
        assertEquals("Share", prepared.dialogTitle)
        assertEquals(emptyList<String>(), prepared.files)
    }

    @Test
    fun treatsAFileUrlAsAFile() {
        val prepared = SharePayloadParser.parse(
            mapOf(
                "url" to "file:///tmp/photo.jpg",
                "files" to listOf("file:///tmp/photo.jpg", "file://localhost/tmp/other.png"),
            ),
        )
        assertNull(prepared.url)
        assertEquals(listOf("file:///tmp/photo.jpg", "file://localhost/tmp/other.png"), prepared.files)
        assertEquals("/tmp/photo.jpg", SharePayloadParser.localPath(prepared.files[0]))
    }

    @Test
    fun rejectsEmptyAndUnsafeInput() {
        assertEquals("INVALID_SHARE", code(emptyMap()))
        assertEquals("INVALID_SHARE", code(mapOf("title" to "Only a subject")))
        assertEquals("INVALID_SHARE", code(mapOf("text" to 1)))
        assertEquals("INVALID_SHARE", code(mapOf("url" to "javascript:alert(1)")))
        assertEquals("INVALID_SHARE", code(mapOf("url" to "data:text/plain,hello")))
        assertEquals("INVALID_SHARE", code(mapOf("url" to "file:///tmp/../etc/passwd")))
        assertEquals("INVALID_SHARE", code(mapOf("files" to listOf("https://example.com/a"))))
        assertEquals("INVALID_SHARE", code(mapOf("files" to listOf("file://files.example/tmp/a.jpg"))))
        assertEquals("INVALID_SHARE", code(mapOf("text" to "x".repeat(5001))))
        assertEquals(
            "INVALID_SHARE",
            code(mapOf("files" to (0 until 11).map { "file:///tmp/$it.txt" })),
        )
    }

    private fun code(value: Map<String, Any?>): String {
        return try {
            SharePayloadParser.parse(value)
            "none"
        } catch (error: ShareException) {
            error.code
        }
    }
}
