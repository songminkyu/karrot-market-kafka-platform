package com.karrot.platform.kafka.common.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * JSON 유틸리티
 */
object JsonUtils {

    val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
    }

    val dataTypeRef = object : TypeReference<Map<String, Any>>() {}

    fun toJson(obj: Any): String = objectMapper.writeValueAsString(obj)

    inline fun <reified T> fromJson(json: String): T = objectMapper.readValue(json)

    fun toMap(obj: Any): Map<String, Any> {
        return objectMapper.convertValue(obj, Map::class.java) as Map<String, Any>
    }
}
