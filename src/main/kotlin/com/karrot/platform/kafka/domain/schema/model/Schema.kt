package com.karrot.platform.kafka.domain.schema.model

import java.time.Instant

/**
 * 스키마 도메인 모델 - Aggregate Root
 */
class Schema private constructor(
    val id: SchemaId,
    val subject: String,
    val version: SchemaVersion,
    val content: String,
    val type: SchemaType,
    var compatibilityMode: SchemaCompatibility,
    private var status: SchemaStatus,
    val fingerprint: String,
    val createdAt: Instant,
    private var updatedAt: Instant,
    private var deletedAt: Instant? = null
) {

    companion object {
        fun create(
            subject: String,
            content: String,
            type: SchemaType = SchemaType.AVRO,
            compatibilityMode: SchemaCompatibility = SchemaCompatibility.BACKWARD
        ): Schema {
            validateSubject(subject)
            validateContent(content, type)

            return Schema(
                id = SchemaId.generate(),
                subject = subject,
                version = SchemaVersion.INITIAL,
                content = content,
                type = type,
                compatibilityMode = compatibilityMode,
                status = SchemaStatus.ACTIVE,
                fingerprint = generateFingerprint(content),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }

        fun createNewVersion(
            previous: Schema,
            newContent: String
        ): Schema {
            require(previous.status == SchemaStatus.ACTIVE) {
                "Cannot create new version from ${previous.status} schema"
            }

            validateContent(newContent, previous.type)

            return Schema(
                id = SchemaId.generate(),
                subject = previous.subject,
                version = previous.version.next(),
                content = newContent,
                type = previous.type,
                compatibilityMode = previous.compatibilityMode,
                status = SchemaStatus.ACTIVE,
                fingerprint = generateFingerprint(newContent),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }

        private fun validateSubject(subject: String) {
            require(subject.isNotBlank()) { "Subject cannot be blank" }
            require(subject.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
                "Subject must contain only alphanumeric characters, dots, underscores, and hyphens"
            }
            require(subject.length <= 255) { "Subject must not exceed 255 characters" }
        }

        private fun validateContent(content: String, type: SchemaType) {
            require(content.isNotBlank()) { "Schema content cannot be blank" }

            when (type) {
                SchemaType.AVRO -> validateAvroSchema(content)
                SchemaType.JSON -> validateJsonSchema(content)
                SchemaType.PROTOBUF -> validateProtobufSchema(content)
            }
        }

        private fun validateAvroSchema(content: String) {
            // Avro 스키마 검증 로직
            try {
                org.apache.avro.Schema.Parser().parse(content)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Avro schema: ${e.message}")
            }
        }

        private fun validateJsonSchema(content: String) {
            // JSON 스키마 검증 로직
            try {
                com.fasterxml.jackson.databind.ObjectMapper().readTree(content)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON schema: ${e.message}")
            }
        }

        private fun validateProtobufSchema(content: String) {
            // Protobuf 스키마 검증 로직
            // 실제 구현에서는 protobuf 컴파일러를 사용
        }

        private fun generateFingerprint(content: String): String {
            // SHA-256 해시를 사용한 지문 생성
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(content.toByteArray())
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    fun getStatus(): SchemaStatus = status
    fun getUpdatedAt(): Instant = updatedAt
    fun getDeletedAt(): Instant? = deletedAt
    fun isDeleted(): Boolean = status == SchemaStatus.DELETED

    fun deprecate() {
        require(status == SchemaStatus.ACTIVE) {
            "Only active schemas can be deprecated"
        }
        this.status = SchemaStatus.DEPRECATED
        this.updatedAt = Instant.now()
    }

    fun delete() {
        require(status != SchemaStatus.DELETED) {
            "Schema is already deleted"
        }
        this.status = SchemaStatus.DELETED
        this.deletedAt = Instant.now()
        this.updatedAt = Instant.now()
    }

    fun restore() {
        require(status == SchemaStatus.DELETED) {
            "Only deleted schemas can be restored"
        }
        this.status = SchemaStatus.ACTIVE
        this.deletedAt = null
        this.updatedAt = Instant.now()
    }

    fun updateCompatibilityMode(newMode: SchemaCompatibility) {
        require(status == SchemaStatus.ACTIVE) {
            "Only active schemas can have compatibility mode updated"
        }
        this.compatibilityMode = newMode
        this.updatedAt = Instant.now()
    }

    fun isCompatibleWith(other: Schema): Boolean {
        return subject == other.subject && type == other.type
    }

    fun toSchemaString(): String {
        return when (type) {
            SchemaType.AVRO -> formatAvroSchema()
            SchemaType.JSON -> formatJsonSchema()
            SchemaType.PROTOBUF -> content
        }
    }

    private fun formatAvroSchema(): String {
        // Avro 스키마 포맷팅
        return try {
            val schema = org.apache.avro.Schema.Parser().parse(content)
            schema.toString(true)
        } catch (e: Exception) {
            content
        }
    }

    private fun formatJsonSchema(): String {
        // JSON 스키마 포맷팅
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val json = mapper.readTree(content)
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)
        } catch (e: Exception) {
            content
        }
    }
}

/**
 * 스키마 ID - Value Object
 */
@JvmInline
value class SchemaId(val value: Long) {
    init {
        require(value >= 0) { "SchemaId must be non-negative" }
    }

    companion object {
        private var counter = System.currentTimeMillis()

        fun generate(): SchemaId = SchemaId(++counter)
        fun of(value: Long): SchemaId = SchemaId(value)
    }
}

/**
 * 스키마 타입
 */
enum class SchemaType {
    AVRO,
    JSON,
    PROTOBUF;

    fun getFileExtension(): String {
        return when (this) {
            AVRO -> ".avsc"
            JSON -> ".json"
            PROTOBUF -> ".proto"
        }
    }

    fun getMimeType(): String {
        return when (this) {
            AVRO -> "application/vnd.apache.avro+json"
            JSON -> "application/schema+json"
            PROTOBUF -> "application/x-protobuf"
        }
    }
}

/**
 * 스키마 상태
 */
enum class SchemaStatus {
    ACTIVE,      // 활성 상태
    DEPRECATED,  // 지원 중단 예정
    DELETED;     // 삭제됨

    fun canTransitionTo(newStatus: SchemaStatus): Boolean {
        return when (this) {
            ACTIVE -> newStatus in listOf(DEPRECATED, DELETED)
            DEPRECATED -> newStatus == DELETED
            DELETED -> newStatus == ACTIVE // 복원 가능
        }
    }
}