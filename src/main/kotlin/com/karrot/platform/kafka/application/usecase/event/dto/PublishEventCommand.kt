package com.karrot.platform.kafka.application.usecase.event.dto

import com.karrot.platform.kafka.domain.event.model.EventType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class PublishEventCommand(
    @field:NotNull(message = "Event type is required")
    val eventType: EventType,

    @field:NotBlank(message = "Aggregate ID is required")
    val aggregateId: String,

    @field:NotNull(message = "Payload is required")
    val payload: Map<String, Any>,

    val correlationId: String? = null,
    val userId: String? = null,
    val source: String? = null
)