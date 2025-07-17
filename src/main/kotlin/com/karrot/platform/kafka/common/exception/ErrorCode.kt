package com.karrot.platform.kafka.common.exception

import org.springframework.http.HttpStatus

/**
 * 에러 코드 정의
 */
enum class ErrorCode(
    val code: String,
    val message: String,
    val httpStatus: HttpStatus
) {
    // Event 관련 에러
    EVENT_NOT_FOUND("EVT001", "이벤트를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    EVENT_PUBLICATION_FAILED("EVT002", "이벤트 발행에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    EVENT_ALREADY_PUBLISHED("EVT003", "이미 발행된 이벤트입니다", HttpStatus.BAD_REQUEST),
    EVENT_VALIDATION_FAILED("EVT004", "이벤트 검증에 실패했습니다", HttpStatus.BAD_REQUEST),

    // Schema 관련 에러
    SCHEMA_NOT_FOUND("SCH001", "스키마를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    SCHEMA_VALIDATION_FAILED("SCH002", "스키마 검증에 실패했습니다", HttpStatus.BAD_REQUEST),
    SCHEMA_COMPATIBILITY_FAILED("SCH003", "스키마 호환성 검증에 실패했습니다", HttpStatus.BAD_REQUEST),

    // Kafka 관련 에러
    KAFKA_CONNECTION_FAILED("KFK001", "Kafka 연결에 실패했습니다", HttpStatus.SERVICE_UNAVAILABLE),
    KAFKA_TIMEOUT("KFK002", "Kafka 요청 시간이 초과되었습니다", HttpStatus.REQUEST_TIMEOUT),

    // 일반 에러
    INVALID_REQUEST("GEN001", "잘못된 요청입니다", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("GEN500", "서버 내부 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR)
}
