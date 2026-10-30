package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckThrottleTest {
    private var t = 10_000L
    private val throttle = CheckThrottle { t }

    @Test fun aStateChangeIsCheckedAtOnce() {
        assertEquals(0L, throttle.delayFor(stateChange = true))
    }

    @Test fun contentBurstsGetOneCheckPer250MsWithATrailingOne() {
        assertEquals(0L, throttle.delayFor(false))
        assertNull(throttle.delayFor(false)) // already scheduled
        throttle.ran()
        t += 100
        assertEquals(150L, throttle.delayFor(false)) // the trailing check
        assertNull(throttle.delayFor(false))
        assertEquals(0L, throttle.delayFor(true)) // a state change replaces it
    }
}
