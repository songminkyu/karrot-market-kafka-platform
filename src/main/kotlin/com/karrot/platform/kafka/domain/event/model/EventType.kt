package com.karrot.platform.kafka.domain.event.model

/**
 * 이벤트 타입
 */
enum class EventType {
    // User Domain Events
    USER_CREATED,
    USER_UPDATED,
    USER_VERIFIED,
    USER_LOCATION_UPDATED,

    // Item Domain Events
    ITEM_CREATED,
    ITEM_UPDATED,
    ITEM_SOLD,
    ITEM_RESERVED,
    ITEM_PRICE_CHANGED,

    // Chat Domain Events
    CHAT_MESSAGE_SENT,
    CHAT_MESSAGE_READ,
    CHAT_ROOM_CREATED,

    // Payment Domain Events
    PAYMENT_REQUESTED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,

    // Community Domain Events
    POST_CREATED,
    POST_LIKED,
    COMMENT_ADDED,
    NEIGHBOR_VERIFIED
}