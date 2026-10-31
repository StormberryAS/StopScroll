package no.stormberry.stopscroll

/*
 * THE FIXED LINK LIST (spec 5, 6.8): every address the app can hand to another app. The page passes
 * only an ID (openLink), or taps an anchor whose address is exactly one of these; nothing else
 * opens. The email link is built here, in Kotlin, with the app's version, versionCode and package
 * name (read at run time), the Android release and each watched app's version, and no screen content;
 * the user's own mail app sends it, if the user chooses.
 */
object LinkList {
    val LINKS: Map<String, String> = linkedMapOf(
        "stormberry" to "https://stormberry.as",
        "brreg" to "https://virksomhet.brreg.no/nb/oppslag/enheter/937751249",
        "linkedin" to "https://linkedin.com/company/StormberryAS",
        "github" to "https://github.com/StormberryAS",
        "nostr" to "nostr:npub1zz9w77p2jkn95t0st9tcuea8zdn2vknf0ctrwyd4g46w3kswdccqesr0pa",
        "onion" to "http://stormberryr7fz5xdopi7f265d7id27bgizc6uxeliaepzstompcfpyd.onion",
        // The disclaimer's contact line (disclaimer.html, from the shared generator): a bare address,
        // nothing filled in.
        "contact" to "mailto:info@stormberry.as",
    )

    const val EMAIL_ID = "email"
    const val EMAIL_TO = "info@stormberry.as"

    val IDS: Set<String> get() = LINKS.keys + EMAIL_ID

    fun idFor(url: String): String? {
        val u = url.trim()
        // The WebView reports a bare origin with a trailing slash ("https://stormberry.as/").
        return LINKS.entries.firstOrNull { it.value == u || (it.value.startsWith("http") && "${it.value}/" == u) }?.key
    }

    /**
     * The "Did StopScroll miss a feed? Or did it block a normal video? Email us!" email (Marcos,
     * 2026-10-29): two empty lines for the user, then versions and the package name only, never
     * screen content. [packageName] is the running app's own (Context.packageName), so the Play
     * edition reports no.stormberry.stopscroll.play; it is never written here. [apps] is each watched
     * app's name and installed version (null: not installed), in the rules' order: one line each
     * (0.0.4: YouTube, Instagram, X and YouTube Music; 0.0.3 had the first three, 0.0.2 YouTube and
     * Instagram, 0.0.1 the one YouTube line).
     */
    fun email(versionName: String, versionCode: Long, packageName: String, androidRelease: String, apps: List<Pair<String, String?>>): String {
        val subject = "StopScroll $versionName: missed a feed or blocked a normal video"
        val body = listOf(
            "Which app:",
            "What happened:",
            "",
            "StopScroll $versionName ($versionCode)",
            packageName,
            "Android $androidRelease",
        ) + apps.map { (name, version) -> "$name ${version ?: "not installed"}" }
        return "mailto:$EMAIL_TO?subject=${enc(subject)}&body=${enc(body.joinToString("\n"))}"
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
