package com.karrot.platform.kafka.common.exception

/**
 * 비즈니스 로직 예외
 */
open class BusinessException(
            val errorCode: ErrorCode,
            override val message: String? = errorCode.message,
            override val cause: Throwable? = null
        ) : RuntimeException(message, cause) {
            fun getHttpStatus() = errorCode.httpStatus
            fun getCode() = errorCode.code
        }

/**
 * 이벤트 발행 실패 예외
 */
class EventPublicationException(
    message: String,
    cause: Throwable? = null
) : BusinessException(ErrorCode.EVENT_PUBLICATION_FAILED, message, cause)

/**
 * 이벤트를 찾을 수 없음
 */
class EventNotFoundException(
    eventId: String
) : BusinessException(ErrorCode.EVENT_NOT_FOUND, "Event not found: $eventId")

/**
 * 스키마 검증 실패
 */
class SchemaValidationException(
    message: String
) : BusinessException(ErrorCode.SCHEMA_VALIDATION_FAILED, message)

/**
 * 이벤트 검증 실패
 */
class EventValidationException(
message: String
) : BusinessException(ErrorCode.EVENT_VALIDATION_FAILED, message)