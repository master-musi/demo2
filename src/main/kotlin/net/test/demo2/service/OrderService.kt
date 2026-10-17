package net.test.demo2.service

import jakarta.transaction.Transactional
import net.test.demo2.entity.Order
import net.test.demo2.integration.PayPalClient
import net.test.demo2.repository.OrderRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class OrderService(
    private val payPalClient: PayPalClient,
    private val orderRepository: OrderRepository
) {

    fun createOrder(name: String, price: BigDecimal): SavedOrderDetails {

        val temporaryOrder = Order(
            name = name,
            price = price
        )

        val savedOrder = orderRepository.save(temporaryOrder)

        val orderResponse = payPalClient.createMoneyOrder(savedOrder)

        val approvalUrl = orderResponse.links?.find { it.rel == "approve" }?.href
            ?: throw IllegalStateException("PayPal did not return an approval link")

        return SavedOrderDetails(
            dbOrderId = savedOrder.id ?: throw IllegalStateException("DB failed to generate UUID"),
            approvalUrl = approvalUrl
        )
    }

    @Transactional
    fun capturePayment(dbOrderUuid: UUID, payPalOrderId: String): OrderCaptureResult {
        // 1. Look up your local database record first to verify it exists
        val order = orderRepository.findById(dbOrderUuid).orElseThrow {
            IllegalArgumentException("Order not found in database for ID: $dbOrderUuid")
        }

        // 2. Tell PayPal to finalize and move the funds
        val captureResponse = payPalClient.captureOrder(payPalOrderId)

        if (captureResponse.status != "COMPLETED") {
            throw IllegalStateException("PayPal transaction was not completed: ${captureResponse.status}")
        }

        // 3. Drill down into the exact models you provided to find the real Capture ID
        val purchaseUnit = captureResponse.purchaseUnits?.firstOrNull()
            ?: throw IllegalStateException("No purchase units found in capture response")

        val realCaptureId = purchaseUnit.payments?.captures?.firstOrNull()?.id
            ?: throw IllegalStateException("No payment capture details found")

        // 4. Update the entity with the permanent transaction tracking reference, for possible refunds
        order.paypalCaptureId = realCaptureId
        orderRepository.save(order)

        return OrderCaptureResult(
            orderId = dbOrderUuid,
            paypalCaptureId = realCaptureId,
            status = "SUCCESS"
        )
    }

    @Transactional
    fun refundPayment(dbOrderUuid: UUID): RefundResult {
        // 1. Find the order in your local database
        val order = orderRepository.findById(dbOrderUuid).orElseThrow {
            IllegalArgumentException("Order not found in database for ID: $dbOrderUuid")
        }

        // 2. Ensure the order was actually captured before allowing a refund
        val captureId = order.paypalCaptureId
            ?: throw IllegalStateException("Cannot refund an order that has not been successfully captured")

        // 3. Request the refund from PayPal using the saved capture ID
        val paypalResponse = payPalClient.refundOrder(
            captureDetailsId = captureId,
            amount = order.price,
        )

        //TODO: 4. (Optional) You can update an internal status flag here, e.g., order.status = "REFUNDED"
        // orderRepository.save(order)

        return RefundResult(
            refundId = paypalResponse.id,
            status = paypalResponse.status,
            message = "Refund processed successfully"
        )
    }

    data class SavedOrderDetails(val dbOrderId: UUID, val approvalUrl: String)

    data class OrderCaptureResult(val orderId: UUID, val paypalCaptureId: String, val status: String)

    data class RefundResult(val refundId: String, val status: String, val message: String)
}