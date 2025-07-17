# 당근마켓 Kafka 플랫폼 - 추가 구현 상세

## 10. Common 모듈 구현

### common/annotation/DomainService.kt
```kotlin
package com.karrot.platform.kafka.common.annotation

import org.springframework.stereotype.Component

/**
 * 도메인 서비스를 나타내는 어노테이션
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
annotation class DomainService
```

### common/annotation/UseCase.kt
```kotlin
package com.karrot.platform.kafka.common.annotation

import org.springframework.stereotype.Service

/**
 * 유스케이스를 나타내는 어노테이션
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class UseCase
```

### common/exception/BusinessException.kt
```kotlin
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
```

### common/exception/ErrorCode.kt
```kotlin
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
```

### common/util/JsonUtils.kt
```kotlin
package com.karrot.platform.kafka.common.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * JSON 유틸리티
 */
object JsonUtils {
    
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
    }
    
    fun toJson(obj: Any): String = objectMapper.writeValueAsString(obj)
    
    inline fun <reified T> fromJson(json: String): T = objectMapper.readValue(json)
    
    fun toMap(obj: Any): Map<String, Any> {
        return objectMapper.convertValue(obj, Map::class.java) as Map<String, Any>
    }
}
```

## 11. Infrastructure 상세 구현

### infrastructure/kafka/consumer/ErrorHandlingConsumer.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.consumer

import com.karrot.platform.kafka.infrastructure.kafka.handler.RetryPolicy
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 에러 처리가 포함된 Consumer 기본 클래스
 */
@Component
abstract class ErrorHandlingConsumer<T>(
    private val retryPolicy: RetryPolicy
) {
    
    private val logger = KotlinLogging.logger {}
    
    protected suspend fun processWithRetry(
        message: T,
        acknowledgment: Acknowledgment,
        processor: suspend (T) -> Unit
    ) {
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < retryPolicy.maxAttempts) {
            try {
                processor(message)
                acknowledgment.acknowledge()
                return
            } catch (e: Exception) {
                attempt++
                lastException = e
                
                if (isRetryable(e) && attempt < retryPolicy.maxAttempts) {
                    val delayMs = retryPolicy.calculateDelay(attempt)
                    logger.warn { 
                        "Retryable error occurred (attempt $attempt/${retryPolicy.maxAttempts}), " +
                        "retrying after ${delayMs}ms: ${e.message}" 
                    }
                    delay(delayMs)
                } else {
                    break
                }
            }
        }
        
        // 최종 실패 처리
        handleFinalFailure(message, lastException!!, acknowledgment)
    }
    
    protected open fun isRetryable(exception: Exception): Boolean {
        return when (exception) {
            is IllegalStateException,
            is IllegalArgumentException -> false
            else -> true
        }
    }
    
    protected abstract suspend fun handleFinalFailure(
        message: T,
        exception: Exception,
        acknowledgment: Acknowledgment
    )
}
```

### infrastructure/kafka/handler/RetryPolicy.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.handler

/**
 * 재시도 정책
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val multiplier: Double = 2.0
) {
    fun calculateDelay(attempt: Int): Long {
        val delay = (initialDelayMs * Math.pow(multiplier, (attempt - 1).toDouble())).toLong()
        return minOf(delay, maxDelayMs)
    }
}
```

### infrastructure/kafka/partitioner/LocationBasedPartitioner.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.partitioner

import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.utils.Utils

/**
 * 위치 기반 파티셔너 - 동네별로 파티션 분배
 */
class LocationBasedPartitioner : Partitioner {
    
    private val neighborhoodPartitionMap = mutableMapOf<String, Int>()
    
    override fun partition(
        topic: String,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster
    ): Int {
        val partitionCount = cluster.partitionCountForTopic(topic)
        
        // 키가 동네 정보를 포함하는 경우
        if (key is String && key.contains(":")) {
            val neighborhood = key.substringBefore(":")
            
            // 동네별로 일관된 파티션 할당
            return neighborhoodPartitionMap.computeIfAbsent(neighborhood) {
                Math.abs(neighborhood.hashCode()) % partitionCount
            }
        }
        
        // 기본 파티셔닝
        return if (keyBytes != null) {
            Utils.toPositive(Utils.murmur2(keyBytes)) % partitionCount
        } else {
            // 키가 없는 경우 라운드 로빈
            Utils.toPositive(Utils.murmur2(valueBytes)) % partitionCount
        }
    }
    
    override fun close() {
        neighborhoodPartitionMap.clear()
    }
    
    override fun configure(configs: Map<String, *>) {
        // 설정이 필요한 경우 구현
    }
}
```

### infrastructure/monitoring/KafkaMetricsCollector.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.Metric
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import mu.KotlinLogging

/**
 * Kafka 메트릭 수집기
 */
@Component
class KafkaMetricsCollector(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val adminClient: AdminClient,
    private val meterRegistry: MeterRegistry
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Scheduled(fixedDelay = 30000) // 30초마다
    fun collectProducerMetrics() {
        try {
            kafkaTemplate.metrics().forEach { (metricName, metric) ->
                when (metricName.name()) {
                    "record-send-rate" -> {
                        meterRegistry.gauge(
                            "kafka.producer.send.rate",
                            Tags.of("client.id", metricName.tags()["client-id"] ?: "unknown"),
                            metric.metricValue() as Double
                        )
                    }
                    "record-error-rate" -> {
                        meterRegistry.gauge(
                            "kafka.producer.error.rate",
                            Tags.of("client.id", metricName.tags()["client-id"] ?: "unknown"),
                            metric.metricValue() as Double
                        )
                    }
                    "request-latency-avg" -> {
                        meterRegistry.gauge(
                            "kafka.producer.latency.avg",
                            Tags.of("client.id", metricName.tags()["client-id"] ?: "unknown"),
                            metric.metricValue() as Double
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to collect producer metrics" }
        }
    }
    
    @Scheduled(fixedDelay = 60000) // 1분마다
    fun collectConsumerLagMetrics() {
        try {
            val consumerGroups = adminClient.listConsumerGroups().all().get()
            
            consumerGroups.forEach { group ->
                try {
                    val offsets = adminClient
                        .listConsumerGroupOffsets(group.groupId())
                        .partitionsToOffsetAndMetadata()
                        .get()
                    
                    offsets.forEach { (topicPartition, offsetAndMetadata) ->
                        val endOffset = getEndOffset(topicPartition)
                        val lag = endOffset - offsetAndMetadata.offset()
                        
                        meterRegistry.gauge(
                            "kafka.consumer.lag",
                            Tags.of(
                                "group", group.groupId(),
                                "topic", topicPartition.topic(),
                                "partition", topicPartition.partition().toString()
                            ),
                            lag.toDouble()
                        )
                    }
                } catch (e: Exception) {
                    logger.warn { "Failed to get lag for group ${group.groupId()}: ${e.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to collect consumer lag metrics" }
        }
    }
    
    private fun getEndOffset(topicPartition: org.apache.kafka.common.TopicPartition): Long {
        val endOffsets = adminClient.listOffsets(
            mapOf(topicPartition to org.apache.kafka.clients.admin.OffsetSpec.latest())
        ).all().get()
        
        return endOffsets[topicPartition]?.offset() ?: 0L
    }
}
```

## 12. 실제 비즈니스 로직 구현

### application/usecase/payment/ProcessPaymentUseCase.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.payment

import com.karrot.platform.kafka.application.port.output.EventOutputPort
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventType
import com.karrot.platform.kafka.domain.payment.saga.PaymentSaga
import com.karrot.platform.kafka.domain.payment.saga.SagaResult
import org.springframework.transaction.annotation.Transactional
import mu.KotlinLogging

/**
 * 당근페이 결제 처리 유스케이스
 */
@UseCase
@Transactional
class ProcessPaymentUseCase(
    private val paymentSaga: PaymentSaga,
    private val eventOutputPort: EventOutputPort
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun processPayment(command: PaymentCommand): PaymentResult {
        logger.info { "Processing payment: ${command.paymentId}" }
        
        // 1. 결제 요청 이벤트 생성
        val requestEvent = createPaymentRequestedEvent(command)
        eventOutputPort.saveEvent(requestEvent)
        
        // 2. Saga 실행
        val sagaResult = paymentSaga.execute(command.paymentId)
        
        // 3. 결과에 따른 이벤트 발행
        return when (sagaResult) {
            is SagaResult.Success -> {
                val completedEvent = createPaymentCompletedEvent(command, sagaResult)
                eventOutputPort.publishToKafka(completedEvent)
                
                PaymentResult.Success(
                    paymentId = command.paymentId,
                    transactionId = sagaResult.sagaId
                )
            }
            
            is SagaResult.Failure -> {
                val failedEvent = createPaymentFailedEvent(command, sagaResult)
                eventOutputPort.publishToKafka(failedEvent)
                
                PaymentResult.Failure(
                    paymentId = command.paymentId,
                    reason = sagaResult.reason
                )
            }
        }
    }
    
    private fun createPaymentRequestedEvent(command: PaymentCommand): Event {
        return Event.create(
            type = EventType.PAYMENT_REQUESTED,
            aggregateId = command.paymentId,
            payload = EventPayload(
                mapOf(
                    "paymentId" to command.paymentId,
                    "buyerId" to command.buyerId,
                    "sellerId" to command.sellerId,
                    "itemId" to command.itemId,
                    "amount" to command.amount,
                    "paymentMethod" to command.paymentMethod.name
                )
            ),
            metadata = EventMetadata(
                userId = command.buyerId,
                source = "PAYMENT_SERVICE"
            )
        )
    }
    
    private fun createPaymentCompletedEvent(
        command: PaymentCommand,
        result: SagaResult.Success
    ): Event {
        return Event.create(
            type = EventType.PAYMENT_COMPLETED,
            aggregateId = command.paymentId,
            payload = EventPayload(
                mapOf(
                    "paymentId" to command.paymentId,
                    "transactionId" to result.sagaId,
                    "completedAt" to Instant.now().toString()
                )
            ),
            metadata = EventMetadata(
                userId = command.buyerId,
                correlationId = result.sagaId,
                source = "PAYMENT_SERVICE"
            )
        )
    }
    
    private fun createPaymentFailedEvent(
        command: PaymentCommand,
        result: SagaResult.Failure
    ): Event {
        return Event.create(
            type = EventType.PAYMENT_FAILED,
            aggregateId = command.paymentId,
            payload = EventPayload(
                mapOf(
                    "paymentId" to command.paymentId,
                    "reason" to result.reason,
                    "failedAt" to Instant.now().toString()
                )
            ),
            metadata = EventMetadata(
                userId = command.buyerId,
                correlationId = result.sagaId,
                source = "PAYMENT_SERVICE"
            )
        )
    }
}

data class PaymentCommand(
    val paymentId: String,
    val buyerId: String,
    val sellerId: String,
    val itemId: String,
    val amount: Long,
    val paymentMethod: PaymentMethod
)

enum class PaymentMethod {
    KARROT_PAY, BANK_TRANSFER, CARD
}

sealed class PaymentResult {
    data class Success(val paymentId: String, val transactionId: String) : PaymentResult()
    data class Failure(val paymentId: String, val reason: String) : PaymentResult()
}
```

### infrastructure/kafka/listener/PaymentEventListener.kt
```kotlin
package com.karrot.platform.kafka.interfaces.kafka.listener

import com.karrot.platform.kafka.application.port.input.PaymentEventInputPort
import com.karrot.platform.kafka.infrastructure.kafka.consumer.ErrorHandlingConsumer
import com.karrot.platform.kafka.infrastructure.kafka.handler.RetryPolicy
import com.karrot.platform.kafka.interfaces.kafka.handler.DlqHandler
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import mu.KotlinLogging

/**
 * 결제 이벤트 리스너
 */
@Component
class PaymentEventListener(
    private val paymentEventInputPort: PaymentEventInputPort,
    private val dlqHandler: DlqHandler,
    retryPolicy: RetryPolicy
) : ErrorHandlingConsumer<PaymentEventMessage>(retryPolicy) {
    
    private val logger = KotlinLogging.logger {}
    
    @KafkaListener(
        topics = ["karrot.payment.events"],
        groupId = "payment-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    suspend fun handlePaymentEvent(
        record: ConsumerRecord<String, PaymentEventMessage>,
        acknowledgment: Acknowledgment
    ) {
        val message = record.value()
        
        logger.info { "Processing payment event: ${message.eventType}" }
        
        processWithRetry(message, acknowledgment) { msg ->
            when (msg.eventType) {
                "PAYMENT_REQUESTED" -> paymentEventInputPort.handlePaymentRequested(msg)
                "PAYMENT_COMPLETED" -> paymentEventInputPort.handlePaymentCompleted(msg)
                "PAYMENT_FAILED" -> paymentEventInputPort.handlePaymentFailed(msg)
                "PAYMENT_REFUNDED" -> paymentEventInputPort.handlePaymentRefunded(msg)
                else -> throw IllegalArgumentException("Unknown payment event type: ${msg.eventType}")
            }
        }
    }
    
    override suspend fun handleFinalFailure(
        message: PaymentEventMessage,
        exception: Exception,
        acknowledgment: Acknowledgment
    ) {
        logger.error(exception) { "Failed to process payment event after retries: ${message.eventId}" }
        
        // DLQ로 전송
        dlqHandler.sendToDlq(
            originalTopic = "karrot.payment.events",
            message = message,
            exception = exception
        )
        
        // 메시지 처리 완료 (다음 메시지 처리를 위해)
        acknowledgment.acknowledge()
    }
}

data class PaymentEventMessage(
    val eventId: String,
    val eventType: String,
    val paymentId: String,
    val payload: Map<String, Any>,
    val timestamp: Instant
)
```

## 13. Kafka Streams 고급 구현

### infrastructure/kafka/streams/UserActivityAggregator.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.streams

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.WindowStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * 사용자 활동 집계 스트림 프로세서
 */
@Configuration
class UserActivityAggregator {
    
    @Bean
    fun buildUserActivityPipeline(builder: StreamsBuilder): KStream<String, UserActivity> {
        
        // 사용자 활동 스트림
        val activities = builder.stream<String, UserActivity>(
            "karrot.user.activities",
            Consumed.with(Serdes.String(), JsonSerde(UserActivity::class.java))
        )
        
        // 1. 사용자별 세션 분석
        val userSessions = activities
            .groupByKey()
            .windowedBy(
                SessionWindows.ofInactivityGapWithNoGrace(Duration.ofMinutes(30))
            )
            .aggregate(
                { UserSession() },
                { _, activity, session ->
                    session.addActivity(activity)
                },
                { _, session1, session2 ->
                    session1.merge(session2)
                },
                Materialized.`as`<String, UserSession, WindowStore<Bytes, ByteArray>>(
                    "user-sessions-store"
                ).withValueSerde(JsonSerde(UserSession::class.java))
            )
        
        // 2. 동네별 활동 통계
        val neighborhoodStats = activities
            .selectKey { _, activity -> activity.neighborhood }
            .groupByKey()
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofHours(24)))
            .aggregate(
                { NeighborhoodStats() },
                { _, activity, stats ->
                    stats.addActivity(activity)
                },
                Materialized.with(Serdes.String(), JsonSerde(NeighborhoodStats::class.java))
            )
        
        // 3. 실시간 사용자 인사이트 생성
        userSessions
            .toStream()
            .map { windowedKey, session ->
                KeyValue(
                    windowedKey.key(),
                    UserInsight(
                        userId = windowedKey.key(),
                        sessionDuration = session.getDuration(),
                        activityCount = session.getActivityCount(),
                        primaryCategory = session.getMostActiveCategory(),
                        engagementScore = session.calculateEngagementScore(),
                        windowStart = windowedKey.window().start(),
                        windowEnd = windowedKey.window().end()
                    )
                )
            }
            .to(
                "karrot.user.insights",
                Produced.with(Serdes.String(), JsonSerde(UserInsight::class.java))
            )
        
        // 4. 동네별 인기 카테고리 추출
        neighborhoodStats
            .toStream()
            .map { windowedKey, stats ->
                KeyValue(
                    windowedKey.key(),
                    NeighborhoodTrends(
                        neighborhood = windowedKey.key(),
                        popularCategories = stats.getTopCategories(10),
                        activeUserCount = stats.getUniqueUserCount(),
                        totalActivities = stats.getTotalActivities(),
                        peakHour = stats.getPeakActivityHour(),
                        timestamp = Instant.now()
                    )
                )
            }
            .to(
                "karrot.neighborhood.trends",
                Produced.with(Serdes.String(), JsonSerde(NeighborhoodTrends::class.java))
            )
        
        return activities
    }
}

data class UserActivity(
    val userId: String,
    val activityType: String,
    val category: String?,
    val neighborhood: String,
    val timestamp: Instant
)

data class UserSession(
    private val activities: MutableList<UserActivity> = mutableListOf(),
    private val categoryCount: MutableMap<String, Int> = mutableMapOf()
) {
    fun addActivity(activity: UserActivity) {
        activities.add(activity)
        activity.category?.let {
            categoryCount.merge(it, 1, Int::plus)
        }
    }
    
    fun merge(other: UserSession): UserSession {
        activities.addAll(other.activities)
        other.categoryCount.forEach { (category, count) ->
            categoryCount.merge(category, count, Int::plus)
        }
        return this
    }
    
    fun getDuration(): Long {
        if (activities.isEmpty()) return 0
        val first = activities.minByOrNull { it.timestamp }!!
        val last = activities.maxByOrNull { it.timestamp }!!
        return Duration.between(first.timestamp, last.timestamp).toMinutes()
    }
    
    fun getActivityCount(): Int = activities.size
    
    fun getMostActiveCategory(): String? {
        return categoryCount.maxByOrNull { it.value }?.key
    }
    
    fun calculateEngagementScore(): Double {
        val duration = getDuration()
        val activityCount = getActivityCount()
        val categoryDiversity = categoryCount.size
        
        return (activityCount * 10.0 + duration * 0.5 + categoryDiversity * 5.0) / 100.0
    }
}
```

## 14. 테스트 구현 상세

### test/domain/event/EventTest.kt
```kotlin
package com.karrot.platform.kafka.domain.event

import com.karrot.platform.kafka.domain.event.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Event 도메인 모델 단위 테스트
 */
class EventTest {
    
    @Test
    fun `이벤트 생성 테스트`() {
        // Given
        val payload = EventPayload(
            data = mapOf("userId" to "user-123", "action" to "login"),
            version = "1.0"
        )
        val metadata = EventMetadata(
            correlationId = "corr-123",
            userId = "user-123",
            source = "AUTH_SERVICE"
        )
        
        // When
        val event = Event.create(
            type = EventType.USER_CREATED,
            aggregateId = "user-123",
            payload = payload,
            metadata = metadata
        )
        
        // Then
        assertNotNull(event.id)
        assertEquals(EventType.USER_CREATED, event.type)
        assertEquals("user-123", event.aggregateId)
        assertEquals(EventStatus.CREATED, event.getStatus())
        assertEquals(payload, event.payload)
        assertEquals(metadata, event.metadata)
    }
    
    @Test
    fun `이벤트 발행 성공 처리`() {
        // Given
        val event = createTestEvent()
        
        // When
        event.markAsPublished()
        
        // Then
        assertEquals(EventStatus.PUBLISHED, event.getStatus())
        assertNotNull(event.getPublishedAt())
    }
    
    @Test
    fun `이미 발행된 이벤트는 실패 처리할 수 없음`() {
        // Given
        val event = createTestEvent()
        event.markAsPublished()
        
        // When & Then
        assertThrows<IllegalArgumentException> {
            event.markAsFailed("Some error")
        }
    }
    
    @Test
    fun `실패한 이벤트는 재시도 가능`() {
        // Given
        val event = createTestEvent()
        event.markAsFailed("Network error")
        
        // When & Then
        assertTrue(event.canRetry())
        assertEquals("Network error", event.getFailureReason())
    }
    
    private fun createTestEvent(): Event {
        return Event.create(
            type = EventType.USER_CREATED,
            aggregateId = "test-123",
            payload = EventPayload(mapOf("test" to "data"))
        )
    }
}
```

### test/infrastructure/kafka/KafkaProducerTest.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka

import com.karrot.platform.kafka.domain.event.model.*
import com.karrot.platform.kafka.infrastructure.kafka.producer.KafkaEventProducer
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Kafka Producer 