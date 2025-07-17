package com.karrot.platform.kafka.domain.event.service

import com.karrot.platform.kafka.common.annotation.DomainService
import com.karrot.platform.kafka.domain.event.model.EventType
import com.karrot.platform.kafka.common.exception.EventValidationException

@DomainService
class EventValidator {

    fun validate(eventType: EventType, payload: Map<String, Any>) {
        when (eventType) {
            EventType.USER_CREATED -> validateUserCreatedEvent(payload)
            EventType.ITEM_CREATED -> validateItemCreatedEvent(payload)
            EventType.PAYMENT_REQUESTED -> validatePaymentRequestedEvent(payload)
            EventType.CHAT_MESSAGE_SENT -> validateChatMessageEvent(payload)
            else -> { /* 기본 검증만 수행 */ }
        }
    }

    private fun validateUserCreatedEvent(payload: Map<String, Any>) {
        requireField(payload, "userId")
        requireField(payload, "email")
        requireField(payload, "nickname")

        validateEmail(payload["email"] as? String)
    }

    private fun validateItemCreatedEvent(payload: Map<String, Any>) {
        requireField(payload, "itemId")
        requireField(payload, "sellerId")
        requireField(payload, "title")
        requireField(payload, "price")

        val price = payload["price"] as? Long ?: 0
        if (price < 0) {
            throw EventValidationException("Price cannot be negative")
        }
    }

    private fun validatePaymentRequestedEvent(payload: Map<String, Any>) {
        requireField(payload, "paymentId")
        requireField(payload, "buyerId")
        requireField(payload, "sellerId")
        requireField(payload, "amount")

        val amount = payload["amount"] as? Long ?: 0
        if (amount <= 0) {
            throw EventValidationException("Amount must be positive")
        }
    }

    private fun validateChatMessageEvent(payload: Map<String, Any>) {
        requireField(payload, "chatRoomId")
        requireField(payload, "senderId")
        requireField(payload, "message")

        val message = payload["message"] as? String ?: ""
        if (message.length > 1000) {
            throw EventValidationException("Message too long")
        }
    }

    private fun requireField(payload: Map<String, Any>, field: String) {
        if (!payload.containsKey(field) || payload[field] == null) {
            throw EventValidationException("Required field missing: $field")
        }
    }

    private fun validateEmail(email: String?) {
        if (email == null || !email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
            throw EventValidationException("Invalid email format")
        }
    }
}
