package com.karrot.platform.kafka.domain.event.repository

import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventId
import com.karrot.platform.kafka.domain.event.model.EventStatus
import java.time.Instant

/**
 * 이벤트 저장소 인터페이스 - Domain Layer
 */
interface EventRepository {
    suspend fun save(event: Event): Event
    suspend fun findById(id: EventId): Event?
    suspend fun findByStatus(status: EventStatus): List<Event>
    suspend fun findFailedEventsOlderThan(timestamp: Instant): List<Event>
    suspend fun deleteArchivedEventsOlderThan(timestamp: Instant): Int
}