package com.karrot.platform.kafka.domain.event.model

/**
 * 이벤트 상태
 */
enum class EventStatus {
    CREATED,
    PUBLISHED,
    FAILED,
    ARCHIVED
}