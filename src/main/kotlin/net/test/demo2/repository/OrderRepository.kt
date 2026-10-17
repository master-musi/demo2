package net.test.demo2.repository

import net.test.demo2.entity.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrderRepository : CrudRepository<Order, UUID> {


}