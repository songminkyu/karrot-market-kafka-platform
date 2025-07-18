# 당근마켓 Kafka 플랫폼 - 프로젝트 구조 기준 누락 파일

## 1. Domain Layer - event/model/

### domain/event/model/EventId.kt
```kotlin
package com.karrot.platform.kafka.domain.event.model

import java.util.UUID

/**
 * 이벤트 ID Value Object
 */
@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId cannot be blank" }
        require(value.matches(Regex("^[a-zA-Z0-9-_]+$"))) { 
            "EventId must contain only alphanumeric characters, hyphens, and underscores" 
        }
    }
    
    companion object {
        fun generate(): EventId = EventId(UUID.randomUUID().toString())
        
        fun of(value: String): EventId = EventId(value)
    }
    
    override fun toString(): String = value
}
```

### domain/event/model/EventType.kt
```kotlin
package com.karrot.platform.kafka.domain.event.model

/**
 * 이벤트 타입 정의
 */
enum class EventType(val category: String, val description: String) {
    // User Domain Events
    USER_CREATED("USER", "사용자 생성"),
    USER_UPDATED("USER", "사용자 정보 수정"),
    USER_VERIFIED("USER", "사용자 인증 완료"),
    USER_DELETED("USER", "사용자 삭제"),
    USER_LOCATION_UPDATED("USER", "사용자 위치 업데이트"),
    
    // Item Domain Events  
    ITEM_CREATED("ITEM", "상품 등록"),
    ITEM_UPDATED("ITEM", "상품 정보 수정"),
    ITEM_SOLD("ITEM", "상품 판매 완료"),
    ITEM_RESERVED("ITEM", "상품 예약"),
    ITEM_DELETED("ITEM", "상품 삭제"),
    ITEM_PRICE_CHANGED("ITEM", "상품 가격 변경"),
    ITEM_HIDDEN("ITEM", "상품 숨김"),
    
    // Chat Domain Events
    CHAT_MESSAGE_SENT("CHAT", "채팅 메시지 전송"),
    CHAT_MESSAGE_READ("CHAT", "채팅 메시지 읽음"),
    CHAT_ROOM_CREATED("CHAT", "채팅방 생성"),
    CHAT_ROOM_DELETED("CHAT", "채팅방 삭제"),
    CHAT_USER_JOINED("CHAT", "채팅방 참여"),
    CHAT_USER_LEFT("CHAT", "채팅방 나감"),
    
    // Payment Domain Events
    PAYMENT_REQUESTED("PAYMENT", "결제 요청"),
    PAYMENT_COMPLETED("PAYMENT", "결제 완료"),
    PAYMENT_FAILED("PAYMENT", "결제 실패"),
    PAYMENT_REFUNDED("PAYMENT", "결제 환불"),
    PAYMENT_CANCELLED("PAYMENT", "결제 취소"),
    
    // Community Domain Events
    POST_CREATED("COMMUNITY", "게시글 작성"),
    POST_UPDATED("COMMUNITY", "게시글 수정"),
    POST_DELETED("COMMUNITY", "게시글 삭제"),
    POST_LIKED("COMMUNITY", "게시글 좋아요"),
    COMMENT_ADDED("COMMUNITY", "댓글 작성"),
    COMMENT_DELETED("COMMUNITY", "댓글 삭제"),
    NEIGHBOR_VERIFIED("COMMUNITY", "동네 인증 완료");
    
    fun getTopic(): String {
        return "karrot.${category.lowercase()}.events"
    }
}
```

### domain/event/model/EventStatus.kt
```kotlin
package com.karrot.platform.kafka.domain.event.model

/**
 * 이벤트 상태
 */
enum class EventStatus {
    CREATED,      // 생성됨
    PUBLISHED,    // 발행됨
    FAILED,       // 실패
    RETRYING,     // 재시도 중
    ARCHIVED;     // 보관됨
    
    fun canRetry(): Boolean {
        return this == FAILED || this == RETRYING
    }
    
    fun canArchive(): Boolean {
        return this == PUBLISHED || this == FAILED
    }
    
    fun isTerminal(): Boolean {
        return this == PUBLISHED || this == ARCHIVED
    }
}
```

### domain/event/model/EventMetadata.kt
```kotlin
package com.karrot.platform.kafka.domain.event.model

import java.time.Instant

/**
 * 이벤트 메타데이터 Value Object
 */
data class EventMetadata(
    val correlationId: String? = null,
    val userId: String? = null,
    val source: String? = null,
    val deviceInfo: DeviceInfo? = null,
    val headers: Map<String, String> = emptyMap(),
    val traceId: String? = null,
    val spanId: String? = null
) {
    companion object {
        fun empty() = EventMetadata()
        
        fun withUser(userId: String) = EventMetadata(userId = userId)
        
        fun withCorrelation(correlationId: String) = EventMetadata(correlationId = correlationId)
        
        fun withSource(source: String) = EventMetadata(source = source)
    }
    
    fun withHeader(key: String, value: String): EventMetadata {
        return copy(headers = headers + (key to value))
    }
    
    fun withHeaders(newHeaders: Map<String, String>): EventMetadata {
        return copy(headers = headers + newHeaders)
    }
}

data class DeviceInfo(
    val deviceType: String,
    val osVersion: String,
    val appVersion: String
)
```

## 2. Domain Layer - schema/model/

### domain/schema/model/SchemaVersion.kt
```kotlin
package com.karrot.platform.kafka.domain.schema.model

/**
 * 스키마 버전 Value Object
 */
@JvmInline
value class SchemaVersion(val value: Int) {
    init {
        require(value > 0) { "Schema version must be positive" }
        require(value <= 999999) { "Schema version must not exceed 999999" }
    }
    
    companion object {
        val INITIAL = SchemaVersion(1)
        
        fun of(value: Int) = SchemaVersion(value)
    }
    
    fun next(): SchemaVersion = SchemaVersion(value + 1)
    
    fun previous(): SchemaVersion? = if (value > 1) SchemaVersion(value - 1) else null
    
    fun isInitial(): Boolean = value == 1
    
    override fun toString(): String = "v$value"
}
```

## 3. Infrastructure Layer - kafka/consumer/

### infrastructure/kafka/consumer/KafkaEventConsumer.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.consumer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 기본 Kafka 이벤트 컨슈머
 */
@Component
abstract class KafkaEventConsumer<T> {
    
    private val logger = KotlinLogging.logger {}
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    protected abstract suspend fun processMessage(message: T, headers: Map<String, String>)
    
    protected abstract fun getTopicName(): String
    
    protected abstract fun getGroupId(): String
    
    protected open fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            is IllegalArgumentException -> false
            is IllegalStateException -> false
            is NullPointerException -> false
            else -> true
        }
    }
    
    protected fun handleMessage(
        record: ConsumerRecord<String, T>,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        
        coroutineScope.launch {
            try {
                logger.debug { 
                    "Processing message from topic: ${record.topic()}, " +
                    "partition: ${record.partition()}, offset: ${record.offset()}" 
                }
                
                val headers = record.headers().associate { 
                    it.key() to String(it.value())
                }
                
                processMessage(record.value(), headers)
                
                acknowledgment.acknowledge()
                
                val duration = System.currentTimeMillis() - startTime
                logger.info { 
                    "Successfully processed message in ${duration}ms - " +
                    "Topic: ${record.topic()}, Offset: ${record.offset()}" 
                }
                
            } catch (e: Exception) {
                logger.error(e) { 
                    "Error processing message - Topic: ${record.topic()}, " +
                    "Offset: ${record.offset()}" 
                }
                
                if (!shouldRetry(e)) {
                    // 재시도하지 않을 에러는 DLQ로 전송하고 커밋
                    sendToDlq(record, e)
                    acknowledgment.acknowledge()
                } else {
                    // 재시도할 에러는 커밋하지 않음
                    throw e
                }
            }
        }
    }
    
    protected abstract fun sendToDlq(record: ConsumerRecord<String, T>, exception: Exception)
}
```

## 4. Application Layer - port/input/

### application/port/input/EventInputPort.kt (완전한 구현)
```kotlin
package com.karrot.platform.kafka.application.port.input

import com.karrot.platform.kafka.application.usecase.event.dto.PublishEventCommand
import com.karrot.platform.kafka.application.usecase.event.dto.EventResponse
import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventType

interface EventInputPort {
    suspend fun publishEvent(command: PublishEventCommand): EventResponse
    suspend fun retryEvent(eventId: String): EventResponse
    suspend fun getEvent(eventId: String): Event?
    suspend fun getEventsByAggregateId(aggregateId: String, limit: Int): List<Event>
    suspend fun getEventsByType(eventType: EventType, limit: Int): List<Event>
    suspend fun archiveEvent(eventId: String): Boolean
    suspend fun getEventStatus(eventId: String): String?
}
```

### application/port/input/MonitoringInputPort.kt (완전한 구현)
```kotlin
package com.karrot.platform.kafka.application.port.input

import com.karrot.platform.kafka.domain.monitoring.model.ConsumerLag
import com.karrot.platform.kafka.domain.monitoring.model.TopicMetrics

interface MonitoringInputPort {
    suspend fun getConsumerLag(groupId: String): ConsumerLagInfo
    suspend fun getAllConsumerLags(): List<ConsumerLagInfo>
    suspend fun getTopicMetrics(topicName: String): TopicMetrics
    suspend fun getAllTopicMetrics(): List<TopicMetrics>
    suspend fun getClusterHealth(): ClusterHealth
    suspend fun getBrokerStatus(): List<BrokerStatus>
}

data class ConsumerLagInfo(
    val groupId: String,
    val totalLag: Long,
    val topics: List<TopicLag>
)

data class TopicLag(
    val topic: String,
    val totalLag: Long,
    val partitions: List<PartitionLag>
)

data class PartitionLag(
    val partition: Int,
    val currentOffset: Long,
    val endOffset: Long,
    val lag: Long
)

data class ClusterHealth(
    val status: HealthStatus,
    val brokerCount: Int,
    val topicCount: Int,
    val partitionCount: Int,
    val underReplicatedPartitions: Int,
    val offlinePartitions: Int
)

enum class HealthStatus {
    UP, DOWN, DEGRADED
}

data class BrokerStatus(
    val brokerId: Int,
    val host: String,
    val port: Int,
    val status: String
)
```

## 5. Application Layer - port/output/

### application/port/output/EventOutputPort.kt (완전한 구현)
```kotlin
package com.karrot.platform.kafka.application.port.output

import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventId
import com.karrot.platform.kafka.domain.event.model.EventStatus
import com.karrot.platform.kafka.domain.event.model.EventType
import com.karrot.platform.kafka.domain.event.service.PublishResult
import java.time.Instant

interface EventOutputPort {
    suspend fun saveEvent(event: Event): Event
    suspend fun updateEventStatus(event: Event): Event
    suspend fun publishToKafka(event: Event): PublishResult
    suspend fun findEventById(id: EventId): Event?
    suspend fun findFailedEvents(): List<Event>
    suspend fun findFailedEventsOlderThan(timestamp: Instant): List<Event>
    suspend fun findEventsByStatus(status: EventStatus): List<Event>
    suspend fun findEventsByAggregateId(aggregateId: String, limit: Int): List<Event>
    suspend fun findEventsByType(eventType: EventType, limit: Int): List<Event>
    suspend fun archiveEvent(event: Event): Event
    suspend fun deleteArchivedEventsOlderThan(timestamp: Instant): Int
}
```

### application/port/output/SchemaRegistryOutputPort.kt (완전한 구현)
```kotlin
package com.karrot.platform.kafka.application.port.output

import com.karrot.platform.kafka.domain.schema.model.Schema
import com.karrot.platform.kafka.domain.schema.model.SchemaCompatibility
import com.karrot.platform.kafka.domain.schema.model.SchemaVersion

interface SchemaRegistryOutputPort {
    suspend fun registerSchema(subject: String, schema: String): Schema
    suspend fun getLatestSchema(subject: String): Schema?
    suspend fun getSchema(subject: String, version: Int): Schema?
    suspend fun getSchemaById(id: Long): Schema?
    suspend fun getAllVersions(subject: String): List<Int>
    suspend fun checkCompatibility(
        subject: String,
        newSchema: String,
        version: Int
    ): SchemaCompatibility
    suspend fun updateCompatibility(
        subject: String,
        compatibility: SchemaCompatibility
    ): Boolean
    suspend fun deleteSchema(subject: String, version: Int)
    suspend fun deleteSubject(subject: String): List<Int>
}
```

### application/port/output/MetricsOutputPort.kt (완전한 구현)
```kotlin
package com.karrot.platform.kafka.application.port.output

import java.time.Instant

interface MetricsOutputPort {
    fun recordEventPublished(topic: String, eventType: String, success: Boolean, duration: Long)
    fun recordEventConsumed(topic: String, eventType: String, success: Boolean, duration: Long)
    fun recordConsumerLag(groupId: String, topic: String, partition: Int, lag: Long)
    fun recordErrorRate(operation: String, errorType: String)
    fun recordThroughput(topic: String, messagesPerSecond: Double)
    fun recordEventSize(topic: String, sizeInBytes: Long)
    fun recordProcessingTime(operation: String, duration: Long)
    fun incrementCounter(name: String, tags: Map<String, String> = emptyMap())
    fun gauge(name: String, value: Double, tags: Map<String, String> = emptyMap())
    fun histogram(name: String, value: Double, tags: Map<String, String> = emptyMap())
}
```

## 6. Infrastructure Layer - 추가 구현

### infrastructure/kafka/config/KafkaTransactionConfig.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.transaction.KafkaTransactionManager
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaTransactionConfig {
    
    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Bean
    fun transactionalProducerFactory(): ProducerFactory<String, Any> {
        val configs = mutableMapOf<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        configs[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configs[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = "karrot-tx-"
        configs[ProducerConfig.ACKS_CONFIG] = "all"
        configs[ProducerConfig.RETRIES_CONFIG] = 3
        configs[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 1
        
        return DefaultKafkaProducerFactory(configs)
    }
    
    @Bean
    fun transactionalKafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(transactionalProducerFactory())
    }
    
    @Bean
    fun kafkaTransactionManager(
        transactionalProducerFactory: ProducerFactory<String, Any>
    ): KafkaTransactionManager {
        return KafkaTransactionManager(transactionalProducerFactory)
    }
}
```

## 7. Common Layer - 추가 구현

### common/exception/EventValidationException.kt
```kotlin
package com.karrot.platform.kafka.common.exception

/**
 * 이벤트 검증 예외
 */
class EventValidationException(
    message: String,
    cause: Throwable? = null
) : BusinessException(ErrorCode.EVENT_VALIDATION_FAILED, message, cause)
```

### common/config/KafkaProperties.kt
```kotlin
package com.karrot.platform.kafka.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "kafka")
data class KafkaProperties @ConstructorBinding constructor(
    val bootstrapServers: String,
    val schemaRegistry: SchemaRegistryProperties,
    val producer: ProducerProperties,
    val consumer: ConsumerProperties,
    val streams: StreamsProperties,
    val topics: TopicsProperties
)

data class SchemaRegistryProperties(
    val url: String,
    val cacheSize: Int = 1000
)

data class ProducerProperties(
    val acks: String = "all",
    val retries: Int = 3,
    val batchSize: Int = 16384,
    val lingerMs: Int = 10,
    val compressionType: String = "lz4",
    val bufferMemory: Long = 33554432L,
    val enableIdempotence: Boolean = true
)

data class ConsumerProperties(
    val groupId: String,
    val autoOffsetReset: String = "earliest",
    val maxPollRecords: Int = 500,
    val enableAutoCommit: Boolean = false,
    val sessionTimeoutMs: Int = 30000,
    val heartbeatIntervalMs: Int = 10000
)

data class StreamsProperties(
    val applicationId: String,
    val stateDir: String = "/tmp/kafka-streams",
    val cacheMaxBytesBuffering: Long = 10485760L,
    val commitIntervalMs: Int = 1000
)

data class TopicsProperties(
    val userEvents: String = "karrot.user.events",
    val itemEvents: String = "karrot.item.events",
    val paymentEvents: String = "karrot.payment.events",
    val chatEvents: String = "karrot.chat.events",
    val dlqPrefix: String = "dlq."
)
```

## 8. Interface Layer - REST DTOs

### interfaces/rest/dto/EventResponse.kt (인터페이스 레이어용)
```kotlin
package com.karrot.platform.kafka.interfaces.rest.dto

import java.time.Instant

data class EventResponse(
    val eventId: String,
    val status: String,
    val publishedAt: Instant? = null,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null,
    val message: String? = null
)
```

## 9. Test 기본 구조

### test/domain/DomainTestBase.kt
```kotlin
package com.karrot.platform.kafka.domain

import com.karrot.platform.kafka.domain.event.model.*
import org.junit.jupiter.api.BeforeEach
import java.time.Instant

abstract class DomainTestBase {
    
    @BeforeEach
    open fun setUp() {
        // 공통 설정
    }
    
    protected fun createTestEvent(
        type: EventType = EventType.USER_CREATED,
        aggregateId: String = "test-aggregate-123"
    ): Event {
        return Event.create(
            type = type,
            aggregateId = aggregateId,
            payload = EventPayload(
                data = mapOf(
                    "testKey" to "testValue",
                    "timestamp" to Instant.now().toString()
                ),
                version = "1.0"
            ),
            metadata = EventMetadata(
                userId = "test-user",
                source = "TEST"
            )
        )
    }
}
```

### test/application/ApplicationTestBase.kt
```kotlin
package com.karrot.platform.kafka.application

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

abstract class ApplicationTestBase {
    
    @BeforeEach
    open fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }
    
    @AfterEach
    open fun tearDown() {
        clearAllMocks()
    }
}
```

### test/infrastructure/InfrastructureTestBase.kt
```kotlin
package com.karrot.platform.kafka.infrastructure

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = [
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    ],
    topics = ["test-topic"]
)
abstract class InfrastructureTestBase {
    
    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("test_db")
            withUsername("test")
            withPassword("test")
        }
        
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
```

### test/interfaces/InterfaceTestBase.kt
```kotlin
package com.karrot.platform.kafka.interfaces

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class InterfaceTestBase {
    
    @Autowired
    protected lateinit var mockMvc: MockMvc
    
    @Autowired
    protected lateinit var objectMapper: ObjectMapper
    
    protected fun asJsonString(obj: Any): String {
        return objectMapper.writeValueAsString(obj)
    }
}
```

### test/integration/IntegrationTestBase.kt
```kotlin
package com.karrot.platform.kafka.integration

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka
abstract class IntegrationTestBase {
    // 통합 테스트 공통 설정
}
```

이제 프로젝트 구조에 명시된 모든 파일들이 구현되었습니다!