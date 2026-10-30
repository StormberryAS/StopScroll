package no.stormberry.stopscroll

/*
 * Maps a request path of the app's own origin to a file under assets/web/ (CheckLink's helper,
 * narrowed to the types StopScroll's page uses). Anything odd (a backslash, a NUL, an empty, dot or
 * dot-dot segment, a hidden file, an unknown type) is refused, never guessed.
 */
object AssetPaths {
    const val ROOT = "web"

    private val TYPES = mapOf(
        "html" to "text/html",
        "js" to "text/javascript",
        "css" to "text/css",
        "svg" to "image/svg+xml",
        "txt" to "text/plain",
        "png" to "image/png",
        "woff2" to "font/woff2",
    )

    fun resolve(path: String?): String? {
        var p = path ?: return null
        if (p.contains('\\') || p.contains('\u0000') || p.startsWith("/")) return null
        if (p.isEmpty() || p.endsWith("/")) p += "index.html"
        val segments = p.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) return null
        if (extension(p) !in TYPES) return null
        return "$ROOT/$p"
    }

    fun mimeType(assetPath: String): String? = TYPES[extension(assetPath)]

    /** Text types carry utf-8; the font and the image are bytes. */
    fun encoding(assetPath: String): String? = if (extension(assetPath) in BINARY) null else "utf-8"

    private val BINARY = setOf("png", "woff2")

    private fun extension(p: String): String = p.substringAfterLast('/').substringAfterLast('.', "").lowercase()
}
