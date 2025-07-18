package com.karrot.platform.kafka.application.usecase.event

import com.karrot.platform.kafka.application.port.input.EventInputPort
import com.karrot.platform.kafka.common.annotation.UseCase
import mu.KotlinLogging

@UseCase
class ConsumeEventUseCase : EventInputPort {

    private val logger = KotlinLogging.logger {}

    override suspend fun handleUserCreated(event: UserEventMessage) {
        logger.info { "Processing user created event: ${event.userId}" }

        // 비즈니스 로직 처리
        // 1. 사용자 프로필 초기화
        // 2. 환영 메시지 전송
        // 3. 추천 아이템 생성
    }

    override suspend fun handleItemCreated(event: ItemEventMessage) {
        logger.info { "Processing item created event: ${event.itemId}" }

        // 비즈니스 로직 처리
        // 1. 검색 인덱스 업데이트
        // 2. 카테고리별 집계 업데이트
        // 3. 알림 전송
    }

    override suspend fun handlePaymentCompleted(event: PaymentEventMessage) {
        logger.info { "Processing payment completed event: ${event.paymentId}" }

        // 비즈니스 로직 처리
        // 1. 판매 통계 업데이트
        // 2. 수수료 계산
        // 3. 정산 데이터 생성
    }
}
