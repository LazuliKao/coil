package coil3.test.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeClock(var epochMillis: Long = 0) : java.time.Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): java.time.Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(epochMillis)
}
