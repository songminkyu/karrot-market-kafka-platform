package com.karrot.platform.kafka.domain.event.model
import com.karrot.platform.kafka.common.util.JsonUtils

/**
 * 이벤트 페이로드 - Value Object
 */
data class EventPayload(
    val data: Map<String, Any>,
    val version: String = "1.0"
) {
    fun toJson(): String = JsonUtils.toJson(data)

    companion object {
        fun fromJson(json: String): EventPayload {
            return EventPayload(JsonUtils.fromJson(json))
        }
    }
}
