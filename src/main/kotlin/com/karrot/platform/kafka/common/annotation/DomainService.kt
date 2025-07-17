package com.karrot.platform.kafka.common.annotation

import org.springframework.stereotype.Component

/**
 * 도메인 서비스를 나타내는 어노테이션
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
annotation class DomainService