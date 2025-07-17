# 당근마켓 Kafka 플랫폼 - Clean Architecture & DDD 프로젝트 구조

## 프로젝트 구조 Overview

```
karrot-market-kafka-platform/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── docker-compose.yml
├── README.md
├── .gitignore
│
├── buildSrc/
│   └── src/main/kotlin/
│       ├── Dependencies.kt
│       └── Versions.kt
│
└── src/
    ├── main/
    │   ├── kotlin/
    │   │   └── com/karrot/platform/kafka/
    │   │       ├── KarrotKafkaPlatformApplication.kt
    │   │       │
    │   │       ├── domain/                    # 도메인 계층 (핵심 비즈니스 로직)
    │   │       │   ├── event/
    │   │       │   │   ├── model/
    │   │       │   │   │   ├── Event.kt
    │   │       │   │   │   ├── EventId.kt
    │   │       │   │   │   ├── EventType.kt
    │   │       │   │   │   ├── EventStatus.kt
    │   │       │   │   │   └── EventMetadata.kt
    │   │       │   │   ├── repository/
    │   │       │   │   │   └── EventRepository.kt
    │   │       │   │   ├── service/
    │   │       │   │   │   ├── EventPublisher.kt
    │   │       │   │   │   └── EventValidator.kt
    │   │       │   │   └── exception/
    │   │       │   │       └── EventDomainException.kt
    │   │       │   │
    │   │       │   ├── schema/
    │   │       │   │   ├── model/
    │   │       │   │   │   ├── Schema.kt
    │   │       │   │   │   ├── SchemaVersion.kt
    │   │       │   │   │   └── SchemaCompatibility.kt
    │   │       │   │   ├── repository/
    │   │       │   │   │   └── SchemaRepository.kt
    │   │       │   │   └── service/
    │   │       │   │       └── SchemaEvolutionService.kt
    │   │       │   │
    │   │       │   └── monitoring/
    │   │       │       ├── model/
    │   │       │       │   ├── ConsumerLag.kt
    │   │       │       │   └── TopicMetrics.kt
    │   │       │       └── service/
    │   │       │           └── MonitoringService.kt
    │   │       │
    │   │       ├── application/               # 애플리케이션 계층 (유스케이스)
    │   │       │   ├── usecase/
    │   │       │   │   ├── event/
    │   │       │   │   │   ├── PublishEventUseCase.kt
    │   │       │   │   │   ├── ConsumeEventUseCase.kt
    │   │       │   │   │   ├── ReprocessEventUseCase.kt
    │   │       │   │   │   └── dto/
    │   │       │   │   │       ├── PublishEventCommand.kt
    │   │       │   │   │       └── EventResponse.kt
    │   │       │   │   │
    │   │       │   │   ├── schema/
    │   │       │   │   │   ├── RegisterSchemaUseCase.kt
    │   │       │   │   │   ├── ValidateSchemaUseCase.kt
    │   │       │   │   │   └── dto/
    │   │       │   │   │       └── SchemaRegistrationCommand.kt
    │   │       │   │   │
    │   │       │   │   └── monitoring/
    │   │       │   │       ├── GetConsumerLagUseCase.kt
    │   │       │   │       └── GetTopicMetricsUseCase.kt
    │   │       │   │
    │   │       │   └── port/                  # 포트 인터페이스
    │   │       │       ├── input/
    │   │       │       │   ├── EventInputPort.kt
    │   │       │       │   └── MonitoringInputPort.kt
    │   │       │       └── output/
    │   │       │           ├── EventOutputPort.kt
    │   │       │           ├── SchemaRegistryOutputPort.kt
    │   │       │           └── MetricsOutputPort.kt
    │   │       │
    │   │       ├── infrastructure/            # 인프라 계층 (외부 시스템 연동)
    │   │       │   ├── kafka/
    │   │       │   │   ├── config/
    │   │       │   │   │   ├── KafkaConfig.kt
    │   │       │   │   │   ├── KafkaProducerConfig.kt
    │   │       │   │   │   ├── KafkaConsumerConfig.kt
    │   │       │   │   │   ├── KafkaStreamsConfig.kt
    │   │       │   │   │   └── SchemaRegistryConfig.kt
    │   │       │   │   │
    │   │       │   │   ├── producer/
    │   │       │   │   │   ├── KafkaEventProducer.kt
    │   │       │   │   │   ├── TransactionalProducer.kt
    │   │       │   │   │   └── BatchProducer.kt
    │   │       │   │   │
    │   │       │   │   ├── consumer/
    │   │       │   │   │   ├── KafkaEventConsumer.kt
    │   │       │   │   │   ├── ErrorHandlingConsumer.kt
    │   │       │   │   │   └── RetryableConsumer.kt
    │   │       │   │   │
    │   │       │   │   ├── streams/
    │   │       │   │   │   ├── EventStreamProcessor.kt
    │   │       │   │   │   └── AggregationProcessor.kt
    │   │       │   │   │
    │   │       │   │   └── admin/
    │   │       │   │       ├── KafkaAdminService.kt
    │   │       │   │       └── TopicManager.kt
    │   │       │   │
    │   │       │   ├── persistence/
    │   │       │   │   ├── config/
    │   │       │   │   │   └── JpaConfig.kt
    │   │       │   │   ├── entity/
    │   │       │   │   │   ├── EventEntity.kt
    │   │       │   │   │   └── SchemaEntity.kt
    │   │       │   │   ├── repository/
    │   │       │   │   │   ├── EventJpaRepository.kt
    │   │       │   │   │   └── SchemaJpaRepository.kt
    │   │       │   │   └── adapter/
    │   │       │   │       ├── EventRepositoryAdapter.kt
    │   │       │   │       └── SchemaRepositoryAdapter.kt
    │   │       │   │
    │   │       │   ├── schema/
    │   │       │   │   ├── AvroSchemaRegistry.kt
    │   │       │   │   └── JsonSchemaValidator.kt
    │   │       │   │
    │   │       │   └── monitoring/
    │   │       │       ├── DatadogMetricsAdapter.kt
    │   │       │       ├── PrometheusExporter.kt
    │   │       │       └── HealthCheckService.kt
    │   │       │
    │   │       ├── interfaces/                # 인터페이스 계층 (외부 진입점)
    │   │       │   ├── rest/
    │   │       │   │   ├── EventController.kt
    │   │       │   │   ├── SchemaController.kt
    │   │       │   │   ├── MonitoringController.kt
    │   │       │   │   └── dto/
    │   │       │   │       ├── EventRequest.kt
    │   │       │   │       ├── EventResponse.kt
    │   │       │   │       └── ErrorResponse.kt
    │   │       │   │
    │   │       │   ├── kafka/
    │   │       │   │   ├── listener/
    │   │       │   │   │   ├── UserEventListener.kt
    │   │       │   │   │   ├── PaymentEventListener.kt
    │   │       │   │   │   └── ChatEventListener.kt
    │   │       │   │   └── handler/
    │   │       │   │       ├── DlqHandler.kt
    │   │       │   │       └── ErrorHandler.kt
    │   │       │   │
    │   │       │   └── graphql/
    │   │       │       ├── EventResolver.kt
    │   │       │       └── schema.graphqls
    │   │       │
    │   │       └── common/                    # 공통 모듈
    │   │           ├── annotation/
    │   │           │   ├── DomainService.kt
    │   │           │   └── UseCase.kt
    │   │           ├── exception/
    │   │           │   ├── BusinessException.kt
    │   │           │   └── ErrorCode.kt
    │   │           ├── util/
    │   │           │   ├── JsonUtils.kt
    │   │           │   └── TimeUtils.kt
    │   │           └── config/
    │   │               ├── AsyncConfig.kt
    │   │               └── SecurityConfig.kt
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── db/migration/
    │       │   ├── V1__create_event_table.sql
    │       │   └── V2__create_schema_table.sql
    │       └── avro/
    │           ├── user-event.avsc
    │           └── payment-event.avsc
    │
    └── test/
        └── kotlin/
            └── com/karrot/platform/kafka/
                ├── domain/
                ├── application/
                ├── infrastructure/
                ├── interfaces/
                └── integration/
```

## 1. Build Configuration

### build.gradle.kts
```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
    kotlin("kapt") version "1.9.20"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "com.karrot.platform"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-streams")
    implementation("io.confluent:kafka-avro-serializer:7.5.0")
    implementation("io.confluent:kafka-schema-registry-client:7.5.0")
    implementation("io.confluent:kafka-streams-avro-serde:7.5.0")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("com.zaxxer:HikariCP")
    
    // Monitoring
    implementation("io.micrometer:micrometer-registry-datadog")
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    // Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

## 2. Domain Layer 구현

### domain/event/model/Event.kt
```kotlin
package com.karrot.platform.kafka.domain.event.model

import java.time.Instant

/**
 * 이벤트 도메인 모델 - DDD Aggregate Root
 */
class Event private constructor(
    val id: EventId,
    val type: EventType,
    val aggregateId: String,
    val payload: EventPayload,
    val metadata: EventMetadata,
    private var status: EventStatus,
    val createdAt: Instant,
    private var publishedAt: Instant? = null,
    private var failureReason: String? = null
) {
    
    companion object {
        fun create(
            type: EventType,
            aggregateId: String,
            payload: EventPayload,
            metadata: EventMetadata = EventMetadata.empty()
        ): Event {
            return Event(
                id = EventId.generate(),
                type = type,
                aggregateId = aggregateId,
                payload = payload,
                metadata = metadata,
                status = EventStatus.CREATED,
                createdAt = Instant.now()
            )
        }
    }
    
    fun markAsPublished() {
        require(status == EventStatus.CREATED || status == EventStatus.FAILED) {
            "Event can only be published from CREATED or FAILED status"
        }
        this.status = EventStatus.PUBLISHED
        this.publishedAt = Instant.now()
        this.failureReason = null
    }
    
    fun markAsFailed(reason: String) {
        require(status != EventStatus.PUBLISHED) {
            "Published event cannot be marked as failed"
        }
        this.status = EventStatus.FAILED
        this.failureReason = reason
    }
    
    fun canRetry(): Boolean = status == EventStatus.FAILED
    
    fun getStatus(): EventStatus = status
    fun getPublishedAt(): Instant? = publishedAt
    fun getFailureReason(): String? = failureReason
}

/**
 * 이벤트 ID - Value Object
 */
@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId cannot be blank" }
    }
    
    companion object {
        fun generate(): EventId = EventId(UUID.randomUUID().toString())
        fun of(value: String): EventId = EventId(value)
    }
}

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

/**
 * 이벤트 상태
 */
enum class EventStatus {
    CREATED,
    PUBLISHED,
    FAILED,
    ARCHIVED
}

/**
 * 이벤트 페이로드 - Value Object
 */
data class EventPayload(
    val data: Map<String, Any>,
    val version: String = "1.0"
) {
    fun toJson(): String = JsonUtils.toJson(data)
    
    companion object {
        fun fromJson(json: String): EventPayload {
            return EventPayload(JsonUtils.fromJson(json))
        }
    }
}

/**
 * 이벤트 메타데이터 - Value Object
 */
data class EventMetadata(
    val correlationId: String? = null,
    val userId: String? = null,
    val source: String? = null,
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        fun empty() = EventMetadata()
    }
}
```

### domain/event/repository/EventRepository.kt
```kotlin
package com.karrot.platform.kafka.domain.event.repository

import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventId
import com.karrot.platform.kafka.domain.event.model.EventStatus
import java.time.Instant

/**
 * 이벤트 저장소 인터페이스 - Domain Layer
 */
interface EventRepository {
    suspend fun save(event: Event): Event
    suspend fun findById(id: EventId): Event?
    suspend fun findByStatus(status: EventStatus): List<Event>
    suspend fun findFailedEventsOlderThan(timestamp: Instant): List<Event>
    suspend fun deleteArchivedEventsOlderThan(timestamp: Instant): Int
}
```

### domain/event/service/EventPublisher.kt
```kotlin
package com.karrot.platform.kafka.domain.event.service

import com.karrot.platform.kafka.domain.event.model.Event

/**
 * 이벤트 발행 도메인 서비스
 */
interface EventPublisher {
    suspend fun publish(event: Event): PublishResult
    suspend fun publishBatch(events: List<Event>): List<PublishResult>
}

data class PublishResult(
    val eventId: String,
    val success: Boolean,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null,
    val error: String? = null
)
```

## 3. Application Layer 구현

### application/usecase/event/PublishEventUseCase.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.event

import com.karrot.platform.kafka.application.port.input.EventInputPort
import com.karrot.platform.kafka.application.port.output.EventOutputPort
import com.karrot.platform.kafka.application.usecase.event.dto.PublishEventCommand
import com.karrot.platform.kafka.application.usecase.event.dto.EventResponse
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventPayload
import com.karrot.platform.kafka.domain.event.model.EventMetadata
import org.springframework.transaction.annotation.Transactional
import mu.KotlinLogging

/**
 * 이벤트 발행 유스케이스
 */
@UseCase
@Transactional
class PublishEventUseCase(
    private val eventOutputPort: EventOutputPort,
    private val eventValidator: EventValidator
) : EventInputPort {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun publishEvent(command: PublishEventCommand): EventResponse {
        logger.info { "Publishing event: ${command.eventType}" }
        
        // 1. 커맨드 검증
        eventValidator.validate(command)
        
        // 2. 도메인 이벤트 생성
        val event = Event.create(
            type = command.eventType,
            aggregateId = command.aggregateId,
            payload = EventPayload(command.payload),
            metadata = EventMetadata(
                correlationId = command.correlationId,
                userId = command.userId,
                source = command.source
            )
        )
        
        // 3. 이벤트 저장 (Transactional Outbox Pattern)
        val savedEvent = eventOutputPort.saveEvent(event)
        
        // 4. Kafka로 발행 (트랜잭션 커밋 후)
        try {
            val publishResult = eventOutputPort.publishToKafka(savedEvent)
            
            if (publishResult.success) {
                savedEvent.markAsPublished()
                eventOutputPort.updateEventStatus(savedEvent)
            } else {
                savedEvent.markAsFailed(publishResult.error ?: "Unknown error")
                eventOutputPort.updateEventStatus(savedEvent)
            }
            
            return EventResponse.from(savedEvent, publishResult)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish event: ${event.id}" }
            savedEvent.markAsFailed(e.message ?: "Publication failed")
            eventOutputPort.updateEventStatus(savedEvent)
            throw EventPublicationException("Failed to publish event", e)
        }
    }
}
```

### application/port/output/EventOutputPort.kt
```kotlin
package com.karrot.platform.kafka.application.port.output

import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.service.PublishResult

/**
 * 이벤트 출력 포트 - Application Layer
 */
interface EventOutputPort {
    suspend fun saveEvent(event: Event): Event
    suspend fun updateEventStatus(event: Event): Event
    suspend fun publishToKafka(event: Event): PublishResult
    suspend fun findFailedEvents(): List<Event>
}
```

## 4. Infrastructure Layer 구현

### infrastructure/kafka/config/KafkaConfig.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.config

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

/**
 * Kafka 기본 설정
 */
@Configuration
@EnableKafka
class KafkaConfig {
    
    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Value("\${kafka.schema-registry.url}")
    private lateinit var schemaRegistryUrl: String
    
    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        return KafkaAdmin(mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
        ))
    }
    
    // 토픽 자동 생성
    @Bean
    fun userEventsTopic(): NewTopic {
        return TopicBuilder.name("karrot.user.events")
            .partitions(10)
            .replicas(3)
            .config("retention.ms", "604800000") // 7 days
            .config("compression.type", "lz4")
            .build()
    }
    
    @Bean
    fun itemEventsTopic(): NewTopic {
        return TopicBuilder.name("karrot.item.events")
            .partitions(20) // 더 많은 파티션 (높은 처리량)
            .replicas(3)
            .config("retention.ms", "259200000") // 3 days
            .build()
    }
    
    @Bean
    fun dlqTopic(): NewTopic {
        return TopicBuilder.name("karrot.events.dlq")
            .partitions(5)
            .replicas(3)
            .config("retention.ms", "2592000000") // 30 days
            .build()
    }
}
```

### infrastructure/kafka/producer/KafkaEventProducer.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.producer

import com.karrot.platform.kafka.application.port.output.EventOutputPort
import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.service.PublishResult
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.future.await
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Kafka 이벤트 프로듀서 구현
 */
@Component
class KafkaEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val meterRegistry: MeterRegistry
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun publish(event: Event, topic: String): PublishResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val message = buildKafkaMessage(event)
            val result = kafkaTemplate.send(topic, event.aggregateId, message).await()
            
            recordMetrics(topic, event.type.name, true, startTime)
            
            PublishResult(
                eventId = event.id.value,
                success = true,
                topic = result.recordMetadata.topic(),
                partition = result.recordMetadata.partition(),
                offset = result.recordMetadata.offset()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish event: ${event.id}" }
            recordMetrics(topic, event.type.name, false, startTime)
            
            PublishResult(
                eventId = event.id.value,
                success = false,
                error = e.message
            )
        }
    }
    
    private fun buildKafkaMessage(event: Event): KafkaMessage {
        return KafkaMessage(
            eventId = event.id.value,
            eventType = event.type.name,
            aggregateId = event.aggregateId,
            payload = event.payload.data,
            metadata = KafkaMessageMetadata(
                correlationId = event.metadata.correlationId,
                userId = event.metadata.userId,
                source = event.metadata.source,
                timestamp = event.createdAt,
                version = event.payload.version
            )
        )
    }
    
    private fun recordMetrics(topic: String, eventType: String, success: Boolean, startTime: Long) {
        val duration = System.currentTimeMillis() - startTime
        
        meterRegistry.counter(
            "kafka.producer.events",
            "topic", topic,
            "event_type", eventType,
            "success", success.toString()
        ).increment()
        
        meterRegistry.timer(
            "kafka.producer.duration",
            "topic", topic
        ).record(duration, TimeUnit.MILLISECONDS)
    }
}

data class KafkaMessage(
    val eventId: String,
    val eventType: String,
    val aggregateId: String,
    val payload: Map<String, Any>,
    val metadata: KafkaMessageMetadata
)

data class KafkaMessageMetadata(
    val correlationId: String?,
    val userId: String?,
    val source: String?,
    val timestamp: Instant,
    val version: String
)
```

### infrastructure/persistence/adapter/EventRepositoryAdapter.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.adapter

import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.model.EventId
import com.karrot.platform.kafka.domain.event.model.EventStatus
import com.karrot.platform.kafka.domain.event.repository.EventRepository
import com.karrot.platform.kafka.infrastructure.persistence.entity.EventEntity
import com.karrot.platform.kafka.infrastructure.persistence.repository.EventJpaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * EventRepository 구현체 - Infrastructure Layer
 */
@Component
class EventRepositoryAdapter(
    private val eventJpaRepository: EventJpaRepository,
    private val eventMapper: EventMapper
) : EventRepository {
    
    override suspend fun save(event: Event): Event = withContext(Dispatchers.IO) {
        val entity = eventMapper.toEntity(event)
        val savedEntity = eventJpaRepository.save(entity)
        eventMapper.toDomain(savedEntity)
    }
    
    override suspend fun findById(id: EventId): Event? = withContext(Dispatchers.IO) {
        eventJpaRepository.findById(id.value)
            .orElse(null)
            ?.let { eventMapper.toDomain(it) }
    }
    
    override suspend fun findByStatus(status: EventStatus): List<Event> = withContext(Dispatchers.IO) {
        eventJpaRepository.findByStatus(status)
            .map { eventMapper.toDomain(it) }
    }
    
    override suspend fun findFailedEventsOlderThan(timestamp: Instant): List<Event> = withContext(Dispatchers.IO) {
        eventJpaRepository.findByStatusAndCreatedAtBefore(EventStatus.FAILED, timestamp)
            .map { eventMapper.toDomain(it) }
    }
    
    override suspend fun deleteArchivedEventsOlderThan(timestamp: Instant): Int = withContext(Dispatchers.IO) {
        eventJpaRepository.deleteByStatusAndCreatedAtBefore(EventStatus.ARCHIVED, timestamp)
    }
}