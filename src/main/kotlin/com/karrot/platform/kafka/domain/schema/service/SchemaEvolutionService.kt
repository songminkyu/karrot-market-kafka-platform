package com.karrot.platform.kafka.domain.schema.service

import com.karrot.platform.kafka.common.annotation.DomainService
import com.karrot.platform.kafka.domain.schema.model.*
import mu.KotlinLogging

@DomainService
class SchemaEvolutionService {

    private val logger = KotlinLogging.logger {}

    fun checkCompatibility(
        currentSchema: Schema,
        newSchemaContent: String,
        compatibilityMode: SchemaCompatibility
    ): CompatibilityResult {
        logger.info {
            "Checking compatibility for subject: ${currentSchema.subject}, " +
                    "mode: $compatibilityMode"
        }

        return try {
            val result = compatibilityMode.validate(currentSchema,
                Schema.create(
                    subject = currentSchema.subject,
                    content = newSchemaContent,
                    type = currentSchema.type,
                    compatibilityMode = compatibilityMode
                )
            )

            if (result.isCompatible()) {
                logger.info { "Schema is compatible" }
            } else {
                logger.warn { "Schema is incompatible: ${result.getErrorMessage()}" }
            }

            result
        } catch (e: Exception) {
            logger.error(e) { "Error checking compatibility" }
            CompatibilityResult.Incompatible(e.message ?: "Unknown error")
        }
    }

    fun evolveSchema(
        currentSchema: Schema,
        newContent: String
    ): Schema {
        val compatibilityResult = checkCompatibility(
            currentSchema,
            newContent,
            currentSchema.compatibilityMode
        )

        if (!compatibilityResult.isCompatible()) {
            throw SchemaEvolutionException(
                "Schema evolution failed: ${compatibilityResult.getErrorMessage()}"
            )
        }

        return Schema.createNewVersion(currentSchema, newContent)
    }

    fun getEvolutionPath(
        schemas: List<Schema>
    ): List<SchemaEvolution> {
        return schemas.sortedBy { it.version.value }
            .zipWithNext { previous, current ->
                SchemaEvolution(
                    fromVersion = previous.version,
                    toVersion = current.version,
                    changes = detectChanges(previous, current)
                )
            }
    }

    private fun detectChanges(previous: Schema, current: Schema): List<SchemaChange> {
        // 실제 구현에서는 스키마 파싱하여 변경사항 감지
        return emptyList()
    }
}

data class SchemaEvolution(
    val fromVersion: SchemaVersion,
    val toVersion: SchemaVersion,
    val changes: List<SchemaChange>
)

sealed class SchemaChange {
    data class FieldAdded(val fieldName: String, val fieldType: String) : SchemaChange()
    data class FieldRemoved(val fieldName: String) : SchemaChange()
    data class FieldTypeChanged(val fieldName: String, val oldType: String, val newType: String) : SchemaChange()
}

class SchemaEvolutionException(message: String) : RuntimeException(message)