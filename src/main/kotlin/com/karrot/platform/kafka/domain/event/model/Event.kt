package com.karrot.platform.kafka.domain.event.model

import org.flywaydb.core.internal.util.JsonUtils
import java.time.Instant

/**
 * 이벤트 도메인 모델 - DDD Aggregate Root
 */
class Event private constructor(
    val id: EventId,
    val type: EventType,
    val aggregateId: String,
    val payload: EventPayload,
    val metadata: EventMetadata,
    private var status: EventStatus,
    val createdAt: Instant,
    private var publishedAt: Instant? = null,
    private var failureReason: String? = null
) {

    companion object {
        fun create(
            type: EventType,
            aggregateId: String,
            payload: EventPayload,
            metadata: EventMetadata = EventMetadata.empty()
        ): Event {
            return Event(
                id = EventId.generate(),
                type = type,
                aggregateId = aggregateId,
                payload = payload,
                metadata = metadata,
                status = EventStatus.CREATED,
                createdAt = Instant.now()
            )
        }
    }

    fun markAsPublished() {
        require(status == EventStatus.CREATED || status == EventStatus.FAILED) {
            "Event can only be published from CREATED or FAILED status"
        }
        this.status = EventStatus.PUBLISHED
        this.publishedAt = Instant.now()
        this.failureReason = null
    }

    fun markAsFailed(reason: String) {
        require(status != EventStatus.PUBLISHED) {
            "Published event cannot be marked as failed"
        }
        this.status = EventStatus.FAILED
        this.failureReason = reason
    }

    fun canRetry(): Boolean = status == EventStatus.FAILED

    fun getStatus(): EventStatus = status
    fun getPublishedAt(): Instant? = publishedAt
    fun getFailureReason(): String? = failureReason
}




