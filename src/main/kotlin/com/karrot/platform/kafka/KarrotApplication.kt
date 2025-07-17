package com.karrot.platform.kafka

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KarrotApplication

fun main(args: Array<String>) {
	runApplication<KarrotApplication>(*args)
}
