package com.karrot.platform.kafka.common.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {

    private val DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul")
    private val DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun now(): Instant = Instant.now()

    fun toLocalDateTime(instant: Instant, zoneId: ZoneId = DEFAULT_ZONE_ID): LocalDateTime {
        return LocalDateTime.ofInstant(instant, zoneId)
    }

    fun format(instant: Instant, pattern: String? = null): String {
        val formatter = pattern?.let { DateTimeFormatter.ofPattern(it) } ?: DEFAULT_FORMATTER
        return toLocalDateTime(instant).format(formatter)
    }

    fun parseInstant(dateTimeString: String, pattern: String? = null): Instant {
        val formatter = pattern?.let { DateTimeFormatter.ofPattern(it) } ?: DEFAULT_FORMATTER
        val localDateTime = LocalDateTime.parse(dateTimeString, formatter)
        return localDateTime.atZone(DEFAULT_ZONE_ID).toInstant()
    }
}
