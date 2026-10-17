package net.test.demo2.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypal")
class PayPalProperties(
    val clientId: String,
    val clientSecret: String,
    val baseUrl: String,
) {

}