package com.karrot.platform.kafka.domain.schema.model

/**
 * 스키마 호환성 모드
 */
enum class SchemaCompatibility {
    NONE,                    // 호환성 검사 안함
    BACKWARD,                // 새 스키마가 이전 스키마로 작성된 데이터를 읽을 수 있음
    FORWARD,                 // 이전 스키마가 새 스키마로 작성된 데이터를 읽을 수 있음
    FULL,                    // BACKWARD + FORWARD
    BACKWARD_TRANSITIVE,     // 모든 이전 버전과 BACKWARD 호환
    FORWARD_TRANSITIVE,      // 모든 이전 버전과 FORWARD 호환
    FULL_TRANSITIVE;         // 모든 이전 버전과 FULL 호환

    fun isBackwardCompatible(): Boolean {
        return this in listOf(BACKWARD, FULL, BACKWARD_TRANSITIVE, FULL_TRANSITIVE)
    }

    fun isForwardCompatible(): Boolean {
        return this in listOf(FORWARD, FULL, FORWARD_TRANSITIVE, FULL_TRANSITIVE)
    }

    fun isTransitive(): Boolean {
        return this in listOf(BACKWARD_TRANSITIVE, FORWARD_TRANSITIVE, FULL_TRANSITIVE)
    }

    fun requiresAllVersionsCheck(): Boolean {
        return isTransitive()
    }

    fun getDescription(): String {
        return when (this) {
            NONE -> "No compatibility check"
            BACKWARD -> "Consumer can read data produced by previous version"
            FORWARD -> "Producer can write data readable by previous version"
            FULL -> "Both backward and forward compatible"
            BACKWARD_TRANSITIVE -> "Consumer can read data from all previous versions"
            FORWARD_TRANSITIVE -> "Producer can write data readable by all previous versions"
            FULL_TRANSITIVE -> "Fully compatible with all previous versions"
        }
    }

    fun validate(currentSchema: Schema, newSchema: Schema): CompatibilityResult {
        if (this == NONE) {
            return CompatibilityResult.Compatible
        }

        require(currentSchema.subject == newSchema.subject) {
            "Cannot check compatibility between different subjects"
        }

        require(currentSchema.type == newSchema.type) {
            "Cannot check compatibility between different schema types"
        }

        return when (this) {
            BACKWARD, BACKWARD_TRANSITIVE -> checkBackwardCompatibility(currentSchema, newSchema)
            FORWARD, FORWARD_TRANSITIVE -> checkForwardCompatibility(currentSchema, newSchema)
            FULL, FULL_TRANSITIVE -> checkFullCompatibility(currentSchema, newSchema)
            else -> CompatibilityResult.Compatible
        }
    }

    private fun checkBackwardCompatibility(
        currentSchema: Schema,
        newSchema: Schema
    ): CompatibilityResult {
        // 새 스키마로 이전 데이터를 읽을 수 있는지 검사
        return try {
            when (currentSchema.type) {
                SchemaType.AVRO -> checkAvroBackwardCompatibility(currentSchema.content, newSchema.content)
                SchemaType.JSON -> checkJsonBackwardCompatibility(currentSchema.content, newSchema.content)
                SchemaType.PROTOBUF -> checkProtobufBackwardCompatibility(currentSchema.content, newSchema.content)
            }
        } catch (e: Exception) {
            CompatibilityResult.Incompatible("Backward compatibility check failed: ${e.message}")
        }
    }

    private fun checkForwardCompatibility(
        currentSchema: Schema,
        newSchema: Schema
    ): CompatibilityResult {
        // 이전 스키마로 새 데이터를 읽을 수 있는지 검사
        return try {
            when (currentSchema.type) {
                SchemaType.AVRO -> checkAvroForwardCompatibility(currentSchema.content, newSchema.content)
                SchemaType.JSON -> checkJsonForwardCompatibility(currentSchema.content, newSchema.content)
                SchemaType.PROTOBUF -> checkProtobufForwardCompatibility(currentSchema.content, newSchema.content)
            }
        } catch (e: Exception) {
            CompatibilityResult.Incompatible("Forward compatibility check failed: ${e.message}")
        }
    }

    private fun checkFullCompatibility(
        currentSchema: Schema,
        newSchema: Schema
    ): CompatibilityResult {
        val backwardResult = checkBackwardCompatibility(currentSchema, newSchema)
        if (backwardResult is CompatibilityResult.Incompatible) {
            return backwardResult
        }

        val forwardResult = checkForwardCompatibility(currentSchema, newSchema)
        if (forwardResult is CompatibilityResult.Incompatible) {
            return forwardResult
        }

        return CompatibilityResult.Compatible
    }

    // 실제 호환성 검사 구현 (Avro)
    private fun checkAvroBackwardCompatibility(current: String, new: String): CompatibilityResult {
        val currentSchema = org.apache.avro.Schema.Parser().parse(current)
        val newSchema = org.apache.avro.Schema.Parser().parse(new)

        // Avro 호환성 규칙에 따른 검사
        // 1. 필드 제거는 기본값이 있는 경우만 허용
        // 2. 필드 추가는 기본값이 있어야 함
        // 3. 타입 변경은 호환 가능한 타입만 허용

        return CompatibilityResult.Compatible // 실제 구현 필요
    }

    private fun checkAvroForwardCompatibility(current: String, new: String): CompatibilityResult {
        // Forward 호환성 검사 로직
        return CompatibilityResult.Compatible
    }

    // JSON Schema 호환성 검사
    private fun checkJsonBackwardCompatibility(current: String, new: String): CompatibilityResult {
        // JSON Schema 호환성 검사 로직
        return CompatibilityResult.Compatible
    }

    private fun checkJsonForwardCompatibility(current: String, new: String): CompatibilityResult {
        // JSON Schema forward 호환성 검사 로직
        return CompatibilityResult.Compatible
    }

    // Protobuf 호환성 검사
    private fun checkProtobufBackwardCompatibility(current: String, new: String): CompatibilityResult {
        // Protobuf 호환성 검사 로직
        return CompatibilityResult.Compatible
    }

    private fun checkProtobufForwardCompatibility(current: String, new: String): CompatibilityResult {
        // Protobuf forward 호환성 검사 로직
        return CompatibilityResult.Compatible
    }
}

/**
 * 호환성 검사 결과
 */
sealed class CompatibilityResult {
    object Compatible : CompatibilityResult()
    data class Incompatible(val reason: String) : CompatibilityResult()

    fun isCompatible(): Boolean = this is Compatible

    fun getErrorMessage(): String? = when (this) {
        is Compatible -> null
        is Incompatible -> reason
    }
}