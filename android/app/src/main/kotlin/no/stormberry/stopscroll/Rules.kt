package no.stormberry.stopscroll

import android.content.Context
import java.time.LocalDate

/** The screen rules bundled in the APK (assets/rules/screen-rules.json, copied from rules/ by the build). */
object Rules {
    const val ASSET = "rules/screen-rules.json"
    private var cached: ScreenRules? = null

    fun load(context: Context): ScreenRules =
        cached ?: context.assets.open(ASSET).use { ScreenRules.parse(String(it.readBytes(), Charsets.UTF_8)) }.also { cached = it }
}

/** The phone's local date, ISO (it only names the day; nothing is timed with it). */
object Today {
    fun iso(): String = LocalDate.now().toString()
}
