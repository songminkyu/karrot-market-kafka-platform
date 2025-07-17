# 당근마켓 Kafka 플랫폼 - 누락된 파일 구현

## 1. 프로젝트 설정 파일

### settings.gradle.kts
```kotlin
rootProject.name = "karrot-market-kafka-platform"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

### gradle.properties
```properties
# Gradle
org.gradle.jvmargs=-Xmx2048m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

# Kotlin
kotlin.code.style=official
kotlin.incremental=true

# Versions
kotlinVersion=1.9.20
springBootVersion=3.2.0
springKafkaVersion=3.0.12
confluentVersion=7.5.0
```

### buildSrc/src/main/kotlin/Dependencies.kt
```kotlin
object Dependencies {
    
    object Spring {
        const val boot = "org.springframework.boot:spring-boot-starter"
        const val webflux = "org.springframework.boot:spring-boot-starter-webflux"
        const val web = "org.springframework.boot:spring-boot-starter-web"
        const val dataJpa = "org.springframework.boot:spring-boot-starter-data-jpa"
        const val validation = "org.springframework.boot:spring-boot-starter-validation"
        const val actuator = "org.springframework.boot:spring-boot-starter-actuator"
        const val kafka = "org.springframework.kafka:spring-kafka"
        const val test = "org.springframework.boot:spring-boot-starter-test"
        const val kafkaTest = "org.springframework.kafka:spring-kafka-test"
    }
    
    object Kafka {
        const val clients = "org.apache.kafka:kafka-clients"
        const val streams = "org.apache.kafka:kafka-streams"
        const val avroSerializer = "io.confluent:kafka-avro-serializer:${Versions.confluent}"
        const val schemaRegistry = "io.confluent:kafka-schema-registry-client:${Versions.confluent}"
        const val streamsAvroSerde = "io.confluent:kafka-streams-avro-serde:${Versions.confluent}"
    }
    
    object Kotlin {
        const val stdlib = "org.jetbrains.kotlin:kotlin-stdlib"
        const val reflect = "org.jetbrains.kotlin:kotlin-reflect"
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core"
        const val coroutinesReactor = "org.jetbrains.kotlinx:kotlinx-coroutines-reactor"
        const val logging = "io.github.microutils:kotlin-logging-jvm:${Versions.kotlinLogging}"
    }
    
    object Database {
        const val postgresql = "org.postgresql:postgresql"
        const val flyway = "org.flywaydb:flyway-core"
        const val hikari = "com.zaxxer:HikariCP"
    }
    
    object Monitoring {
        const val micrometerDatadog = "io.micrometer:micrometer-registry-datadog"
        const val micrometerPrometheus = "io.micrometer:micrometer-registry-prometheus"
    }
    
    object Test {
        const val junit = "org.junit.jupiter:junit-jupiter"
        const val mockk = "io.mockk:mockk:${Versions.mockk}"
        const val springMockk = "com.ninja-squad:springmockk:${Versions.springMockk}"
        const val testcontainers = "org.testcontainers:testcontainers"
        const val testcontainersKafka = "org.testcontainers:kafka"
        const val testcontainersPostgresql = "org.testcontainers:postgresql"
    }
    
    object Jackson {
        const val kotlin = "com.fasterxml.jackson.module:jackson-module-kotlin"
        const val jsr310 = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"
    }
}
```

### buildSrc/src/main/kotlin/Versions.kt
```kotlin
object Versions {
    const val kotlin = "1.9.20"
    const val springBoot = "3.2.0"
    const val springDependencyManagement = "1.1.4"
    const val springKafka = "3.0.12"
    const val confluent = "7.5.0"
    const val kotlinLogging = "3.0.5"
    const val mockk = "1.13.8"
    const val springMockk = "4.0.2"
    const val testcontainers = "1.19.3"
    const val avroPlugin = "1.9.1"
}
```

## 2. Main Application

### KarrotKafkaPlatformApplication.kt
```kotlin
package com.karrot.platform.kafka

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
class KarrotKafkaPlatformApplication

fun main(args: Array<String>) {
    runApplication<KarrotKafkaPlatformApplication>(*args)
}
```

## 3. Domain Layer - 누락된 파일들

### domain/event/service/EventValidator.kt
```kotlin
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
```

### domain/event/exception/EventDomainException.kt
```kotlin
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
```

## 4. Application Layer - DTO 구현

### application/usecase/event/dto/PublishEventCommand.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.event.dto

import com.karrot.platform.kafka.domain.event.model.EventType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class PublishEventCommand(
    @field:NotNull(message = "Event type is required")
    val eventType: EventType,
    
    @field:NotBlank(message = "Aggregate ID is required")
    val aggregateId: String,
    
    @field:NotNull(message = "Payload is required")
    val payload: Map<String, Any>,
    
    val correlationId: String? = null,
    val userId: String? = null,
    val source: String? = null
)
```

### application/usecase/event/dto/EventResponse.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.event.dto

import com.karrot.platform.kafka.domain.event.model.Event
import com.karrot.platform.kafka.domain.event.service.PublishResult
import java.time.Instant

data class EventResponse(
    val eventId: String,
    val status: String,
    val publishedAt: Instant?,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null
) {
    companion object {
        fun from(event: Event, publishResult: PublishResult): EventResponse {
            return EventResponse(
                eventId = event.id.value,
                status = event.getStatus().name,
                publishedAt = event.getPublishedAt(),
                topic = publishResult.topic,
                partition = publishResult.partition,
                offset = publishResult.offset
            )
        }
    }
}
```

### application/usecase/event/ConsumeEventUseCase.kt
```kotlin
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
```

## 5. Infrastructure Layer - 누락된 설정

### infrastructure/kafka/config/KafkaProducerConfig.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaProducerConfig {
    
    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configs = mutableMapOf<String, Any>()
        
        // 기본 설정
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        
        // 성능 최적화
        configs[ProducerConfig.ACKS_CONFIG] = "all"
        configs[ProducerConfig.RETRIES_CONFIG] = 3
        configs[ProducerConfig.BATCH_SIZE_CONFIG] = 100_000
        configs[ProducerConfig.LINGER_MS_CONFIG] = 10
        configs[ProducerConfig.BUFFER_MEMORY_CONFIG] = 64 * 1024 * 1024
        configs[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"
        
        // Idempotence 설정
        configs[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configs[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        
        // 타임아웃 설정
        configs[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 30000
        configs[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 120000
        
        return DefaultKafkaProducerFactory(configs)
    }
    
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }
    
    // 트랜잭션용 Producer Factory
    @Bean
    fun transactionalProducerFactory(): ProducerFactory<String, Any> {
        val factory = DefaultKafkaProducerFactory<String, Any>(producerConfigs())
        factory.setTransactionIdPrefix("karrot-tx-")
        return factory
    }
    
    @Bean
    fun transactionalKafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(transactionalProducerFactory())
    }
    
    private fun producerConfigs(): Map<String, Any> {
        return mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
    }
}
```

### infrastructure/kafka/config/KafkaConsumerConfig.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer

@Configuration
class KafkaConsumerConfig {
    
    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Value("\${kafka.consumer.group-id}")
    private lateinit var groupId: String
    
    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val configs = mutableMapOf<String, Any>()
        
        configs[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        configs[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = ErrorHandlingDeserializer::class.java
        configs[ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS] = JsonDeserializer::class.java
        configs[JsonDeserializer.TRUSTED_PACKAGES] = "*"
        configs[JsonDeserializer.VALUE_DEFAULT_TYPE] = "com.karrot.platform.kafka.infrastructure.kafka.message.KafkaMessage"
        
        // 성능 설정
        configs[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configs[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configs[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 500
        configs[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = 300000
        configs[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = 30000
        configs[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = 10000
        
        return DefaultKafkaConsumerFactory(configs)
    }
    
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.setConcurrency(3)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.setCommonErrorHandler(kafkaErrorHandler())
        return factory
    }
    
    // 배치 처리용 Factory
    @Bean
    fun batchKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.setConcurrency(5)
        factory.isBatchListener = true
        factory.containerProperties.ackMode = ContainerProperties.AckMode.BATCH
        return factory
    }
    
    @Bean
    fun kafkaErrorHandler(): DefaultErrorHandler {
        val errorHandler = DefaultErrorHandler(
            DeadLetterPublishingRecoverer(kafkaTemplate()),
            FixedBackOff(1000L, 3)
        )
        
        // 재시도하지 않을 예외
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException::class.java,
            ValidationException::class.java
        )
        
        return errorHandler
    }
}
```

## 6. Interface Layer - 누락된 DTO

### interfaces/rest/dto/EventRequest.kt
```kotlin
package com.karrot.platform.kafka.interfaces.rest.dto

import com.karrot.platform.kafka.domain.event.model.EventType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class EventRequest(
    @field:NotNull(message = "Event type is required")
    val eventType: EventType,
    
    @field:NotBlank(message = "Aggregate ID is required")
    val aggregateId: String,
    
    @field:NotNull(message = "Payload is required")
    val payload: Map<String, Any>,
    
    val correlationId: String? = null,
    val userId: String? = null,
    val source: String? = null
)
```

### interfaces/rest/dto/ErrorResponse.kt
```kotlin
package com.karrot.platform.kafka.interfaces.rest.dto

import com.karrot.platform.kafka.common.exception.ErrorCode
import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
    val path: String? = null,
    val details: Map<String, Any>? = null
) {
    companion object {
        fun of(errorCode: ErrorCode, path: String? = null): ErrorResponse {
            return ErrorResponse(
                code = errorCode.code,
                message = errorCode.message,
                path = path
            )
        }
        
        fun of(errorCode: ErrorCode, message: String, path: String? = null): ErrorResponse {
            return ErrorResponse(
                code = errorCode.code,
                message = message,
                path = path
            )
        }
    }
}
```

### interfaces/kafka/handler/DlqHandler.kt
```kotlin
package com.karrot.platform.kafka.interfaces.kafka.handler

import com.karrot.platform.kafka.common.util.JsonUtils
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
class DlqHandler(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        const val DLQ_TOPIC_PREFIX = "dlq."
        const val ORIGINAL_TOPIC_HEADER = "original_topic"
        const val EXCEPTION_MESSAGE_HEADER = "exception_message"
        const val EXCEPTION_CLASS_HEADER = "exception_class"
        const val FAILED_AT_HEADER = "failed_at"
        const val RETRY_COUNT_HEADER = "retry_count"
    }
    
    fun <T> sendToDlq(
        originalTopic: String,
        record: ConsumerRecord<String, T>,
        exception: Exception
    ) {
        val dlqTopic = "$DLQ_TOPIC_PREFIX$originalTopic"
        
        try {
            val headers = mutableListOf(
                RecordHeader(ORIGINAL_TOPIC_HEADER, originalTopic.toByteArray()),
                RecordHeader(EXCEPTION_MESSAGE_HEADER, (exception.message ?: "Unknown error").toByteArray()),
                RecordHeader(EXCEPTION_CLASS_HEADER, exception.javaClass.name.toByteArray()),
                RecordHeader(FAILED_AT_HEADER, Instant.now().toString().toByteArray())
            )
            
            // 기존 헤더 복사
            record.headers().forEach { header ->
                headers.add(RecordHeader(header.key(), header.value()))
            }
            
            // 재시도 횟수 증가
            val retryCount = getRetryCount(record) + 1
            headers.add(RecordHeader(RETRY_COUNT_HEADER, retryCount.toString().toByteArray()))
            
            val dlqRecord = ProducerRecord(
                dlqTopic,
                null,
                record.timestamp(),
                record.key(),
                record.value(),
                headers
            )
            
            kafkaTemplate.send(dlqRecord)
            
            logger.warn { 
                "Message sent to DLQ - Topic: $dlqTopic, " +
                "Key: ${record.key()}, " +
                "RetryCount: $retryCount, " +
                "Exception: ${exception.message}" 
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to send message to DLQ" }
        }
    }
    
    fun sendToDlq(
        originalTopic: String,
        message: Any,
        exception: Exception
    ) {
        val dlqTopic = "$DLQ_TOPIC_PREFIX$originalTopic"
        
        val dlqMessage = DlqMessage(
            originalTopic = originalTopic,
            message = message,
            exceptionMessage = exception.message ?: "Unknown error",
            exceptionClass = exception.javaClass.name,
            failedAt = Instant.now()
        )
        
        kafkaTemplate.send(dlqTopic, JsonUtils.toJson(message), dlqMessage)
            .whenComplete { _, ex ->
                if (ex != null) {
                    logger.error(ex) { "Failed to send message to DLQ" }
                } else {
                    logger.warn { "Message sent to DLQ: $dlqTopic" }
                }
            }
    }
    
    private fun getRetryCount(record: ConsumerRecord<*, *>): Int {
        val header = record.headers().lastHeader(RETRY_COUNT_HEADER)
        return header?.let {
            String(it.value(), StandardCharsets.UTF_8).toIntOrNull() ?: 0
        } ?: 0
    }
}

data class DlqMessage(
    val originalTopic: String,
    val message: Any,
    val exceptionMessage: String,
    val exceptionClass: String,
    val failedAt: Instant
)
```

## 7. Common Layer - 추가 설정

### common/config/AsyncConfig.kt
```kotlin
package com.karrot.platform.kafka.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
class AsyncConfig : AsyncConfigurer {
    
    @Bean(name = ["taskExecutor"])
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 10
        executor.maxPoolSize = 50
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("Async-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }
    
    @Bean(name = ["kafkaExecutor"])
    fun kafkaExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 20
        executor.maxPoolSize = 100
        executor.queueCapacity = 500
        executor.setThreadNamePrefix("Kafka-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }
}
```

### common/config/SecurityConfig.kt
```kotlin
package com.karrot.platform.kafka.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {
    
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api/v1/monitoring/**").permitAll()
                    .requestMatchers("/api/v1/**").authenticated()
                    .anyRequest().permitAll()
            }
            .build()
    }
}
```

### common/util/TimeUtils.kt
```kotlin
package com.karrot.platform.kafka.common.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {
    
    private val DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul")
    private val DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    fun now(): Instant = Instant.now()
    
    fun toLocalDateTime(instant: Instant, zoneId: ZoneId = DEFAULT_ZONE_ID): LocalDateTime {
        return LocalDateTime.ofInstant(instant, zoneId)
    }
    
    fun format(instant: Instant, pattern: String? = null): String {
        val formatter = pattern?.let { DateTimeFormatter.ofPattern(it) } ?: DEFAULT_FORMATTER
        return toLocalDateTime(instant).format(formatter)
    }
    
    fun parseInstant(dateTimeString: String, pattern: String? = null): Instant {
        val formatter = pattern?.let { DateTimeFormatter.ofPattern(it) } ?: DEFAULT_FORMATTER
        val localDateTime = LocalDateTime.parse(dateTimeString, formatter)
        return localDateTime.atZone(DEFAULT_ZONE_ID).toInstant()
    }
}
```

## 8. Database Migration 파일

### resources/db/migration/V1__create_event_table.sql
```sql
-- Event Store 테이블
CREATE TABLE IF NOT EXISTS event_store (
    event_id VARCHAR(255) PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data TEXT NOT NULL,
    event_time TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    published_at TIMESTAMP,
    failure_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_event_store_aggregate_id ON event_store(aggregate_id);
CREATE INDEX idx_event_store_event_type ON event_store(event_type);
CREATE INDEX idx_event_store_status ON event_store(status);
CREATE INDEX idx_event_store_created_at ON event_store(created_at);
CREATE INDEX idx_event_store_published_at ON event_store(published_at);

-- 복합 인덱스
CREATE INDEX idx_event_store_status_created_at ON event_store(status, created_at);

-- 트리거: updated_at 자동 업데이트
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$ language 'plpgsql';

CREATE TRIGGER update_event_store_updated_at 
    BEFORE UPDATE ON event_store 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();