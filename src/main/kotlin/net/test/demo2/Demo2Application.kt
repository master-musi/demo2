package net.test.demo2

import net.test.demo2.config.PayPalProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@EnableConfigurationProperties(PayPalProperties::class)
@SpringBootApplication
class Demo2Application

fun main(args: Array<String>) {
	runApplication<Demo2Application>(*args)
}
