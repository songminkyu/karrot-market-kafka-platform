package com.karrot.platform.kafka.application.usecase.event.dto

import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.service.PublishResult
import java.time.Instant

data class EventResponse(
    val eventId: String,
    val status: String,
    val publishedAt: Instant?,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null
) {
    companion object {
        fun from(event: Event, publishResult: PublishResult): EventResponse {
            return EventResponse(
                eventId = event.id.value,
                status = event.getStatus().name,
                publishedAt = event.getPublishedAt(),
                topic = publishResult.topic,
                partition = publishResult.partition,
                offset = publishResult.offset
            )
        }
    }
}
