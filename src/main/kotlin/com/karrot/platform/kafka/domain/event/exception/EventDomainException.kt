package com.karrot.platform.kafka.domain.event.exception

import com.karrot.platform.kafka.common.exception.BusinessException
import com.karrot.platform.kafka.common.exception.ErrorCode

/**
 * 이벤트 도메인 예외
 */
sealed class EventDomainException(
    errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null
) : BusinessException(errorCode, message, cause)

class EventAlreadyPublishedException(eventId: String) : EventDomainException(
    ErrorCode.EVENT_ALREADY_PUBLISHED,
    "Event already published: $eventId"
)

class InvalidEventStateException(message: String) : EventDomainException(
    ErrorCode.EVENT_VALIDATION_FAILED,
    message
)
