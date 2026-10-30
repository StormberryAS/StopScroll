package no.stormberry.stopscroll

/*
 * The headers of every response the app's own origin serves. The policy is the page's own (its meta
 * tag says the same): scripts, styles and images from the app only, and connect-src 'none', so the
 * page cannot open a connection even in principle. The app has no INTERNET permission besides.
 */
object ResponseHeaders {
    const val CONTENT_SECURITY_POLICY =
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; " +
            "connect-src 'none'; form-action 'none'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"

    val ALL: Map<String, String> = mapOf(
        "Content-Security-Policy" to CONTENT_SECURITY_POLICY,
        "X-Content-Type-Options" to "nosniff",
        "Cache-Control" to "no-store",
        "Referrer-Policy" to "no-referrer",
    )
}
