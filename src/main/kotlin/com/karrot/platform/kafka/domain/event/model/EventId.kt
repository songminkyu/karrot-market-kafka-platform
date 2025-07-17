package com.karrot.platform.kafka.domain.event.model

import java.util.UUID

/**
 * 이벤트 ID - Value Object
 */
@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId cannot be blank" }
    }

    companion object {
        fun generate(): EventId = EventId(UUID.randomUUID().toString())
        fun of(value: String): EventId = EventId(value)
    }
}