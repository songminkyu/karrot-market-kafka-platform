# 당근마켓 Kafka 플랫폼 - 추가 필수 파일들

## 14. Infrastructure Layer - 누락된 구현체들

### infrastructure/persistence/entity/EventEntity.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.entity

import com.karrot.platform.kafka.domain.event.model.EventStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "event_store")
data class EventEntity(
    @Id
    @Column(name = "event_id")
    val eventId: String,
    
    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: String,
    
    @Column(name = "event_type", nullable = false)
    val eventType: String,
    
    @Column(name = "event_data", columnDefinition = "TEXT", nullable = false)
    val eventData: String,
    
    @Column(name = "event_time", nullable = false)
    val eventTime: Instant,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: EventStatus = EventStatus.CREATED,
    
    @Column(name = "published_at")
    val publishedAt: Instant? = null,
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    val failureReason: String? = null,
    
    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
```

### infrastructure/persistence/entity/SchemaEntity.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "schema_registry",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["subject", "version"])
    ]
)
data class SchemaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schema_id")
    val schemaId: Long? = null,
    
    @Column(name = "subject", nullable = false)
    val subject: String,
    
    @Column(name = "version", nullable = false)
    val version: Int,
    
    @Column(name = "schema_content", columnDefinition = "TEXT", nullable = false)
    val schemaContent: String,
    
    @Column(name = "schema_type", nullable = false)
    val schemaType: String = "AVRO",
    
    @Column(name = "compatibility_mode", nullable = false)
    val compatibilityMode: String = "BACKWARD",
    
    @Column(name = "is_deleted", nullable = false)
    val isDeleted: Boolean = false,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
```

### infrastructure/persistence/repository/EventJpaRepository.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.repository

import com.karrot.platform.kafka.domain.event.model.EventStatus
import com.karrot.platform.kafka.infrastructure.persistence.entity.EventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface EventJpaRepository : JpaRepository<EventEntity, String> {
    
    fun findByStatus(status: EventStatus): List<EventEntity>
    
    fun findByAggregateId(aggregateId: String): List<EventEntity>
    
    fun findByEventType(eventType: String): List<EventEntity>
    
    fun findByStatusAndCreatedAtBefore(
        status: EventStatus,
        createdAt: Instant
    ): List<EventEntity>
    
    @Query("""
        SELECT e FROM EventEntity e 
        WHERE e.status = :status 
        AND e.createdAt < :timestamp 
        ORDER BY e.createdAt ASC
        LIMIT :limit
    """)
    fun findFailedEventsForRetry(
        @Param("status") status: EventStatus,
        @Param("timestamp") timestamp: Instant,
        @Param("limit") limit: Int
    ): List<EventEntity>
    
    @Modifying
    @Query("""
        DELETE FROM EventEntity e 
        WHERE e.status = :status 
        AND e.createdAt < :timestamp
    """)
    fun deleteByStatusAndCreatedAtBefore(
        @Param("status") status: EventStatus,
        @Param("timestamp") timestamp: Instant
    ): Int
}
```

### infrastructure/persistence/repository/SchemaJpaRepository.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.repository

import com.karrot.platform.kafka.infrastructure.persistence.entity.SchemaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SchemaJpaRepository : JpaRepository<SchemaEntity, Long> {
    
    fun findBySubjectAndVersion(subject: String, version: Int): SchemaEntity?
    
    fun findBySubjectOrderByVersionDesc(subject: String): List<SchemaEntity>
    
    @Query("""
        SELECT s FROM SchemaEntity s 
        WHERE s.subject = :subject 
        AND s.isDeleted = false 
        ORDER BY s.version DESC 
        LIMIT 1
    """)
    fun findLatestBySubject(@Param("subject") subject: String): SchemaEntity?
    
    fun existsBySubjectAndVersion(subject: String, version: Int): Boolean
}
```

### infrastructure/persistence/config/JpaConfig.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableJpaRepositories(
    basePackages = ["com.karrot.platform.kafka.infrastructure.persistence.repository"]
)
@EnableJpaAuditing
@EnableTransactionManagement
class JpaConfig
```

### infrastructure/persistence/adapter/EventMapper.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.adapter

import com.karrot.platform.kafka.common.util.JsonUtils
import com.karrot.platform.kafka.domain.event.model.*
import com.karrot.platform.kafka.infrastructure.persistence.entity.EventEntity
import org.springframework.stereotype.Component

@Component
class EventMapper {
    
    fun toEntity(event: Event): EventEntity {
        return EventEntity(
            eventId = event.id.value,
            aggregateId = event.aggregateId,
            eventType = event.type.name,
            eventData = JsonUtils.toJson(event.payload.data),
            eventTime = event.createdAt,
            status = event.getStatus(),
            publishedAt = event.getPublishedAt(),
            failureReason = event.getFailureReason()
        )
    }
    
    fun toDomain(entity: EventEntity): Event {
        val payload = EventPayload(
            data = JsonUtils.fromJson<Map<String, Any>>(entity.eventData),
            version = "1.0" // 실제로는 eventData에서 추출
        )
        
        // 메타데이터도 eventData에서 추출 (실제 구현에서는)
        val metadata = EventMetadata.empty()
        
        return Event.create(
            type = EventType.valueOf(entity.eventType),
            aggregateId = entity.aggregateId,
            payload = payload,
            metadata = metadata
        ).apply {
            // Private 필드 설정을 위한 리플렉션 사용 (실제로는 다른 방법 고려)
            when (entity.status) {
                EventStatus.PUBLISHED -> markAsPublished()
                EventStatus.FAILED -> markAsFailed(entity.failureReason ?: "Unknown")
                else -> { /* CREATED 상태 유지 */ }
            }
        }
    }
}
```

## 15. Application Layer - Port 구현

### application/port/input/EventInputPort.kt
```kotlin
package com.karrot.platform.kafka.application.port.input

import com.karrot.platform.kafka.application.usecase.event.dto.PublishEventCommand
import com.karrot.platform.kafka.application.usecase.event.dto.EventResponse

interface EventInputPort {
    suspend fun publishEvent(command: PublishEventCommand): EventResponse
    suspend fun retryEvent(eventId: String): EventResponse
    suspend fun getEvent(eventId: String): Event?
    suspend fun getEventsByAggregateId(aggregateId: String, limit: Int): List<Event>
}
```

### application/port/input/MonitoringInputPort.kt
```kotlin
package com.karrot.platform.kafka.application.port.input

import com.karrot.platform.kafka.domain.monitoring.model.ConsumerLag
import com.karrot.platform.kafka.domain.monitoring.model.TopicMetrics

interface MonitoringInputPort {
    suspend fun getConsumerLag(groupId: String): ConsumerLagInfo
    suspend fun getAllConsumerLags(): List<ConsumerLagInfo>
    suspend fun getTopicMetrics(topicName: String): TopicMetrics
    suspend fun getClusterHealth(): ClusterHealth
}
```

### application/port/output/SchemaRegistryOutputPort.kt
```kotlin
package com.karrot.platform.kafka.application.port.output

import com.karrot.platform.kafka.domain.schema.model.Schema
import com.karrot.platform.kafka.domain.schema.model.SchemaCompatibility

interface SchemaRegistryOutputPort {
    suspend fun registerSchema(subject: String, schema: String): Schema
    suspend fun getLatestSchema(subject: String): Schema?
    suspend fun getSchema(subject: String, version: Int): Schema?
    suspend fun checkCompatibility(
        subject: String,
        newSchema: String,
        version: Int
    ): SchemaCompatibility
    suspend fun deleteSchema(subject: String, version: Int)
}
```

### application/port/output/MetricsOutputPort.kt
```kotlin
package com.karrot.platform.kafka.application.port.output

interface MetricsOutputPort {
    fun recordEventPublished(topic: String, eventType: String, success: Boolean, duration: Long)
    fun recordEventConsumed(topic: String, eventType: String, success: Boolean, duration: Long)
    fun recordConsumerLag(groupId: String, topic: String, partition: Int, lag: Long)
    fun recordErrorRate(operation: String, errorType: String)
}
```

## 16. Infrastructure Layer - 추가 구현

### infrastructure/kafka/producer/TransactionalProducer.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.producer

import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionalProducer(
    private val transactionalKafkaTemplate: KafkaTemplate<String, Any>
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Transactional("kafkaTransactionManager")
    suspend fun sendInTransaction(
        operations: List<ProducerOperation>
    ): List<TransactionResult> {
        val results = mutableListOf<TransactionResult>()
        
        try {
            operations.forEach { operation ->
                val future = transactionalKafkaTemplate.send(
                    operation.topic,
                    operation.key,
                    operation.value
                )
                
                results.add(
                    TransactionResult(
                        operationId = operation.id,
                        success = true,
                        future = future
                    )
                )
            }
            
            logger.info { "Transaction completed with ${operations.size} operations" }
            
        } catch (e: Exception) {
            logger.error(e) { "Transaction failed" }
            throw TransactionFailedException("Kafka transaction failed", e)
        }
        
        return results
    }
}

data class ProducerOperation(
    val id: String,
    val topic: String,
    val key: String,
    val value: Any
)

data class TransactionResult(
    val operationId: String,
    val success: Boolean,
    val future: ListenableFuture<SendResult<String, Any>>? = null,
    val error: String? = null
)

class TransactionFailedException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

### infrastructure/kafka/producer/BatchProducer.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.producer

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class BatchProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun sendBatch(
        topic: String,
        messages: List<Pair<String, Any>>,
        parallelism: Int = 10
    ): BatchResult = coroutineScope {
        
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<Deferred<SendResult>>()
        
        // 병렬 처리를 위한 세마포어
        val semaphore = kotlinx.coroutines.sync.Semaphore(parallelism)
        
        messages.forEach { (key, value) ->
            val deferred = async {
                semaphore.withPermit {
                    try {
                        val future = kafkaTemplate.send(topic, key, value)
                        SendResult(
                            key = key,
                            success = true,
                            offset = future.get().recordMetadata.offset(),
                            partition = future.get().recordMetadata.partition()
                        )
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to send message with key: $key" }
                        SendResult(
                            key = key,
                            success = false,
                            error = e.message
                        )
                    }
                }
            }
            results.add(deferred)
        }
        
        val sendResults = results.awaitAll()
        val duration = System.currentTimeMillis() - startTime
        
        val successCount = sendResults.count { it.success }
        val failureCount = sendResults.count { !it.success }
        
        logger.info { 
            "Batch completed - Total: ${messages.size}, " +
            "Success: $successCount, Failure: $failureCount, " +
            "Duration: ${duration}ms"
        }
        
        BatchResult(
            totalCount = messages.size,
            successCount = successCount,
            failureCount = failureCount,
            duration = duration,
            results = sendResults
        )
    }
}

data class BatchResult(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val duration: Long,
    val results: List<SendResult>
)

data class SendResult(
    val key: String,
    val success: Boolean,
    val offset: Long? = null,
    val partition: Int? = null,
    val error: String? = null
)
```

### infrastructure/kafka/consumer/RetryableConsumer.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.consumer

import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.kafka.support.Acknowledgment
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
abstract class RetryableConsumer<T> {
    
    private val logger = KotlinLogging.logger {}
    
    @Retryable(
        value = [RetryableException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    suspend fun processWithRetry(
        message: T,
        processor: suspend (T) -> Unit
    ) {
        try {
            processor(message)
        } catch (e: Exception) {
            when {
                isRetryable(e) -> {
                    logger.warn { "Retryable error occurred: ${e.message}" }
                    throw RetryableException(e.message ?: "Processing failed", e)
                }
                else -> {
                    logger.error(e) { "Non-retryable error occurred" }
                    throw e
                }
            }
        }
    }
    
    protected open fun isRetryable(exception: Exception): Boolean {
        return when (exception) {
            is IllegalArgumentException -> false
            is IllegalStateException -> false
            is ValidationException -> false
            else -> true
        }
    }
}

class RetryableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ValidationException(
    message: String
) : RuntimeException(message)
```

### infrastructure/kafka/streams/AggregationProcessor.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.streams

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class AggregationProcessor {
    
    @Bean
    fun buildAggregationPipeline(builder: StreamsBuilder): KStream<String, AggregationEvent> {
        
        // 이벤트 스트림
        val events = builder.stream<String, AggregationEvent>(
            "karrot.aggregation.events",
            Consumed.with(Serdes.String(), JsonSerde(AggregationEvent::class.java))
        )
        
        // 1. 카테고리별 집계
        val categoryAggregation = events
            .groupBy { _, event -> event.category }
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofHours(1)))
            .aggregate(
                { CategoryStats() },
                { _, event, stats -> stats.add(event) },
                Materialized.with(Serdes.String(), JsonSerde(CategoryStats::class.java))
            )
        
        // 2. 사용자별 집계
        val userAggregation = events
            .groupBy { _, event -> event.userId }
            .aggregate(
                { UserStats() },
                { _, event, stats -> stats.add(event) },
                Materialized.with(Serdes.String(), JsonSerde(UserStats::class.java))
            )
        
        // 3. 전체 통계
        val globalStats = events
            .groupByKey()
            .aggregate(
                { GlobalStats() },
                { _, event, stats -> stats.add(event) },
                Materialized.`as`("global-stats-store")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde(GlobalStats::class.java))
            )
        
        // 결과 출력
        categoryAggregation
            .toStream()
            .map { windowedKey, stats ->
                KeyValue(
                    windowedKey.key(),
                    CategoryAggregationResult(
                        category = windowedKey.key(),
                        stats = stats,
                        windowStart = windowedKey.window().start(),
                        windowEnd = windowedKey.window().end()
                    )
                )
            }
            .to("karrot.category.aggregations")
        
        return events
    }
}

data class AggregationEvent(
    val eventId: String,
    val userId: String,
    val category: String,
    val value: Double,
    val timestamp: Instant
)

data class CategoryStats(
    var count: Long = 0,
    var sum: Double = 0.0,
    var min: Double = Double.MAX_VALUE,
    var max: Double = Double.MIN_VALUE
) {
    fun add(event: AggregationEvent): CategoryStats {
        count++
        sum += event.value
        min = minOf(min, event.value)
        max = maxOf(max, event.value)
        return this
    }
    
    fun average(): Double = if (count > 0) sum / count else 0.0
}
```

### infrastructure/kafka/admin/KafkaAdminService.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.admin

import mu.KotlinLogging
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.config.ConfigResource
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class KafkaAdminService(
    private val adminClient: AdminClient
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun createTopic(
        topicName: String,
        partitions: Int,
        replicationFactor: Short,
        configs: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            val newTopic = NewTopic(topicName, partitions, replicationFactor)
                .configs(configs)
            
            adminClient.createTopics(listOf(newTopic))
                .all()
                .get(30, TimeUnit.SECONDS)
            
            logger.info { "Topic created: $topicName" }
            true
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to create topic: $topicName" }
            false
        }
    }
    
    suspend fun deleteTopic(topicName: String): Boolean {
        return try {
            adminClient.deleteTopics(listOf(topicName))
                .all()
                .get(30, TimeUnit.SECONDS)
            
            logger.info { "Topic deleted: $topicName" }
            true
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete topic: $topicName" }
            false
        }
    }
    
    suspend fun listTopics(): Set<String> {
        return try {
            adminClient.listTopics()
                .names()
                .get(30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.error(e) { "Failed to list topics" }
            emptySet()
        }
    }
    
    suspend fun describeCluster(): ClusterDescription {
        return try {
            val description = adminClient.describeCluster()
            
            ClusterDescription(
                clusterId = description.clusterId().get(),
                brokers = description.nodes().get().map { node ->
                    BrokerInfo(
                        id = node.id(),
                        host = node.host(),
                        port = node.port(),
                        rack = node.rack()
                    )
                },
                controller = description.controller().get().id()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to describe cluster" }
            throw ClusterDescriptionException("Failed to describe cluster", e)
        }
    }
    
    suspend fun getTopicConfigs(topicName: String): Map<String, String> {
        return try {
            val resource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
            val configs = adminClient.describeConfigs(listOf(resource))
                .all()
                .get(30, TimeUnit.SECONDS)
            
            configs[resource]?.entries()
                ?.associate { it.name() to it.value() }
                ?: emptyMap()
                
        } catch (e: Exception) {
            logger.error(e) { "Failed to get topic configs: $topicName" }
            emptyMap()
        }
    }
}

data class ClusterDescription(
    val clusterId: String,
    val brokers: List<BrokerInfo>,
    val controller: Int
)

data class BrokerInfo(
    val id: Int,
    val host: String,
    val port: Int,
    val rack: String?
)

class ClusterDescriptionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

### infrastructure/kafka/admin/TopicManager.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.admin

import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewPartitions
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TopicManager(
    private val adminClient: AdminClient,
    private val kafkaAdminService: KafkaAdminService
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        private val MANAGED_TOPICS = mapOf(
            "karrot.user.events" to TopicConfig(partitions = 10, replicationFactor = 3),
            "karrot.item.events" to TopicConfig(partitions = 20, replicationFactor = 3),
            "karrot.payment.events" to TopicConfig(partitions = 10, replicationFactor = 3),
            "karrot.chat.events" to TopicConfig(partitions = 30, replicationFactor = 3)
        )
    }
    
    @Scheduled(fixedDelay = 300000) // 5분마다
    suspend fun ensureTopicsExist() {
        val existingTopics = kafkaAdminService.listTopics()
        
        MANAGED_TOPICS.forEach { (topicName, config) ->
            if (!existingTopics.contains(topicName)) {
                logger.info { "Creating missing topic: $topicName" }
                
                kafkaAdminService.createTopic(
                    topicName = topicName,
                    partitions = config.partitions,
                    replicationFactor = config.replicationFactor,
                    configs = config.configs
                )
            }
        }
    }
    
    suspend fun increasePartitions(topicName: String, newPartitionCount: Int): Boolean {
        return try {
            val currentPartitions = adminClient.describeTopics(listOf(topicName))
                .values()[topicName]
                ?.get()
                ?.partitions()
                ?.size ?: 0
            
            if (newPartitionCount <= currentPartitions) {
                logger.warn { 
                    "Cannot decrease partitions. Current: $currentPartitions, Requested: $newPartitionCount" 
                }
                return false
            }
            
            adminClient.createPartitions(
                mapOf(topicName to NewPartitions.increaseTo(newPartitionCount))
            ).all().get()
            
            logger.info { "Increased partitions for $topicName to $newPartitionCount" }
            true
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to increase partitions for $topicName" }
            false
        }
    }
}

data class TopicConfig(
    val partitions: Int,
    val replicationFactor: Short,
    val configs: Map<String, String> = emptyMap()
)
```

## 17. Test 구현

### test/domain/DomainTestBase.kt
```kotlin
package com.karrot.platform.kafka.domain

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
abstract class DomainTestBase {
    
    @BeforeEach
    fun setUp() {
        // 공통 테스트 설정
    }
    
    protected fun createTestEvent() = Event.create(
        type = EventType.USER_CREATED,
        aggregateId = "test-aggregate-123",
        payload = EventPayload(
            data = mapOf(
                "userId" to "user-123",
                "email" to "test@karrot.com"
            )
        )
    )
}
```

### test/application/ApplicationTestBase.kt
```kotlin
package com.karrot.platform.kafka.application

import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import org.junit.jupiter.api.BeforeEach

abstract class ApplicationTestBase {
    
    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        clearMocks()
    }
}
```

### test/infrastructure/InfrastructureTestBase.kt
```kotlin
package com.karrot.platform.kafka.infrastructure

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
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
    ]
)
abstract class InfrastructureTestBase {
    
    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("test_db")
            withUsername("test")
            withPassword("test")
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
}