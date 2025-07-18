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
