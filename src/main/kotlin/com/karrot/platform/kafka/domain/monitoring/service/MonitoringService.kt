package com.karrot.platform.kafka.domain.monitoring.service

import com.karrot.platform.kafka.common.annotation.DomainService
import com.karrot.platform.kafka.domain.monitoring.model.ConsumerLag
import com.karrot.platform.kafka.domain.monitoring.model.PartitionLag
import com.karrot.platform.kafka.domain.monitoring.model.TopicLag
import com.karrot.platform.kafka.domain.monitoring.model.TopicMetrics
import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.springframework.beans.factory.annotation.Autowired


@DomainService
class MonitoringService(
    @Autowired
    private val adminClient: AdminClient
) {

    private val logger = KotlinLogging.logger {}

    suspend fun getConsumerLag(groupId: String): ConsumerLag {
        try {
            val groupDescription = adminClient.describeConsumerGroups(listOf(groupId))
                .describedGroups()[groupId]?.get()
                ?: throw IllegalArgumentException("Consumer group not found: $groupId")

            val offsets = adminClient.listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata()
                .get()

            val topicLags = offsets.entries
                .groupBy { it.key.topic() }
                .map { (topic, partitions) ->
                    val partitionLags = partitions.map { (topicPartition, offsetMetadata) ->
                        val endOffset = getEndOffset(topicPartition)
                        val currentOffset = offsetMetadata.offset()
                        val lag = endOffset - currentOffset

                        PartitionLag(
                            partition = topicPartition.partition(),
                            currentOffset = currentOffset,
                            endOffset = endOffset,
                            lag = lag
                        )
                    }

                    TopicLag(
                        topic = topic,
                        totalLag = partitionLags.sumOf { it.lag },
                        partitionLags = partitionLags
                    )
                }

            return ConsumerLag(
                groupId = groupId,
                totalLag = topicLags.sumOf { it.totalLag },
                topicLags = topicLags
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get consumer lag for group: $groupId" }
            throw MonitoringException("Failed to get consumer lag", e)
        }
    }

    suspend fun getAllConsumerLags(): List<ConsumerLag> {
        return try {
            val groups = adminClient.listConsumerGroups().all().get()
            groups.mapNotNull { group ->
                try {
                    getConsumerLag(group.groupId())
                } catch (e: Exception) {
                    logger.warn { "Failed to get lag for group ${group.groupId()}: ${e.message}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get all consumer lags" }
            emptyList()
        }
    }

    suspend fun getTopicMetrics(topicName: String): TopicMetrics {
        try {
            val topicDescription = adminClient.describeTopics(listOf(topicName))
                .values()[topicName]?.get()
                ?: throw IllegalArgumentException("Topic not found: $topicName")

            // 실제로는 JMX 메트릭을 읽어와야 함
            return TopicMetrics(
                topicName = topicName,
                partitionCount = topicDescription.partitions().size,
                replicationFactor = topicDescription.partitions()[0].replicas().size,
                messageRate = 1000.0, // 실제 메트릭으로 대체
                bytesInPerSec = 1024 * 1024.0, // 실제 메트릭으로 대체
                bytesOutPerSec = 1024 * 1024.0, // 실제 메트릭으로 대체
                totalMessages = 1000000L // 실제 메트릭으로 대체
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get topic metrics: $topicName" }
            throw MonitoringException("Failed to get topic metrics", e)
        }
    }

    private fun getEndOffset(topicPartition: org.apache.kafka.common.TopicPartition): Long {
        val endOffsets = adminClient.listOffsets(
            mapOf(topicPartition to org.apache.kafka.clients.admin.OffsetSpec.latest())
        ).all().get()

        return endOffsets[topicPartition]?.offset() ?: 0L
    }
}

class MonitoringException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
