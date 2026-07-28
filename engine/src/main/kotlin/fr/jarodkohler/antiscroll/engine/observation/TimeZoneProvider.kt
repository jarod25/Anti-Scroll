package fr.jarodkohler.antiscroll.engine.observation

import java.time.ZoneId

fun interface TimeZoneProvider {
    fun currentZoneId(): ZoneId
}
