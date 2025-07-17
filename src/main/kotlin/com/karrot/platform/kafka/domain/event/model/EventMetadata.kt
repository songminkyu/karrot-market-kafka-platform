package com.karrot.platform.kafka.domain.event.model


/**
 * 이벤트 메타데이터 - Value Object
 */
data class EventMetadata(
    val correlationId: String? = null,
    val userId: String? = null,
    val source: String? = null,
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        fun empty() = EventMetadata()
    }
}
