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