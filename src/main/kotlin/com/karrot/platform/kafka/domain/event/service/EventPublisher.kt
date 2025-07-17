package com.karrot.platform.kafka.domain.event.service
import com.karrot.platform.kafka.domain.event.model.Event

/**
 * 이벤트 발행 도메인 서비스
 */
interface EventPublisher {
    suspend fun publish(event: Event): PublishResult
    suspend fun publishBatch(events: List<Event>): List<PublishResult>
}

data class PublishResult(
    val eventId: String,
    val success: Boolean,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null,
    val error: String? = null
)
