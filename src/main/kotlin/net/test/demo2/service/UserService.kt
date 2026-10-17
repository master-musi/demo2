package net.test.demo2.service

import net.test.demo2.entity.User
import net.test.demo2.integration.PayPalClient
import net.test.demo2.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val payPalClient: PayPalClient
) {

    fun getAll(): List<User> = userRepository.findAll()

    fun getById(id: Long): Optional<User>? = userRepository.findById(id)

    fun getByName(name: String): User? =
        userRepository.findByName(name)

    fun getUserWithPayPalToken(name: String): UserWithToken? {
        val user = userRepository.findByName(name)
        val accessToken = payPalClient.getAccessToken()

        return UserWithToken(user, accessToken)
    }

    @Transactional
    fun save(user: User): User = userRepository.save(user)

    data class UserWithToken(val user: User?, val accessToken: String?)

}