package no.stormberry.stopscroll

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.IOException

/** Serves the bundled web app from assets/web/ with the app's headers; a miss is a 404, never a guess. */
class WebAssetHandler(private val assets: AssetManager) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse {
        val assetPath = AssetPaths.resolve(path) ?: return notFound()
        val mime = AssetPaths.mimeType(assetPath) ?: return notFound()
        return try {
            WebResourceResponse(mime, AssetPaths.encoding(assetPath), 200, "OK", ResponseHeaders.ALL, assets.open(assetPath))
        } catch (_: IOException) {
            notFound()
        }
    }

    companion object {
        fun notFound(): WebResourceResponse = empty(404, "Not Found")

        fun forbidden(): WebResourceResponse = empty(403, "Forbidden")

        private fun empty(code: Int, reason: String) =
            WebResourceResponse("text/plain", "utf-8", code, reason, ResponseHeaders.ALL, ByteArrayInputStream(ByteArray(0)))
    }
}
