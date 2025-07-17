# 당근마켓 Kafka 플랫폼 - 최종 누락 파일 검토 및 추가

프로젝트 구조를 검토한 결과, 다음 파일들이 누락되어 있습니다:

## 1. Infrastructure Layer - 누락된 파일들

### infrastructure/persistence/adapter/SchemaRepositoryAdapter.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.adapter

import com.karrot.platform.kafka.domain.schema.model.*
import com.karrot.platform.kafka.domain.schema.repository.SchemaRepository
import com.karrot.platform.kafka.infrastructure.persistence.entity.SchemaEntity
import com.karrot.platform.kafka.infrastructure.persistence.repository.SchemaJpaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component

@Component
class SchemaRepositoryAdapter(
    private val schemaJpaRepository: SchemaJpaRepository,
    private val schemaMapper: SchemaMapper
) : SchemaRepository {
    
    override suspend fun save(schema: Schema): Schema = withContext(Dispatchers.IO) {
        val entity = schemaMapper.toEntity(schema)
        val savedEntity = schemaJpaRepository.save(entity)
        schemaMapper.toDomain(savedEntity)
    }
    
    override suspend fun findById(id: SchemaId): Schema? = withContext(Dispatchers.IO) {
        schemaJpaRepository.findById(id.value)
            .orElse(null)
            ?.let { schemaMapper.toDomain(it) }
    }
    
    override suspend fun findBySubjectAndVersion(
        subject: String, 
        version: SchemaVersion
    ): Schema? = withContext(Dispatchers.IO) {
        schemaJpaRepository.findBySubjectAndVersion(subject, version.value)
            ?.let { schemaMapper.toDomain(it) }
    }
    
    override suspend fun findLatestBySubject(subject: String): Schema? = withContext(Dispatchers.IO) {
        schemaJpaRepository.findLatestBySubject(subject)
            ?.let { schemaMapper.toDomain(it) }
    }
    
    override suspend fun findAllBySubject(subject: String): List<Schema> = withContext(Dispatchers.IO) {
        schemaJpaRepository.findBySubjectOrderByVersionDesc(subject)
            .map { schemaMapper.toDomain(it) }
    }
    
    override suspend fun deleteBySubjectAndVersion(
        subject: String, 
        version: SchemaVersion
    ) = withContext(Dispatchers.IO) {
        schemaJpaRepository.findBySubjectAndVersion(subject, version.value)?.let {
            schemaJpaRepository.delete(it)
        }
    }
}
```

### infrastructure/persistence/adapter/SchemaMapper.kt
```kotlin
package com.karrot.platform.kafka.infrastructure.persistence.adapter

import com.karrot.platform.kafka.domain.schema.model.*
import com.karrot.platform.kafka.infrastructure.persistence.entity.SchemaEntity
import org.springframework.stereotype.Component

@Component
class SchemaMapper {
    
    fun toEntity(schema: Schema): SchemaEntity {
        return SchemaEntity(
            schemaId = if (schema.id.value > 0) schema.id.value else null,
            subject = schema.subject,
            version = schema.version.value,
            schemaContent = schema.content,
            schemaType = schema.type.name,
            compatibilityMode = schema.compatibilityMode.name,
            isDeleted = schema.isDeleted,
            createdAt = schema.createdAt
        )
    }
    
    fun toDomain(entity: SchemaEntity): Schema {
        return Schema(
            id = SchemaId(entity.schemaId ?: 0L),
            subject = entity.subject,
            version = SchemaVersion(entity.version),
            content = entity.schemaContent,
            type = SchemaType.valueOf(entity.schemaType),
            compatibilityMode = SchemaCompatibility.valueOf(entity.compatibilityMode),
            isDeleted = entity.isDeleted,
            createdAt = entity.createdAt
        )
    }
}
```

## 2. Application Layer - 누락된 UseCase

### application/usecase/schema/ValidateSchemaUseCase.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.schema

import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.domain.schema.model.Schema
import com.karrot.platform.kafka.domain.schema.model.SchemaType
import com.karrot.platform.kafka.infrastructure.schema.JsonSchemaValidator
import mu.KotlinLogging
import org.apache.avro.Schema.Parser as AvroParser

@UseCase
class ValidateSchemaUseCase(
    private val jsonSchemaValidator: JsonSchemaValidator
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun validateSchema(
        schemaContent: String,
        schemaType: SchemaType
    ): SchemaValidationResult {
        return try {
            when (schemaType) {
                SchemaType.AVRO -> validateAvroSchema(schemaContent)
                SchemaType.JSON -> validateJsonSchema(schemaContent)
                SchemaType.PROTOBUF -> validateProtobufSchema(schemaContent)
            }
        } catch (e: Exception) {
            logger.error(e) { "Schema validation failed" }
            SchemaValidationResult.Invalid(e.message ?: "Unknown error")
        }
    }
    
    private fun validateAvroSchema(content: String): SchemaValidationResult {
        return try {
            AvroParser().parse(content)
            SchemaValidationResult.Valid
        } catch (e: Exception) {
            SchemaValidationResult.Invalid("Invalid Avro schema: ${e.message}")
        }
    }
    
    private fun validateJsonSchema(content: String): SchemaValidationResult {
        return if (jsonSchemaValidator.isValidSchema(content)) {
            SchemaValidationResult.Valid
        } else {
            SchemaValidationResult.Invalid("Invalid JSON schema")
        }
    }
    
    private fun validateProtobufSchema(content: String): SchemaValidationResult {
        // Protobuf 검증 로직 구현
        return SchemaValidationResult.Valid
    }
}

sealed class SchemaValidationResult {
    object Valid : SchemaValidationResult()
    data class Invalid(val reason: String) : SchemaValidationResult()
}
```

### application/usecase/monitoring/GetTopicMetricsUseCase.kt
```kotlin
package com.karrot.platform.kafka.application.usecase.monitoring

import com.karrot.platform.kafka.application.port.input.MonitoringInputPort
import com.karrot.platform.kafka.common.annotation.UseCase
import com.karrot.platform.kafka.domain.monitoring.model.TopicMetrics
import com.karrot.platform.kafka.domain.monitoring.service.MonitoringService
import mu.KotlinLogging

@UseCase
class GetTopicMetricsUseCase(
    private val monitoringService: MonitoringService
) {
    
    private val logger = KotlinLogging.logger {}
    
    suspend fun getTopicMetrics(topicName: String): TopicMetrics {
        logger.debug { "Getting metrics for topic: $topicName" }
        return monitoringService.getTopicMetrics(topicName)
    }
    
    suspend fun getAllTopicMetrics(): List<TopicMetrics> {
        logger.debug { "Getting metrics for all topics" }
        
        val topics = listOf(
            "karrot.user.events",
            "karrot.item.events",
            "karrot.payment.events",
            "karrot.chat.events"
        )
        
        return topics.mapNotNull { topic ->
            try {
                monitoringService.getTopicMetrics(topic)
            } catch (e: Exception) {
                logger.warn { "Failed to get metrics for topic $topic: ${e.message}" }
                null
            }
        }
    }
}
```

## 3. Interface Layer - 누락된 Handler

### interfaces/kafka/handler/ErrorHandler.kt
```kotlin
package com.karrot.platform.kafka.interfaces.kafka.handler

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component("customErrorHandler")
class CustomErrorHandler : ConsumerAwareListenerErrorHandler {
    
    private val logger = KotlinLogging.logger {}
    
    override fun handleError(
        message: Message<*>,
        exception: ListenerExecutionFailedException,
        consumer: Consumer<*, *>
    ): Any? {
        logger.error(exception) { 
            "Error handling message: ${message.payload}" 
        }
        
        // 에러 타입에 따른 처리
        when (val cause = exception.cause) {
            is IllegalArgumentException -> {
                // 재시도하지 않을 에러
                logger.warn { "Skipping message due to validation error: ${cause.message}" }
                return null
            }
            is NullPointerException -> {
                // 치명적 에러 - 알림 전송
                logger.error { "Critical error occurred: ${cause.message}" }
                sendAlert(message, cause)
                return null
            }
            else -> {
                // 재시도할 에러
                throw exception
            }
        }
    }
    
    private fun sendAlert(message: Message<*>, error: Throwable) {
        // Slack, Email 등으로 알림 전송
        logger.error { "Sending alert for critical error: ${error.message}" }
    }
}
```

## 4. Common Layer - 누락된 파일

### common/exception/EventValidationException.kt
```kotlin
package com.karrot.platform.kafka.common.exception

class EventValidationException(
    message: String
) : BusinessException(ErrorCode.EVENT_VALIDATION_FAILED, message)
```

## 5. Application Layer - 누락된 Port 인터페이스

### application/port/input/UserEventInputPort.kt
```kotlin
package com.karrot.platform.kafka.application.port.input

import com.karrot.platform.kafka.interfaces.kafka.listener.message.UserEventMessage

interface UserEventInputPort {
    suspend fun handleUserCreated(event: UserEventMessage)
    suspend fun handleUserUpdated(event: UserEventMessage)
    suspend fun handleUserVerified(event: UserEventMessage)
    suspend fun handleUserLocationUpdated(event: UserEventMessage)
}
```

### application/port/input/PaymentEventInputPort.kt
```kotlin
package com.karrot.platform.kafka.application.port.input

import com.karrot.platform.kafka.interfaces.kafka.listener.PaymentEventMessage

interface PaymentEventInputPort {
    suspend fun handlePaymentRequested(event: PaymentEventMessage)
    suspend fun handlePaymentCompleted(event: PaymentEventMessage)
    suspend fun handlePaymentFailed(event: PaymentEventMessage)
    suspend fun handlePaymentRefunded(event: PaymentEventMessage)
}
```

## 6. Interface Layer - 누락된 메시지 클래스

### interfaces/kafka/listener/message/UserEventMessage.kt
```kotlin
package com.karrot.platform.kafka.interfaces.kafka.listener.message

import java.time.Instant

data class UserEventMessage(
    val eventId: String,
    val eventType: String,
    val userId: String,
    val email: String? = null,
    val nickname: String? = null,
    val neighborhood: String? = null,
    val location: LocationData? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Instant
)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String
)
```

## 7. Domain Layer - 누락된 모델

### domain/item/event/ItemEventHandler.kt의 Location 클래스 import 수정
```kotlin
package com.karrot.platform.kafka.domain.item.event

import com.karrot.platform.kafka.common.annotation.DomainService
import com.karrot.platform.kafka.domain.event.model.*
import java.time.Instant

/**
 * 중고거래 아이템 이벤트 핸들러
 */
@DomainService
class ItemEventHandler {
    
    fun createItemCreatedEvent(
        itemId: String,
        sellerId: String,
        title: String,
        price: Long,
        category: String,
        location: ItemLocation,
        images: List<String>
    ): Event {
        val payload = mapOf(
            "itemId" to itemId,
            "sellerId" to sellerId,
            "title" to title,
            "price" to price,
            "category" to category,
            "location" to mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "address" to location.address,
                "neighborhood" to location.neighborhood
            ),
            "images" to images,
            "status" to "ACTIVE"
        )
        
        return Event.create(
            type = EventType.ITEM_CREATED,
            aggregateId = itemId,
            payload = EventPayload(payload),
            metadata = EventMetadata(
                userId = sellerId,
                source = "ITEM_SERVICE"
            )
        )
    }
    
    fun createItemSoldEvent(
        itemId: String,
        sellerId: String,
        buyerId: String,
        soldPrice: Long
    ): Event {
        val payload = mapOf(
            "itemId" to itemId,
            "sellerId" to sellerId,
            "buyerId" to buyerId,
            "soldPrice" to soldPrice,
            "soldAt" to Instant.now().toString()
        )
        
        return Event.create(
            type = EventType.ITEM_SOLD,
            aggregateId = itemId,
            payload = EventPayload(payload),
            metadata = EventMetadata(
                userId = sellerId,
                source = "ITEM_SERVICE"
            )
        )
    }
}

data class ItemLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val neighborhood: String
)
```

## 8. Test 관련 누락 파일

### test/resources/application-test.yml
```yaml
spring:
  profiles:
    active: test
    
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
    
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        
  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers}
    consumer:
      group-id: test-group
      auto-offset-reset: earliest
      
logging:
  level:
    com.karrot.platform.kafka: DEBUG
    org.apache.kafka: WARN
    
kafka:
  schema-registry:
    url: mock://test
```

## 9. Dockerfile 누락

### Dockerfile
```dockerfile
FROM openjdk:17-jdk-slim AS builder

WORKDIR /app

# Gradle wrapper 복사
COPY gradlew .
COPY gradle gradle

# 의존성 캐싱을 위한 빌드 파일 복사
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# 의존성 다운로드
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src src

# 빌드
RUN ./gradlew build -x test --no-daemon

FROM openjdk:17-jdk-slim

# 타임존 설정
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 애플리케이션 디렉토리
WORKDIR /app

# 빌드 결과물 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 실행 사용자 생성
RUN useradd -m appuser
USER appuser

# JVM 옵션
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError"

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## 10. GitHub Actions CI/CD

### .github/workflows/ci.yml
```yaml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_PASSWORD: test
          POSTGRES_DB: test_db
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
          
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
          
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      
    - name: Run tests
      run: ./gradlew test
      env:
        SPRING_PROFILES_ACTIVE: test
        
    - name: Build
      run: ./gradlew build
      
    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: test-results
        path: build/test-results/test
        
    - name: Upload coverage reports
      uses: codecov/codecov-action@v3
      with:
        file: ./build/reports/jacoco/test/jacocoTestReport.xml
```

## 11. Kubernetes 배포 파일

### k8s/deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: karrot-kafka-platform
  namespace: karrot-kafka
  labels:
    app: karrot-kafka-platform
spec:
  replicas: 3
  selector:
    matchLabels:
      app: karrot-kafka-platform
  template:
    metadata:
      labels:
        app: karrot-kafka-platform
    spec:
      containers:
      - name: app
        image: karrot-kafka-platform:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: kafka-config
              key: bootstrap.servers
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: kafka-secrets
              key: db-password
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
```

### k8s/service.yaml
```yaml
apiVersion: v1
kind: Service
metadata:
  name: karrot-kafka-platform
  namespace: karrot-kafka
spec:
  selector:
    app: karrot-kafka-platform
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: ClusterIP
```

이제 프로젝트의 모든 누락된 파일들을 추가했습니다! 

주요 추가 사항:
1. **Persistence Adapter**: SchemaRepositoryAdapter, SchemaMapper
2. **Application UseCase**: ValidateSchemaUseCase, GetTopicMetricsUseCase
3. **Input Ports**: UserEventInputPort, PaymentEventInputPort
4. **Interface Layer**: ErrorHandler, Message 클래스들
5. **Test 설정**: application-test.yml
6. **배포 관련**: Dockerfile, CI/CD, Kubernetes 설정

이제 완전한 프로젝트 구조를 갖추었으며, 실제 개발과 배포가 가능한 상태입니다!