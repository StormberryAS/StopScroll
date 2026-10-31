package no.stormberry.stopscroll

/*
 * Consent to the in-app disclosure (spec 6.7, Play's prominent-disclosure rule).
 *
 * The disclosure names the apps the build watches, so a consent counts only for the disclosure it
 * was given to: the same version of the text and the same list of apps. When a later build watches
 * one more app, the old consent no longer counts and the disclosure is shown again: 0.0.2 adds
 * Instagram, so a 0.0.1 consent (version 1, YouTube only) fails on both counts.
 *
 * Inert until consent: a user can turn the service on in Android's settings without passing the
 * disclosure. Without a valid consent ServiceConfig.plan sets the event types to 0, so the service
 * receives nothing and reads nothing, and the bridge refuses to open Accessibility settings.
 * DISCLOSURE_VERSION must equal the one in ts/core/copy.ts (tests/guards.test.ts checks). Version 2
 * (0.0.2): the disclosure names YouTube and Instagram and speaks of "a Shorts or Reels player".
 */

data class ConsentRecord(val date: String, val version: Int, val apps: List<String>)

object Consent {
    const val DISCLOSURE_VERSION = 2

    fun isValid(record: ConsentRecord?, watched: List<String>): Boolean =
        record != null && record.version == DISCLOSURE_VERSION && record.apps.toSet() == watched.toSet() &&
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(record.date)
}
