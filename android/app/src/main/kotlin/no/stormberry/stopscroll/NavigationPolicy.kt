package no.stormberry.stopscroll

import java.net.URI

/*
 * What the WebView may load. Only the app's own asset origin loads inside the app. Every other
 * address is blocked, except an exact entry of the fixed link list (LinkList), which opens in
 * another app after a tap. So the page cannot build an address that carries data out of the app,
 * and the app needs no INTERNET permission for its links.
 */
object NavigationPolicy {
    const val ASSET_HOST = "appassets.androidplatform.net"
    const val ASSET_ORIGIN = "https://$ASSET_HOST"

    enum class Decision { IN_APP, LINK, BLOCK }

    fun decide(url: String?): Decision {
        if (url.isNullOrBlank()) return Decision.BLOCK
        if (LinkList.idFor(url) != null) return Decision.LINK
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return Decision.BLOCK
        }
        val ok = uri.scheme?.lowercase() == "https" && uri.host?.lowercase() == ASSET_HOST &&
            (uri.port == -1 || uri.port == 443) && uri.rawUserInfo == null
        return if (ok) Decision.IN_APP else Decision.BLOCK
    }

    fun isAssetRequest(url: String?): Boolean = decide(url) == Decision.IN_APP

    fun isAssetOrigin(origin: String?): Boolean {
        val o = origin?.trimEnd('/') ?: return false
        return o == ASSET_ORIGIN || o == "$ASSET_ORIGIN:443"
    }
}
