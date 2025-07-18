package com.karrot.platform.kafka.application.usecase.event

import com.karrot.platform.kafka.application.port.output.EventOutputPort
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.common.exception.EventNotFoundException
import com.karrot.platform.kafka.domain.event.model.EventId
import com.karrot.platform.kafka.domain.event.model.EventStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@UseCase
class ReprocessEventUseCase(
    private val eventOutputPort: EventOutputPort
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    suspend fun reprocessEvent(eventId: String): EventResponse {
        val event = eventOutputPort.findEventById(EventId(eventId))
            ?: throw EventNotFoundException(eventId)

        if (!event.canRetry()) {
            throw IllegalStateException("Event cannot be retried: ${event.getStatus()}")
        }

        logger.info { "Reprocessing event: $eventId" }

        val publishResult = eventOutputPort.publishToKafka(event)

        if (publishResult.success) {
            event.markAsPublished()
        } else {
            event.markAsFailed(publishResult.error ?: "Reprocessing failed")
        }

        val updatedEvent = eventOutputPort.updateEventStatus(event)

        return EventResponse.from(updatedEvent, publishResult)
    }

    @Scheduled(fixedDelay = 60000) // 1분마다
    suspend fun reprocessFailedEvents() {
        val cutoffTime = Instant.now().minus(5, ChronoUnit.MINUTES)
        val failedEvents = eventOutputPort.findFailedEventsOlderThan(cutoffTime)

        if (failedEvents.isEmpty()) {
            return
        }

        logger.info { "Found ${failedEvents.size} failed events to reprocess" }

        failedEvents.forEach { event ->
            try {
                reprocessEvent(event.id.value)
            } catch (e: Exception) {
                logger.error(e) { "Failed to reprocess event: ${event.id}" }
            }
        }
    }

    fun streamFailedEvents(): Flow<Event> = flow {
        val failedEvents = eventOutputPort.findFailedEvents()
        failedEvents.forEach { emit(it) }
    }
}
