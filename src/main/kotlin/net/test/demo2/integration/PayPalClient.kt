package net.test.demo2.integration

import com.fasterxml.jackson.annotation.JsonProperty
import net.test.demo2.config.PayPalProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.util.*

/**
 *  PayPal's Payments V2 API integration.
 *
 *  Step    Action                              ID Generated                        What it represents
 *  1       Backend calls /v2/checkout/orders   Order ID (PayPalOrderResponse.id)   "Intent created, awaiting user login."
 *  2       Frontend redirects buyer
 *          using the 'approve' link.            None                                Buyer logs in and clicks "Pay Now" on PayPal.
 *  3       Backend calls
 *      /v2/checkout/orders/{order_id}/capture  'Capture ID' (paypalCaptureId)        "Money successfully collected." Use this for refunds!
 *
 */
@Component
class PayPalClient(
    private val payPalProperties: PayPalProperties,
) {
    private val restClient = RestClient.builder()
        .baseUrl(payPalProperties.baseUrl)
        .build()

    fun getAccessToken(): String? {
        val creds = "${payPalProperties.clientId}:${payPalProperties.clientSecret}"
        val encodedCreds = Base64.getEncoder().encodeToString(creds.toByteArray())

        val formData = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
        }

        val response = restClient.post()
            .uri("${payPalProperties.baseUrl}/v1/oauth2/token")
            .header(HttpHeaders.AUTHORIZATION, "Basic $encodedCreds")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .body(TokenResponse::class.java) ?: return null

        return response.accessToken
    }

    /**
     * Creates a PayPal checkout order based on our database Order entity.
     * Uses the database ID as an item reference and an idempotency key.
     *
     * [ Money DB Order ] -> createMoneyOrder() -> Returns PayPal Order ID (payPalOrderResponseId)
     *                                                       |
     * [ Frontend Flow ] <- Buyer Authorizes <- Pass payPalOrderResponseId to Frontend
     *                                                       |
     * [ Execute Payment] -> captureOrder(payPalOrderResponseId) -> Returns Capture ID (captureDetailsId)
     *                                                       |
     * [ Later Management] -> refundOrder(captureDetailsId, ...) -> Returns PayPal Refund ID
     */
    fun createMoneyOrder(order: net.test.demo2.entity.Order): PayPalOrderResponse {
        val token = getAccessToken()

        // Using database order ID to ensure PayPal processes this specific transaction exactly once
        val idempotencyKey = "order-id-${order.id}"

        val requestPayload = PayPalOrderRequest(
            intent = "CAPTURE", // CAPTURE means immediate payment execution after user approval
            purchaseUnits = listOf(
                PurchaseUnit(
                    referenceId = order.id.toString(),
                    amount = Amount(
                        currencyCode = "USD",
                        value = order.price.toPlainString()
                    ),
                    description = order.name
                )
            )
        )

        return restClient.post()
            .uri("/v2/checkout/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("PayPal-Request-Id", idempotencyKey) // Idempotency protection
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestPayload)
            .retrieve()
            .body(PayPalOrderResponse::class.java)
            ?: throw IllegalStateException("PayPal order creation response was empty")
    }

    /**
     * Captures the funds for an approved PayPal order.
     * Run this after the customer returns from the PayPal approval redirect.
     * @param payPalOrderResponseId - it is {@link PayPalOrderResponse#id} response model after op ".uri("/v2/checkout/orders")".
     */
    fun captureOrder(payPalOrderResponseId: String): PayPalCaptureResponse {
        val token = getAccessToken()

        return restClient.post()
            .uri("/v2/checkout/orders/$payPalOrderResponseId/capture")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(PayPalCaptureResponse::class.java)
            ?: throw IllegalStateException("PayPal capture response was empty")
    }

    /**
     * Issues a full or partial refund for a previously captured payment.
     *
     * @param captureDetailsId The PayPal capture ID returned when the payment was collected, {@link CaptureDetails#id}, ops 'captureOrder'.
     * @param amount Optional specific amount to refund. If null, PayPal issues a full refund.
     * @param currency Optional currency code (e.g., "USD"). Required if 'amount' is provided.
     */
    fun refundOrder(captureDetailsId: String,
                            amount: BigDecimal,
                            currency: String? = "USD"): PayPalRefundResponse {

        val token = getAccessToken()

        // Build an idempotency key specific to this refund operation
        val idempotencyKey = "refund-id-${captureDetailsId}"

        val requestPayload = PayPalRefundRequest(
                amount = Amount(
                    currencyCode = currency ?: "USD",
                    value = amount.toPlainString()
                ),
                noteToPayor = "Refund processed by system application."
            )

        return restClient.post()
            .uri("/v2/payments/captures/${captureDetailsId}/refund")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("PayPal-Request-Id", idempotencyKey) // Idempotency protection
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestPayload)
            .retrieve()
            .body(PayPalRefundResponse::class.java)
            ?: throw IllegalStateException("PayPal refund response was empty")
    }

    data class TokenResponse(
        val scope: String?,
        val access_token: String?,
        val token_type: String?,
        val expires_in: Long?,
        val app_id: String?,
        val nonce: String?,
    ) {
        val accessToken: String get() = access_token ?: ""
    }

    data class PayPalOrderRequest(
        val intent: String, // CAPTURE or AUTHORIZE
        @JsonProperty("purchase_units")
        val purchaseUnits: List<PurchaseUnit>
    )

    data class PurchaseUnit(
        @JsonProperty("reference_id")
        val referenceId: String?,
        val amount: Amount,
        val description: String?
    )

    data class Amount(
        @JsonProperty("currency_code")
        val currencyCode: String,
        val value: String
    )

    data class PayPalOrderResponse(
        val id: String, // This is the PayPal Order ID for the frontend needs
        val status: String, // CREATED, APPROVED, COMPLETED, etc.
        val links: List<PayPalLink>?
    )

    data class PayPalLink(
        val href: String, // The target URL destination the application needs to redirect the user to
        val rel: String, // Look for rel = "approve" to redirect the user
        val method: String // The HTTP verb required to interact with the link
    )

    data class PayPalRefundRequest(
        val amount: Amount?,
        @JsonProperty("note_to_payor") val noteToPayor: String?
    )

    data class PayPalRefundResponse(
        val id: String, // The generated PayPal Refund ID
        val status: String, // CANCELLED, PENDING, COMPLETED
        @JsonProperty("invoice_id") val invoiceId: String?,
        val links: List<PayPalLink>?
    )

    data class PayPalCaptureResponse(
        val id: String, // This is the original Order ID again
        val status: String, // Will change to "COMPLETED"
        @JsonProperty("purchase_units")
        val purchaseUnits: List<CapturePurchaseUnit>?
    )

    data class CapturePurchaseUnit(
        val payments: CapturePayments?
    )

    data class CapturePayments(
        val captures: List<CaptureDetails>?
    )

    // Paypal transaction ID and other stuff
    data class CaptureDetails(
        val id: String, // THIS IS THE REAL PAYPAL CAPTURE ID FOR REFUNDS!!!11
        val status: String,
        val amount: Amount?
    )
}