package net.test.demo2.controller

import net.test.demo2.entity.User
import net.test.demo2.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    val userService: UserService
) {

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): ResponseEntity<User>? {
        return userService.getById(id)
            ?.map { ResponseEntity.ok(it) }
            ?.orElse(ResponseEntity.notFound().build())
    }

    @GetMapping("/search")
    fun getUserByName(
        @RequestParam("firstname") firstName: String,
        @RequestParam("lastname") lastName: String
    ): ResponseEntity<User> {
        val fullName = "$firstName $lastName"
        val user = userService.getByName(fullName)

        return if (user != null) {
            ResponseEntity.ok(user)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<User>> {
        return ResponseEntity.ok(userService.getAll())
    }

    /**
     * Example:
      curl "localhost:8082/api/users/with-token?firstname=Alice&lastname=Johnson"
     */
    @GetMapping("/with-token")
    fun getUserWithToken(
        @RequestParam("firstname") firstName: String,
        @RequestParam("lastname") lastName: String
    ): ResponseEntity<UserService.UserWithToken> {

        val fullName = "$firstName $lastName"
        val user = userService.getUserWithPayPalToken(fullName)
        return if (user != null) {
            ResponseEntity.ok(user)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping
    fun createUser(@RequestBody user: User): User {
        val saved = userService.save(user)
        return saved
    }

}