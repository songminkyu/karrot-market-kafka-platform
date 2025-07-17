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
