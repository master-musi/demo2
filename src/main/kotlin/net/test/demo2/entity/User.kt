package net.test.demo2.entity

import java.time.Instant
import jakarta.persistence.*

@Entity
@Table(name = "users")
data class User(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, unique = true, length = 150)
    val email: String,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()

) {
    constructor() : this(null, "","", Instant.now())
}