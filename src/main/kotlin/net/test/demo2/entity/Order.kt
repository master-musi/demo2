package net.test.demo2.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Entity
@Table(name = "orders")
data class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false)
    val price: BigDecimal,

    @Column(name = "paypal_capture_id", nullable = true, length = 100)
    var paypalCaptureId: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
) {
    // Secondary constructor to satisfy JPA requirements
    constructor() : this(null, "", BigDecimal.ZERO, null, Instant.now())
}