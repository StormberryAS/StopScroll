package no.stormberry.stopscroll

/*
 * Consent to the in-app disclosure (spec 6.7, Play's prominent-disclosure rule).
 *
 * PER-APP CONSENT (0.0.3, decision UPG-2 b of 31 October 2026). The disclosure names the apps the
 * build watches, and an agreement counts for exactly the apps it named: the record keeps that list.
 * When a later build adds an app, the apps agreed to before keep working as set, and the new app is
 * not watched until the user reads the disclosure, which names every watched app, and agrees again:
 * the service receives nothing from it (while blocking is active the app is left out of the runtime
 * packageNames; otherwise the event types are 0 and the service receives no events at all). So 0.0.2 to 0.0.3 keeps YouTube and Instagram
 * blocking, and X waits; 0.0.3 to 0.0.4 keeps YouTube, Instagram and X blocking, and YouTube Music
 * waits (CONSENT-4: 0.0.4 adds one sentence about YouTube Music only, so no version bump).
 *
 * DISCLOSURE_VERSION: bump it only when what the disclosure says about apps already agreed to changes
 * (what it reads, does or keeps, or the "Other apps" rule). Adding an app does not bump it: the
 * record's app list decides per app. Version 2 since 0.0.2; a 0.0.1 agreement (version 1, YouTube
 * only) still counts for nothing, as in 0.0.2.
 *
 * Inert until consent: a user can turn the service on in Android's settings without passing the
 * disclosure. Without an agreed app ServiceConfig.plan sets the event types to 0, so the service
 * receives nothing and reads nothing, and the bridge refuses to open Accessibility settings.
 * DISCLOSURE_VERSION must equal the one in ts/core/copy.ts (tests/guards.test.ts checks).
 */

data class ConsentRecord(val date: String, val version: Int, val apps: List<String>)

object Consent {
    const val DISCLOSURE_VERSION = 2
    private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * The [watched] packages the stored [record] covers, in [watched]'s order: none unless the
     * record's date is a day and its version is [DISCLOSURE_VERSION].
     */
    fun agreedApps(record: ConsentRecord?, watched: List<String>): List<String> {
        if (record == null || record.version != DISCLOSURE_VERSION || !DAY.matches(record.date)) return emptyList()
        val named = record.apps.toSet()
        return watched.filter { it in named }
    }

    /** What "I agree" stores: the day, this disclosure's version and every watched app (the disclosure names them all). */
    fun recordFor(day: String, watched: List<String>): ConsentRecord = ConsentRecord(day, DISCLOSURE_VERSION, watched.toList())
}
