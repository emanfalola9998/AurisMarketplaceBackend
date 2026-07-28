// ─── app/co/auris/services/PaymentService.scala ───────────────────────────────
//
// Thin wrapper around the Stripe SDK. All amounts in this app are GBP major
// units (BigDecimal, e.g. 250.00); Stripe wants integer minor units (pence).

package co.auris.services

import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.net.{RequestOptions, Webhook}
import com.stripe.param.PaymentIntentCreateParams
import play.api.Configuration

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

final case class PaymentIntentResult(paymentIntentId: String, clientSecret: String)

@Singleton
class PaymentService @Inject() (config: Configuration)(implicit ec: ExecutionContext) {

  Stripe.apiKey = config.get[String]("stripe.secretKey")
  private val webhookSecret = config.get[String]("stripe.webhookSecret")
  private val currency      = "gbp"

  def createPaymentIntent(
                            bookingId:    UUID,
                            amount:       BigDecimal,
                            receiptEmail: Option[String]
                          ): Future[PaymentIntentResult] = Future {
    val amountMinorUnits = (amount * 100).setScale(0, BigDecimal.RoundingMode.HALF_UP).toLongExact

    val paramsBuilder = PaymentIntentCreateParams.builder()
      .setAmount(amountMinorUnits)
      .setCurrency(currency)
      .putMetadata("bookingId", bookingId.toString)
      .setAutomaticPaymentMethods(
        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
      )
    receiptEmail.foreach(paramsBuilder.setReceiptEmail)

    val requestOptions = RequestOptions.builder()
      .setIdempotencyKey(s"booking-payment-intent-$bookingId")
      .build()

    val intent = com.stripe.model.PaymentIntent.create(paramsBuilder.build(), requestOptions)
    PaymentIntentResult(intent.getId, intent.getClientSecret)
  }

  def verifyWebhookSignature(payload: String, sigHeader: String): Try[Event] =
    Try(Webhook.constructEvent(payload, sigHeader, webhookSecret))
}
