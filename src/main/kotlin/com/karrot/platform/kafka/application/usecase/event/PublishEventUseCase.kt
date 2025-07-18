package com.karrot.platform.kafka.application.usecase.event

import com.karrot.platform.kafka.application.port.input.EventInputPort
import com.karrot.platform.kafka.application.port.output.EventOutputPort
import com.karrot.platform.kafka.application.usecase.event.dto.PublishEventCommand
import com.karrot.platform.kafka.application.usecase.event.dto.EventResponse
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventPayload
import com.karrot.platform.kafka.domain.event.model.EventMetadata
import org.springframework.transaction.annotation.Transactional
import mu.KotlinLogging

/**
 * 이벤트 발행 유스케이스
 */
@UseCase
@Transactional
class PublishEventUseCase(
    private val eventOutputPort: EventOutputPort,
    private val eventValidator: EventValidator
) : EventInputPort {

    private val logger = KotlinLogging.logger {}

    override suspend fun publishEvent(command: PublishEventCommand): EventResponse {
        logger.info { "Publishing event: ${command.eventType}" }

        // 1. 커맨드 검증
        eventValidator.validate(command)

        // 2. 도메인 이벤트 생성
        val event = Event.create(
            type = command.eventType,
            aggregateId = command.aggregateId,
            payload = EventPayload(command.payload),
            metadata = EventMetadata(
                correlationId = command.correlationId,
                userId = command.userId,
                source = command.source
            )
        )

        // 3. 이벤트 저장 (Transactional Outbox Pattern)
        val savedEvent = eventOutputPort.saveEvent(event)

        // 4. Kafka로 발행 (트랜잭션 커밋 후)
        try {
            val publishResult = eventOutputPort.publishToKafka(savedEvent)

            if (publishResult.success) {
                savedEvent.markAsPublished()
                eventOutputPort.updateEventStatus(savedEvent)
            } else {
                savedEvent.markAsFailed(publishResult.error ?: "Unknown error")
                eventOutputPort.updateEventStatus(savedEvent)
            }

            return EventResponse.from(savedEvent, publishResult)

        } catch (e: Exception) {
            logger.error(e) { "Failed to publish event: ${event.id}" }
            savedEvent.markAsFailed(e.message ?: "Publication failed")
            eventOutputPort.updateEventStatus(savedEvent)
            throw EventPublicationException("Failed to publish event", e)
        }
    }
}
