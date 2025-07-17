package com.karrot.platform.kafka.common.annotation
import org.springframework.stereotype.Service

/**
 * 유스케이스를 나타내는 어노테이션
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class UseCase
