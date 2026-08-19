package dev.dispatch.dispatch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DispatchApplication

fun main(args: Array<String>) {
    runApplication<DispatchApplication>(*args)
}
