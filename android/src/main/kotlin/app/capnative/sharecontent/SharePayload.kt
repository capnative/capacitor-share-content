package app.capnative.sharecontent

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

internal object ShareLimits {
    const val TITLE = 200
    const val TEXT = 5000
    const val URL = 2048
    const val FILE_URL = 4096
    const val FILES = 10
    const val DIALOG_TITLE = 80
}

internal data class PreparedShare(
    val title: String?,
    val text: String?,
    val url: String?,
    val files: List<String>,
    val dialogTitle: String,
)

internal class ShareException(val code: String, message: String) : Exception(message)

internal object SharePayloadParser {
    fun parse(raw: Any?): PreparedShare {
        val options = asMap(raw) ?: throw ShareException("INVALID_SHARE", "share() expects an options object.")
        val title = optionalText(options["title"], "title", ShareLimits.TITLE)
        val text = optionalText(options["text"], "text", ShareLimits.TEXT)
        val dialogTitle = optionalText(options["dialogTitle"], "dialogTitle", ShareLimits.DIALOG_TITLE) ?: "Share"
        val files = fileList(options["files"]).toMutableList()
        val link = options["url"]?.let { httpOrFile(it) }
        if (link is ShareLink.File) {
            files.add(0, link.value)
        }
        val unique = dedupe(files)
        if (unique.size > ShareLimits.FILES) {
            throw ShareException("INVALID_SHARE", "At most ${ShareLimits.FILES} files can be shared.")
        }
        val url = (link as? ShareLink.Http)?.value
        if (text == null && url == null && unique.isEmpty()) {
            throw ShareException("INVALID_SHARE", "Share at least one of text, url, or files.")
        }
        return PreparedShare(title, text, url, unique, dialogTitle)
    }

    fun localPath(fileUrl: String): String = URI(fileUrl).path

    private fun optionalText(value: Any?, name: String, max: Int): String? {
        if (value == null) return null
        val text = value as? String ?: throw ShareException("INVALID_SHARE", "$name must be a string.")
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length > max) {
            throw ShareException("INVALID_SHARE", "$name must be $max characters or fewer.")
        }
        return trimmed
    }

    private fun fileList(value: Any?): List<String> {
        if (value == null) return emptyList()
        val items = asList(value) ?: throw ShareException("INVALID_SHARE", "files must be an array of file:// URLs.")
        return items.map { item ->
            val text = item as? String ?: throw ShareException("INVALID_SHARE", "Each file must be a file:// URL.")
            fileUrl(text)
        }
    }

    private fun httpOrFile(value: Any): ShareLink {
        val text = value as? String ?: throw ShareException("INVALID_SHARE", "url must be a string.")
        val trimmed = text.trim()
        if (unsafe(trimmed)) {
            throw ShareException("INVALID_SHARE", "url must be an http, https, or file URL.")
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            throw ShareException("INVALID_SHARE", "url must be an http, https, or file URL.")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            if (trimmed.length > ShareLimits.URL) {
                throw ShareException("INVALID_SHARE", "url must be ${ShareLimits.URL} characters or fewer.")
            }
            if (uri.host.isNullOrBlank()) {
                throw ShareException("INVALID_SHARE", "url must include a host.")
            }
            return ShareLink.Http(trimmed)
        }
        if (scheme == "file") {
            return ShareLink.File(fileUrl(trimmed))
        }
        throw ShareException("INVALID_SHARE", "url must be an http, https, or file URL.")
    }

    private fun fileUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length > ShareLimits.FILE_URL) {
            throw ShareException("INVALID_SHARE", "A file URL must be ${ShareLimits.FILE_URL} characters or fewer.")
        }
        if (unsafe(trimmed)) {
            throw ShareException("INVALID_SHARE", "Each file must be a file:// URL.")
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            throw ShareException("INVALID_SHARE", "Each file must be a file:// URL.")
        }
        if (uri.scheme?.lowercase() != "file") {
            throw ShareException("INVALID_SHARE", "Each file must be a file:// URL.")
        }
        if (hasDotSegment(trimmed)) {
            throw ShareException("INVALID_SHARE", "File URLs must be an absolute local path.")
        }
        val host = uri.host
        if (!host.isNullOrEmpty() && !host.equals("localhost", ignoreCase = true)) {
            throw ShareException("INVALID_SHARE", "File URLs must point at a local file.")
        }
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            throw ShareException("INVALID_SHARE", "File URLs must be an absolute local path.")
        }
        val path = uri.path ?: ""
        if (!path.startsWith("/") || path == "/" || path.endsWith("/") || path.contains('\\') || path.contains('\u0000')) {
            throw ShareException("INVALID_SHARE", "File URLs must be an absolute local path.")
        }
        if (path.split('/').contains("..")) {
            throw ShareException("INVALID_SHARE", "File URLs must be an absolute local path.")
        }
        return trimmed
    }

    private fun hasDotSegment(value: String): Boolean {
        val decoded = try {
            java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: Exception) {
            return true
        }
        return decoded.split('/', '\\').contains("..")
    }

    private fun unsafe(value: String): Boolean {
        return value.any { char -> char.code <= 0x1F || char.code == 0x7F || char.isWhitespace() }
    }

    private fun dedupe(values: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        val unique = mutableListOf<String>()
        for (value in values) {
            if (seen.add(value)) unique.add(value)
        }
        return unique
    }

    private fun asMap(value: Any?): Map<String, Any?>? {
        return when (value) {
            is Map<*, *> -> {
                val map = linkedMapOf<String, Any?>()
                for ((key, item) in value) {
                    if (key !is String) return null
                    map[key] = unwrap(item)
                }
                map
            }
            is JSONObject -> {
                val map = linkedMapOf<String, Any?>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = unwrap(value.get(key))
                }
                map
            }
            else -> null
        }
    }

    private fun asList(value: Any?): List<Any?>? {
        return when (value) {
            is List<*> -> value.map { unwrap(it) }
            is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
            else -> null
        }
    }

    private fun unwrap(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> asMap(value)
            is JSONArray -> asList(value)
            else -> value
        }
    }
}

private sealed interface ShareLink {
    data class Http(val value: String) : ShareLink
    data class File(val value: String) : ShareLink
}
