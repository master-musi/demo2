package net.test.demo2.controller

import net.test.demo2.service.OrderService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/orders")
class OrderController(
    val service: OrderService
) {

    /**
     * Creates intention for order payment, returns approvalURL to the client to approve
     * @param - order details
     */
    @PostMapping("/create")
    fun placeOrder(@RequestBody order: OrderRequest): OrderResponse {

        val details = service.createOrder(order.name, order.price)

        return OrderResponse(
            orderId = details.dbOrderId,
            name = order.name,
            url = details.approvalUrl
        )
    }

    /**
     * Executes the payment capture. The client must pass the 'token' string
     * that PayPal appended to their return URL after authorization.
     * @param dbOrderId - local order id
     * @param payPalOrderId - it is {@link PayPalOrderResponse#id} response model after op ".uri("/v2/checkout/orders")".
     */
    @PostMapping("/{orderId}/capture")
    fun captureOrder(
        @PathVariable("orderId") dbOrderId: UUID,
        @RequestParam("token") payPalOrderId: String
    ): OrderService.OrderCaptureResult {
        return service.capturePayment(dbOrderId, payPalOrderId)
    }

    /**
     * Issues a full or partial refund for a specific database order.
     */
    @PostMapping("/{orderId}/refund")
    fun refundOrder(
        @PathVariable("orderId") dbOrderId: UUID
    ): OrderService.RefundResult {
        return service.refundPayment(dbOrderUuid = dbOrderId)
    }

    data class OrderRequest(
        val name: String,
        val price: BigDecimal
    )

    data class OrderResponse(
        val orderId: UUID,
        val name: String,
        val url: String
    )
}