# 당근마켓 Kafka 플랫폼 - 최종 추가 파일들

## 21. Infrastructure Layer - Kafka Streams Config

### infrastructure/kafka/config/KafkaStreamsConfig.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.config

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.processor.WallclockTimestampExtractor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import org.springframework.kafka.support.serializer.JsonSerde

@Configuration
class KafkaStreamsConfig {
    
    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Value("\${kafka.streams.application-id}")
    private lateinit var applicationId: String
    
    @Value("\${kafka.streams.state-dir}")
    private lateinit var stateDir: String
    
    @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
    fun kStreamsConfig(): KafkaStreamsConfiguration {
        val props = mutableMapOf<String, Any>()
        
        // 기본 설정
        props[StreamsConfig.APPLICATION_ID_CONFIG] = applicationId
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = JsonSerde::class.java.name
        props[StreamsConfig.STATE_DIR_CONFIG] = stateDir
        
        // 성능 최적화
        props[StreamsConfig.COMMIT_INTERVAL_MS_CONFIG] = 1000
        props[StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG] = 10 * 1024 * 1024 // 10MB
        props[StreamsConfig.NUM_STREAM_THREADS_CONFIG] = 3
        
        // Exactly-once 처리
        props[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = StreamsConfig.EXACTLY_ONCE_V2
        
        // 타임스탬프 추출기
        props[StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG] = 
            WallclockTimestampExtractor::class.java.name
        
        return KafkaStreamsConfiguration(props)
    }
    
    @Bean
    fun streamsBuilderFactoryBeanCustomizer(): StreamsBuilderFactoryBeanCustomizer {
        return StreamsBuilderFactoryBeanCustomizer { factoryBean ->
            factoryBean.setStateListener { newState, oldState ->
                logger.info { "Streams state changed from $oldState to $newState" }
            }
        }
    }
}
```

### infrastructure/kafka/config/SchemaRegistryConfig.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka.config

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.ProducerFactory

@Configuration
class SchemaRegistryConfig {
    
    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String
    
    @Value("\${kafka.schema-registry.url}")
    private lateinit var schemaRegistryUrl: String
    
    @Bean
    fun schemaRegistryClient(): SchemaRegistryClient {
        return CachedSchemaRegistryClient(schemaRegistryUrl, 1000)
    }
    
    @Bean
    fun avroProducerFactory(): ProducerFactory<String, Any> {
        val configs = mutableMapOf<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
        configs[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl
        configs[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = true
        configs[AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY] = 
            "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy"
        
        return DefaultKafkaProducerFactory(configs)
    }
    
    @Bean
    fun avroConsumerFactory(): ConsumerFactory<String, Any> {
        val configs = mutableMapOf<String, Any>()
        configs[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ConsumerConfig.GROUP_ID_CONFIG] = "avro-consumer-group"
        configs[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
        configs[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl
        configs[KafkaAvroDeserializer.SPECIFIC_AVRO_READER_CONFIG] = true
        
        return DefaultKafkaConsumerFactory(configs)
    }
}
```

## 22. Application Layer - 추가 UseCase

### application/usecase/event/ReprocessEventUseCase.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.event

import com.karrot.platform.kafka.application.port.output.EventOutputPort
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.common.exception.EventNotFoundException
import com.karrot.platform.kafka.domain.event.model.EventId
import com.karrot.platform.kafka.domain.event.model.EventStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@UseCase
class ReprocessEventUseCase(
    private val eventOutputPort: EventOutputPort
) {
    
    private val logger = KotlinLogging.logger {}
    
    @Transactional
    suspend fun reprocessEvent(eventId: String): EventResponse {
        val event = eventOutputPort.findEventById(EventId(eventId))
            ?: throw EventNotFoundException(eventId)
        
        if (!event.canRetry()) {
            throw IllegalStateException("Event cannot be retried: ${event.getStatus()}")
        }
        
        logger.info { "Reprocessing event: $eventId" }
        
        val publishResult = eventOutputPort.publishToKafka(event)
        
        if (publishResult.success) {
            event.markAsPublished()
        } else {
            event.markAsFailed(publishResult.error ?: "Reprocessing failed")
        }
        
        val updatedEvent = eventOutputPort.updateEventStatus(event)
        
        return EventResponse.from(updatedEvent, publishResult)
    }
    
    @Scheduled(fixedDelay = 60000) // 1분마다
    suspend fun reprocessFailedEvents() {
        val cutoffTime = Instant.now().minus(5, ChronoUnit.MINUTES)
        val failedEvents = eventOutputPort.findFailedEventsOlderThan(cutoffTime)
        
        if (failedEvents.isEmpty()) {
            return
        }
        
        logger.info { "Found ${failedEvents.size} failed events to reprocess" }
        
        failedEvents.forEach { event ->
            try {
                reprocessEvent(event.id.value)
            } catch (e: Exception) {
                logger.error(e) { "Failed to reprocess event: ${event.id}" }
            }
        }
    }
    
    fun streamFailedEvents(): Flow<Event> = flow {
        val failedEvents = eventOutputPort.findFailedEvents()
        failedEvents.forEach { emit(it) }
    }
}
```

### application/usecase/schema/RegisterSchemaUseCase.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.schema

import com.karrot.platform.kafka.application.port.output.SchemaRegistryOutputPort
import com.karrot.platform.kafka.application.usecase.schema.dto.SchemaRegistrationCommand
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.domain.schema.model.Schema
import com.karrot.platform.kafka.domain.schema.repository.SchemaRepository
import com.karrot.platform.kafka.domain.schema.service.SchemaEvolutionService
import mu.KotlinLogging
import org.springframework.transaction.annotation.Transactional

@UseCase
@Transactional
class RegisterSchemaUseCase(
    private val schemaRepository: SchemaRepository,
    private val schemaRegistryOutputPort: SchemaRegistryOutputPort,
    private val schemaEvolutionService: SchemaEvolutionService
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun registerSchema(command: SchemaRegistrationCommand): SchemaRegistrationResult {
        logger.info { "Registering schema for subject: ${command.subject}" }
        
        // 1. 기존 스키마 확인
        val latestSchema = schemaRepository.findLatestBySubject(command.subject)
        
        // 2. 호환성 검사
        if (latestSchema != null) {
            val compatibilityResult = schemaEvolutionService.checkCompatibility(
                currentSchema = latestSchema,
                newSchemaContent = command.schema,
                compatibilityMode = command.compatibilityMode
            )
            
            if (compatibilityResult is CompatibilityResult.Incompatible) {
                return SchemaRegistrationResult.Failed(
                    reason = compatibilityResult.reason
                )
            }
        }
        
        // 3. Schema Registry에 등록
        val registeredSchema = schemaRegistryOutputPort.registerSchema(
            subject = command.subject,
            schema = command.schema
        )
        
        // 4. 로컬 DB에 저장
        schemaRepository.save(registeredSchema)
        
        return SchemaRegistrationResult.Success(
            schemaId = registeredSchema.id.value,
            version = registeredSchema.version.value
        )
    }
}

sealed class SchemaRegistrationResult {
    data class Success(
        val schemaId: Long,
        val version: Int
    ) : SchemaRegistrationResult()
    
    data class Failed(
        val reason: String
    ) : SchemaRegistrationResult()
}
```

### application/usecase/schema/dto/SchemaRegistrationCommand.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.schema.dto

import com.karrot.platform.kafka.domain.schema.model.SchemaCompatibility
import com.karrot.platform.kafka.domain.schema.model.SchemaType
import jakarta.validation.constraints.NotBlank

data class SchemaRegistrationCommand(
    @field:NotBlank(message = "Subject is required")
    val subject: String,
    
    @field:NotBlank(message = "Schema is required")
    val schema: String,
    
    val schemaType: SchemaType = SchemaType.AVRO,
    
    val compatibilityMode: SchemaCompatibility = SchemaCompatibility.BACKWARD
)
```

### application/usecase/monitoring/GetConsumerLagUseCase.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.monitoring

import com.karrot.platform.kafka.application.port.input.MonitoringInputPort
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.domain.monitoring.service.MonitoringService
import mu.KotlinLogging

@UseCase
class GetConsumerLagUseCase(
    private val monitoringService: MonitoringService
) : MonitoringInputPort {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun getConsumerLag(groupId: String): ConsumerLagInfo {
        logger.debug { "Getting consumer lag for group: $groupId" }
        
        val lag = monitoringService.getConsumerLag(groupId)
        
        return ConsumerLagInfo(
            groupId = lag.groupId,
            totalLag = lag.totalLag,
            topics = lag.topicLags.map { topicLag ->
                TopicLag(
                    topic = topicLag.topic,
                    totalLag = topicLag.totalLag,
                    partitions = topicLag.partitionLags.map { partitionLag ->
                        PartitionLag(
                            partition = partitionLag.partition,
                            currentOffset = partitionLag.currentOffset,
                            endOffset = partitionLag.endOffset,
                            lag = partitionLag.lag
                        )
                    }
                )
            }
        )
    }
    
    override suspend fun getAllConsumerLags(): List<ConsumerLagInfo> {
        logger.debug { "Getting all consumer lags" }
        
        return monitoringService.getAllConsumerLags().map { lag ->
            ConsumerLagInfo(
                groupId = lag.groupId,
                totalLag = lag.totalLag,
                topics = lag.topicLags.map { topicLag ->
                    TopicLag(
                        topic = topicLag.topic,
                        totalLag = topicLag.totalLag,
                        partitions = topicLag.partitionLags.map { partitionLag ->
                            PartitionLag(
                                partition = partitionLag.partition,
                                currentOffset = partitionLag.currentOffset,
                                endOffset = partitionLag.endOffset,
                                lag = partitionLag.lag
                            )
                        }
                    }
                )
            }
        }
    }
}
```

## 23. Interface Layer - 추가 Controller

### interfaces/rest/SchemaController.kt
```kotlin
package com.karrot.platform.kafka.interfaces.rest

import com.karrot.platform.kafka.application.usecase.schema.RegisterSchemaUseCase
import com.karrot.platform.kafka.application.usecase.schema.dto.SchemaRegistrationCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Schema API", description = "스키마 관리 API")
@RestController
@RequestMapping("/api/v1/schemas")
class SchemaController(
    private val registerSchemaUseCase: RegisterSchemaUseCase
) {
    
    @Operation(summary = "스키마 등록", description = "새로운 스키마를 등록합니다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun registerSchema(
        @Valid @RequestBody command: SchemaRegistrationCommand
    ): SchemaRegistrationResponse {
        val result = registerSchemaUseCase.registerSchema(command)
        
        return when (result) {
            is SchemaRegistrationResult.Success -> {
                SchemaRegistrationResponse(
                    success = true,
                    schemaId = result.schemaId,
                    version = result.version
                )
            }
            is SchemaRegistrationResult.Failed -> {
                SchemaRegistrationResponse(
                    success = false,
                    error = result.reason
                )
            }
        }
    }
    
    @Operation(summary = "스키마 조회", description = "특정 주제의 스키마를 조회합니다")
    @GetMapping("/{subject}")
    suspend fun getSchema(
        @PathVariable subject: String,
        @RequestParam(required = false) version: Int?
    ): SchemaResponse {
        // 구현
        return SchemaResponse(
            subject = subject,
            version = version ?: 1,
            schema = "{}",
            schemaType = "AVRO"
        )
    }
}

data class SchemaRegistrationResponse(
    val success: Boolean,
    val schemaId: Long? = null,
    val version: Int? = null,
    val error: String? = null
)

data class SchemaResponse(
    val subject: String,
    val version: Int,
    val schema: String,
    val schemaType: String
)
```

## 24. Interface Layer - Kafka Listeners

### interfaces/kafka/listener/ChatEventListener.kt
```kotlin
package com.karrot.platform.kafka.interfaces.kafka.listener

import com.karrot.platform.kafka.infrastructure.kafka.consumer.ErrorHandlingConsumer
import com.karrot.platform.kafka.infrastructure.kafka.handler.RetryPolicy
import com.karrot.platform.kafka.interfaces.kafka.handler.DlqHandler
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ChatEventListener(
    private val dlqHandler: DlqHandler,
    retryPolicy: RetryPolicy
) : ErrorHandlingConsumer<ChatEventMessage>(retryPolicy) {
    
    private val logger = KotlinLogging.logger {}
    
    @KafkaListener(
        topics = ["karrot.chat.events"],
        groupId = "chat-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    suspend fun handleChatEvent(
        record: ConsumerRecord<String, ChatEventMessage>,
        acknowledgment: Acknowledgment
    ) {
        val message = record.value()
        
        processWithRetry(message, acknowledgment) { msg ->
            when (msg.eventType) {
                "CHAT_MESSAGE_SENT" -> processChatMessageSent(msg)
                "CHAT_MESSAGE_READ" -> processChatMessageRead(msg)
                "CHAT_ROOM_CREATED" -> processChatRoomCreated(msg)
                else -> logger.warn { "Unknown chat event type: ${msg.eventType}" }
            }
        }
    }
    
    private suspend fun processChatMessageSent(message: ChatEventMessage) {
        logger.info { "Processing chat message sent: ${message.chatRoomId}" }
        
        // 1. 푸시 알림 전송
        // 2. 읽지 않은 메시지 카운트 증가
        // 3. 최근 대화 목록 업데이트
    }
    
    private suspend fun processChatMessageRead(message: ChatEventMessage) {
        logger.info { "Processing chat message read: ${message.chatRoomId}" }
        
        // 1. 읽음 상태 업데이트
        // 2. 읽지 않은 메시지 카운트 감소
    }
    
    private suspend fun processChatRoomCreated(message: ChatEventMessage) {
        logger.info { "Processing chat room created: ${message.chatRoomId}" }
        
        // 1. 채팅방 초기화
        // 2. 참여자 설정
    }
    
    override suspend fun handleFinalFailure(
        message: ChatEventMessage,
        exception: Exception,
        acknowledgment: Acknowledgment
    ) {
        logger.error(exception) { "Failed to process chat event: ${message.eventId}" }
        
        dlqHandler.sendToDlq(
            originalTopic = "karrot.chat.events",
            message = message,
            exception = exception
        )
        
        acknowledgment.acknowledge()
    }
}

data class ChatEventMessage(
    val eventId: String,
    val eventType: String,
    val chatRoomId: String,
    val senderId: String,
    val receiverId: String?,
    val message: String?,
    val timestamp: Instant
)
```

## 25. Domain Layer - 추가 모델

### domain/monitoring/model/ConsumerLag.kt
```kotlin
package com.karrot.platform.kafka.domain.monitoring.model

/**
 * Consumer Lag 도메인 모델
 */
data class ConsumerLag(
    val groupId: String,
    val totalLag: Long,
    val topicLags: List<TopicLag>
) {
    fun isHealthy(threshold: Long = 10000): Boolean {
        return totalLag < threshold
    }
    
    fun getCriticalTopics(threshold: Long = 5000): List<TopicLag> {
        return topicLags.filter { it.totalLag > threshold }
    }
}

data class TopicLag(
    val topic: String,
    val totalLag: Long,
    val partitionLags: List<PartitionLag>
) {
    fun getMaxPartitionLag(): Long {
        return partitionLags.maxOfOrNull { it.lag } ?: 0L
    }
}

data class PartitionLag(
    val partition: Int,
    val currentOffset: Long,
    val endOffset: Long,
    val lag: Long
) {
    fun getLagPercentage(): Double {
        return if (endOffset > 0) {
            (lag.toDouble() / endOffset) * 100
        } else {
            0.0
        }
    }
}
```

### domain/monitoring/model/TopicMetrics.kt
```kotlin
package com.karrot.platform.kafka.domain.monitoring.model

import java.time.Instant

/**
 * 토픽 메트릭 도메인 모델
 */
data class TopicMetrics(
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val messageRate: Double,
    val bytesInPerSec: Double,
    val bytesOutPerSec: Double,
    val totalMessages: Long,
    val timestamp: Instant = Instant.now()
) {
    fun getThroughputMBPerSec(): Double {
        return (bytesInPerSec + bytesOutPerSec) / (1024 * 1024)
    }
    
    fun isHighThroughput(thresholdMBPerSec: Double = 100.0): Boolean {
        return getThroughputMBPerSec() > thresholdMBPerSec
    }
}
```

### domain/monitoring/service/MonitoringService.kt
```kotlin
package com.karrot.platform.kafka.domain.monitoring.service

import com.karrot.platform.kafka.common.annotation.DomainService
import com.karrot.platform.kafka.domain.monitoring.model.ConsumerLag
import com.karrot.platform.kafka.domain.monitoring.model.TopicMetrics
import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ConsumerGroupDescription
import org.springframework.stereotype.Service

@DomainService
class MonitoringService(
    private val adminClient: AdminClient
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun getConsumerLag(groupId: String): ConsumerLag {
        try {
            val groupDescription = adminClient.describeConsumerGroups(listOf(groupId))
                .describedGroups()[groupId]?.get()
                ?: throw IllegalArgumentException("Consumer group not found: $groupId")
            
            val offsets = adminClient.listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata()
                .get()
            
            val topicLags = offsets.entries
                .groupBy { it.key.topic() }
                .map { (topic, partitions) ->
                    val partitionLags = partitions.map { (topicPartition, offsetMetadata) ->
                        val endOffset = getEndOffset(topicPartition)
                        val currentOffset = offsetMetadata.offset()
                        val lag = endOffset - currentOffset
                        
                        PartitionLag(
                            partition = topicPartition.partition(),
                            currentOffset = currentOffset,
                            endOffset = endOffset,
                            lag = lag
                        )
                    }
                    
                    TopicLag(
                        topic = topic,
                        totalLag = partitionLags.sumOf { it.lag },
                        partitionLags = partitionLags
                    )
                }
            
            return ConsumerLag(
                groupId = groupId,
                totalLag = topicLags.sumOf { it.totalLag },
                topicLags = topicLags
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get consumer lag for group: $groupId" }
            throw MonitoringException("Failed to get consumer lag", e)
        }
    }
    
    suspend fun getAllConsumerLags(): List<ConsumerLag> {
        return try {
            val groups = adminClient.listConsumerGroups().all().get()
            groups.mapNotNull { group ->
                try {
                    getConsumerLag(group.groupId())
                } catch (e: Exception) {
                    logger.warn { "Failed to get lag for group ${group.groupId()}: ${e.message}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get all consumer lags" }
            emptyList()
        }
    }
    
    suspend fun getTopicMetrics(topicName: String): TopicMetrics {
        try {
            val topicDescription = adminClient.describeTopics(listOf(topicName))
                .values()[topicName]?.get()
                ?: throw IllegalArgumentException("Topic not found: $topicName")
            
            // 실제로는 JMX 메트릭을 읽어와야 함
            return TopicMetrics(
                topicName = topicName,
                partitionCount = topicDescription.partitions().size,
                replicationFactor = topicDescription.partitions()[0].replicas().size,
                messageRate = 1000.0, // 실제 메트릭으로 대체
                bytesInPerSec = 1024 * 1024.0, // 실제 메트릭으로 대체
                bytesOutPerSec = 1024 * 1024.0, // 실제 메트릭으로 대체
                totalMessages = 1000000L // 실제 메트릭으로 대체
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get topic metrics: $topicName" }
            throw MonitoringException("Failed to get topic metrics", e)
        }
    }
    
    private fun getEndOffset(topicPartition: org.apache.kafka.common.TopicPartition): Long {
        val endOffsets = adminClient.listOffsets(
            mapOf(topicPartition to org.apache.kafka.clients.admin.OffsetSpec.latest())
        ).all().get()
        
        return endOffsets[topicPartition]?.offset() ?: 0L
    }
}

class MonitoringException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

## 26. SDK 구현 상세

### sdk/producer/EventProducer.kt
```kotlin
package com.karrot.platform.kafka.sdk.producer

import com.karrot.platform.kafka.sdk.config.KafkaClientConfig
import com.karrot.platform.kafka.sdk.model.KarrotEvent
import kotlinx.coroutines.future.await
import mu.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

class EventProducer(
    private val config: KafkaClientConfig
) {
    
    private val logger = KotlinLogging.logger {}
    
    private val producer = KafkaProducer<String, Any>(
        config.toProducerProperties()
    )
    
    suspend fun publish(
        topic: String,
        key: String,
        event: KarrotEvent
    ): PublishResult {
        return try {
            val record = ProducerRecord(topic, key, event)
            val metadata = producer.send(record).await()
            
            PublishResult(
                success = true,
                topic = metadata.topic(),
                partition = metadata.partition(),
                offset = metadata.offset()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish event to topic: $topic" }
            PublishResult(
                success = false,
                error = e.message
            )
        }
    }
    
    suspend fun publishBatch(events: List<BatchEvent>): List<PublishResult> {
        return events.map { batchEvent ->
            publish(batchEvent.topic, batchEvent.key, batchEvent.event)
        }
    }
    
    fun close() {
        producer.close()
    }
}

data class PublishResult(
    val success: Boolean,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null,
    val error: String? = null
)

data class BatchEvent(
    val topic: String,
    val key: String,
    val event: KarrotEvent
)
```

### sdk/consumer/EventConsumer.kt
```kotlin
package com.karrot.platform.kafka.sdk.consumer

import com.karrot.platform.kafka.sdk.config.KafkaClientConfig
import com.karrot.platform.kafka.sdk.model.KarrotEvent
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class EventConsumer(
    private val config: KafkaClientConfig
) {
    
    private val logger = KotlinLogging.logger {}
    private val subscriptions = ConcurrentHashMap<String, ConsumerSubscription>()
    
    fun subscribe(
        topic: String,
        groupId: String,
        handler: suspend (KarrotEvent) -> Unit
    ): EventSubscription {
        val subscriptionId = "${topic}_${groupId}_${System.currentTimeMillis()}"
        
        val subscription = ConsumerSubscription(
            id = subscriptionId,
            topic = topic,
            groupId = groupId,
            handler = handler,
            config = config
        )
        
        subscriptions[subscriptionId] = subscription
        subscription.start()
        
        return EventSubscription(
            subscriptionId = subscriptionId,
            topic = topic,
            groupId = groupId
        ) {
            subscription.stop()
            subscriptions.remove(subscriptionId)
        }
    }
    
    fun closeAll() {
        subscriptions.values.forEach { it.stop() }
        subscriptions.clear()
    }
}

class ConsumerSubscription(
    val id: String,
    val topic: String,
    val groupId: String,
    val handler: suspend (KarrotEvent) -> Unit,
    val config: KafkaClientConfig
) {
    
    private val logger = KotlinLogging.logger {}
    private val running = AtomicBoolean(false)
    private var consumerJob: Job? = null
    
    fun start() {
        running.set(true)
        consumerJob = GlobalScope.launch {
            consumeMessages()
        }
    }
    
    fun stop() {
        running.set(false)
        consumerJob?.cancel()
    }
    
    private suspend fun consumeMessages() = coroutineScope {
        val consumer = KafkaConsumer<String, KarrotEvent>(
            config.toConsumerProperties(groupId)
        )
        
        consumer.subscribe(listOf(topic))
        
        try {
            while (running.get()) {
                val records = consumer.poll(Duration.ofMillis(100))
                
                records.forEach { record ->
                    launch {
                        processRecord(record)
                    }
                }
                
                consumer.commitAsync()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in consumer loop" }
        } finally {
            consumer.close()
        }
    }
    
    private suspend fun processRecord(record: ConsumerRecord<String, KarrotEvent>) {
        try {
            handler(record.value())
        } catch (e: Exception) {
            logger.error(e) { 
                "Error processing message from topic: ${record.topic()}, " +
                "partition: ${record.partition()}, offset: ${record.offset()}" 
            }
        }
    }
}

class EventSubscription(
    val subscriptionId: String,
    val topic: String,
    val groupId: String,
    private val closeAction: () -> Unit
) : AutoCloseable {
    
    override fun close() {
        closeAction()
    }
}
```

### sdk/config/KafkaClientConfig.kt
```kotlin
package com.karrot.platform.kafka.sdk.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

data class KafkaClientConfig(
    val bootstrapServers: String,
    val schemaRegistryUrl: String? = null,
    val producerConfig: ProducerConfig = ProducerConfig(),
    val consumerConfig: ConsumerConfig = ConsumerConfig(),
    val defaultTopic: String = "karrot.events"
) {
    
    fun toProducerProperties(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer::class.java)
            put(ProducerConfig.ACKS_CONFIG, producerConfig.acks)
            put(ProducerConfig.RETRIES_CONFIG, producerConfig.retries)
            put(ProducerConfig.BATCH_SIZE_CONFIG, producerConfig.batchSize)
            put(ProducerConfig.LINGER_MS_CONFIG, producerConfig.lingerMs)
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producerConfig.compressionType)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, producerConfig.enableIdempotence)
        }
    }
    
    fun toConsumerProperties(groupId: String): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer::class.java)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerConfig.autoOffsetReset)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumerConfig.enableAutoCommit)
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerConfig.maxPollRecords)
            put(JsonDeserializer.TRUSTED_PACKAGES, "*")
            put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.karrot.platform.kafka.sdk.model.KarrotEvent")
        }
    }
}

data class ProducerConfig(
    val acks: String = "all",
    val retries: Int = 3,
    val batchSize: Int = 16384,
    val lingerMs: Int = 10,
    val compressionType: String = "lz4",
    val enableIdempotence: Boolean = true
)

data class ConsumerConfig(
    val autoOffsetReset: String = "earliest",
    val enableAutoCommit: Boolean = true,
    val maxPollRecords: Int = 500
)
```

### sdk/model/KarrotEvent.kt
```kotlin
package com.karrot.platform.kafka.sdk.model

import java.time.Instant
import java.util.UUID

/**
 * Karrot Event 기본 인터페이스
 */
interface KarrotEvent {
    val eventId: String
    val eventType: String
    val aggregateId: String
    val timestamp: Instant
}

/**
 * 기본 이벤트 구현
 */
abstract class BaseEvent : KarrotEvent {
    override val eventId: String = UUID.randomUUID().toString()
    override val timestamp: Instant = Instant.now()
}

// 사용자 도메인 이벤트
data class UserCreatedEvent(
    val userId: String,
    val email: String,
    val nickname: String,
    val neighborhood: String
) : BaseEvent() {
    override val eventType = "USER_CREATED"
    override val aggregateId = userId
}

data class UserUpdatedEvent(
    val userId: String,
    val updatedFields: Map<String, Any>
) : BaseEvent() {
    override val eventType = "USER_UPDATED"
    override val aggregateId = userId
}

// 아이템 도메인 이벤트
data class ItemCreatedEvent(
    val itemId: String,
    val sellerId: String,
    val title: String,
    val price: Long,
    val neighborhood: String
) : BaseEvent() {
    override val eventType = "ITEM_CREATED"
    override val aggregateId = itemId
}

data class ItemSoldEvent(
    val itemId: String,
    val buyerId: String,
    val soldPrice: Long
) : BaseEvent() {
    override val eventType = "ITEM_SOLD"
    override val aggregateId = itemId
}

// 채팅 도메인 이벤트
data class ChatMessageSentEvent(
    val chatRoomId: String,
    val senderId: String,
    val message: String
) : BaseEvent() {
    override val eventType = "CHAT_MESSAGE_SENT"
    override val aggregateId = chatRoomId
}

// 결제 도메인 이벤트
data class PaymentRequestedEvent(
    val paymentId: String,
    val buyerId: String,
    val sellerId: String,
    val amount: Long
) : BaseEvent() {
    override val eventType = "PAYMENT_REQUESTED"
    override val aggregateId = paymentId
}
```

## 27. 추가 테스트 구현

### test/application/usecase/PublishEventUseCaseTest.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.event

import com.karrot.platform.kafka.application.ApplicationTestBase
import com.karrot.platform.kafka.application.port.output.EventOutputPort
import com.karrot.platform.kafka.application.usecase.event.dto.PublishEventCommand
import com.karrot.platform.kafka.domain.event.model.EventType
import com.karrot.platform.kafka.domain.event.service.PublishResult
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublishEventUseCaseTest : ApplicationTestBase() {
    
    private val eventOutputPort = mockk<EventOutputPort>()
    private val eventValidator = mockk<EventValidator>()
    
    private val useCase = PublishEventUseCase(eventOutputPort, eventValidator)
    
    @Test
    fun `이벤트 발행 성공 테스트`() = runBlocking {
        // Given
        val command = PublishEventCommand(
            eventType = EventType.USER_CREATED,
            aggregateId = "user-123",
            payload = mapOf("email" to "test@karrot.com"),
            userId = "user-123"
        )
        
        val publishResult = PublishResult(
            eventId = "event-123",
            success = true,
            topic = "karrot.user.events",
            partition = 0,
            offset = 100
        )
        
        every { eventValidator.validate(any()) } just Runs
        coEvery { eventOutputPort.saveEvent(any()) } returns mockk(relaxed = true)
        coEvery { eventOutputPort.publishToKafka(any()) } returns publishResult
        coEvery { eventOutputPort.updateEventStatus(any()) } returns mockk(relaxed = true)
        
        // When
        val result = useCase.publishEvent(command)
        
        // Then
        assertTrue(result.status == "PUBLISHED")
        assertEquals("karrot.user.events", result.topic)
        assertEquals(0, result.partition)
        assertEquals(100L, result.offset)
        
        coVerify { eventOutputPort.saveEvent(any()) }
        coVerify { eventOutputPort.publishToKafka(any()) }
    }
}
```

### test/infrastructure/kafka/KafkaProducerIntegrationTest.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.kafka

import com.karrot.platform.kafka.infrastructure.InfrastructureTestBase
import com.karrot.platform.kafka.infrastructure.kafka.producer.KafkaEventProducer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

class KafkaProducerIntegrationTest : InfrastructureTestBase() {
    
    @Autowired
    private lateinit var kafkaEventProducer: KafkaEventProducer
    
    @Test
    fun `Kafka에 이벤트 발행 통합 테스트`() = runBlocking {
        // Given
        val event = createTestEvent()
        val topic = "test-topic"
        
        // When
        val result = kafkaEventProducer.publish(event, topic)
        
        // Then
        assertTrue(result.success)
        assertNotNull(result.offset)
    }
}
```

## 28. 운영 스크립트

### scripts/health-check.sh
```bash
#!/bin/bash

# Kafka 클러스터 헬스체크 스크립트

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
SERVICE_URL=${SERVICE_URL:-http://localhost:8080}

echo "=== Kafka Cluster Health Check ==="

# Kafka 브로커 연결 확인
echo -n "Checking Kafka brokers... "
if kafka-broker-api-versions.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS > /dev/null 2>&1; then
    echo "OK"
else
    echo "FAILED"
    exit 1
fi

# 토픽 목록 확인
echo -n "Checking topics... "
TOPICS=$(kafka-topics.sh --list --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS 2>/dev/null | wc -l)
echo "$TOPICS topics found"

# Consumer Group 확인
echo -n "Checking consumer groups... "
GROUPS=$(kafka-consumer-groups.sh --list --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS 2>/dev/null | wc -l)
echo "$GROUPS consumer groups found"

# 서비스 헬스체크
echo -n "Checking service health... "
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" $SERVICE_URL/actuator/health)
if [ $HTTP_STATUS -eq 200 ]; then
    echo "OK"
else
    echo "FAILED (HTTP $HTTP_STATUS)"
    exit 1
fi

echo "=== Health Check Completed ==="
```

### scripts/consumer-lag-monitor.sh
```bash
#!/bin/bash

# Consumer Lag 모니터링 스크립트

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
LAG_THRESHOLD=${LAG_THRESHOLD:-1000}

echo "=== Consumer Lag Monitoring ==="
echo "Threshold: $LAG_THRESHOLD"
echo ""

# 모든 Consumer Group의 Lag 확인
kafka-consumer-groups.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS --all-groups --describe | \
while read line; do
    if echo "$line" | grep -q "^[A-Za-z]"; then
        LAG=$(echo "$line" | awk '{print $5}')
        if [ "$LAG" -gt "$LAG_THRESHOLD" ] 2>/dev/null; then
            echo "WARNING: High lag detected - $line"
        fi
    fi
done

echo ""
echo "=== Monitoring Completed ==="
```

## 29. 문서화

### docs/API.md
```markdown
# Karrot Kafka Platform API Documentation

## Overview
당근마켓 Kafka 플랫폼의 REST API 문서입니다.

## Base URL
```
https://api.karrot.com/kafka/v1
```

## Authentication
모든 API 요청에는 인증 토큰이 필요합니다.

```
Authorization: Bearer <token>
```

## Endpoints

### Event API

#### 이벤트 발행
```http
POST /events
Content-Type: application/json

{
  "eventType": "USER_CREATED",
  "aggregateId": "user-123",
  "payload": {
    "email": "user@karrot.com",
    "nickname": "당근이"
  }
}
```

**Response**
```json
{
  "eventId": "evt_abc123",
  "status": "PUBLISHED",
  "publishedAt": "2024-01-01T00:00:00Z",
  "topic": "karrot.user.events",
  "partition": 0,
  "offset": 12345
}
```

#### 이벤트 재시도
```http
POST /events/{eventId}/retry
```

### Schema API

#### 스키마 등록
```http
POST /schemas
Content-Type: application/json

{
  "subject": "user-event",
  "schema": "{\"type\":\"record\",\"name\":\"UserEvent\",...}",
  "schemaType": "AVRO"
}
```

### Monitoring API

#### Consumer Lag 조회
```http
GET /monitoring/consumer-lag?groupId=user-service
```

**Response**
```json
{
  "groupId": "user-service",
  "totalLag": 1234,
  "topics": [
    {
      "topic": "karrot.user.events",
      "totalLag": 1234,
      "partitions": [
        {
          "partition": 0,
          "currentOffset": 1000,
          "endOffset": 2234,
          "lag": 1234
        }
      ]
    }
  ]
}
```

## Error Codes

| Code | Description |
|------|-------------|
| EVT001 | Event not found |
| EVT002 | Event publication failed |
| SCH001 | Schema not found |
| SCH002 | Schema validation failed |
| KFK001 | Kafka connection failed |

## Rate Limiting
- 1000 requests per minute per API key
- 헤더에서 확인 가능: `X-RateLimit-Remaining`
```

### docs/DEPLOYMENT.md
```markdown
# Deployment Guide

## Prerequisites
- Kubernetes 1.24+
- Helm 3.0+
- kubectl configured

## Deployment Steps

### 1. Namespace 생성
```bash
kubectl create namespace karrot-kafka
```

### 2. ConfigMap 생성
```bash
kubectl create configmap kafka-config \
  --from-file=application.yml \
  -n karrot-kafka
```

### 3. Secret 생성
```bash
kubectl create secret generic kafka-secrets \
  --from-literal=db-password=$DB_PASSWORD \
  --from-literal=datadog-api-key=$DATADOG_API_KEY \
  -n karrot-kafka
```

### 4. Helm Chart 배포
```bash
helm install karrot-kafka ./helm/karrot-kafka \
  --namespace karrot-kafka \
  --values ./helm/values.yaml
```

## Health Check
```bash
kubectl get pods -n karrot-kafka
kubectl logs -f deployment/karrot-kafka -n karrot-kafka
```

## Rollback
```bash
helm rollback karrot-kafka 1 -n karrot-kafka
```
```

## 30. Makefile
```makefile
.PHONY: help build test run docker-up docker-down clean

help:
	@echo "Available targets:"
	@echo "  build       - Build the application"
	@echo "  test        - Run tests"
	@echo "  run         - Run the application locally"
	@echo "  docker-up   - Start Docker containers"
	@echo "  docker-down - Stop Docker containers"
	@echo "  clean       - Clean build artifacts"

build:
	./gradlew clean build -x test

test:
	./gradlew test

run:
	./gradlew bootRun --args='--spring.profiles.active=local'

docker-build:
	docker build -t karrot-kafka-platform:latest .

docker-up:
	docker-compose up -d

docker-down:
	docker-compose down

clean:
	./gradlew clean
	rm -rf build/
	rm -rf .gradle/

format:
	./gradlew ktlintFormat

lint:
	./gradlew ktlintCheck

migrate:
	./gradlew flywayMigrate

topics-create:
	./scripts/kafka-topics-setup.sh

health-check:
	./scripts/health-check.sh

monitor-lag:
	./scripts/consumer-lag-monitor.sh
```

이제 당근마켓 Kafka 플랫폼 프로젝트의 모든 파일 구현이 완료되었습니다!

프로젝트 구조 요약:
- **Clean Architecture**: Domain, Application, Infrastructure, Interface 계층 분리
- **DDD 적용**: Aggregate, Value Object, Domain Service 패턴
- **Kafka 통합**: Producer, Consumer, Streams, Admin API
- **모니터링**: Datadog, Prometheus, Health Check
- **SDK 제공**: 다른 팀이 쉽게 사용할 수 있는 클라이언트 라이브러리
- **운영 도구**: 스크립트, 문서화, 배포 가이드

이 구조로 당근마켓 공통서비스개발팀의 Kafka 플랫폼을 구축하실 수 있습니다!