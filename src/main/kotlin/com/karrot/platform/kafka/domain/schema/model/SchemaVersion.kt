package com.karrot.platform.kafka.domain.schema.model

/**
 * 스키마 버전 Value Object
 */
@JvmInline
value class SchemaVersion(val value: Int) {
    init {
        require(value > 0) { "Schema version must be positive" }
        require(value <= 999999) { "Schema version must not exceed 999999" }
    }

    companion object {
        val INITIAL = SchemaVersion(1)

        fun of(value: Int) = SchemaVersion(value)

        fun parse(versionString: String): SchemaVersion {
            val version = versionString.removePrefix("v").toIntOrNull()
                ?: throw IllegalArgumentException("Invalid version format: $versionString")
            return SchemaVersion(version)
        }
    }

    fun next(): SchemaVersion = SchemaVersion(value + 1)

    fun previous(): SchemaVersion? = if (value > 1) SchemaVersion(value - 1) else null

    fun isInitial(): Boolean = value == 1

    fun isNewerThan(other: SchemaVersion): Boolean = value > other.value

    fun isOlderThan(other: SchemaVersion): Boolean = value < other.value

    override fun toString(): String = "v$value"

    fun toInt(): Int = value
}
