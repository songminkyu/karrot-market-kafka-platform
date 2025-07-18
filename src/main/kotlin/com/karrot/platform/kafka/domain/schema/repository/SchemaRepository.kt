package com.karrot.platform.kafka.domain.schema.repository

import com.karrot.platform.kafka.domain.schema.model.Schema
import com.karrot.platform.kafka.domain.schema.model.SchemaId
import com.karrot.platform.kafka.domain.schema.model.SchemaVersion

interface SchemaRepository {
    suspend fun save(schema: Schema): Schema
    suspend fun findById(id: SchemaId): Schema?
    suspend fun findBySubjectAndVersion(subject: String, version: SchemaVersion): Schema?
    suspend fun findLatestBySubject(subject: String): Schema?
    suspend fun findAllBySubject(subject: String): List<Schema>
    suspend fun deleteBySubjectAndVersion(subject: String, version: SchemaVersion)
}