// ─── app/co/auris/services/PaymentService.scala ───────────────────────────────
//
// Thin wrapper around the Stripe SDK. All amounts in this app are GBP major
// units (BigDecimal, e.g. 250.00); Stripe wants integer minor units (pence).

package co.auris.services

import com.stripe.Stripe
import com.stripe.model.{Customer, Event, Product, Subscription}
import com.stripe.net.{RequestOptions, Webhook}
import com.stripe.param.{CustomerCreateParams, PaymentIntentCreateParams, ProductCreateParams, SubscriptionCreateParams}
import play.api.Configuration

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

final case class PaymentIntentResult(paymentIntentId: String, clientSecret: String)
final case class SubscriptionResult(subscriptionId: String, clientSecret: String)

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

  // ─── Surgeon billing ─────────────────────────────────────────────────────
  // Surgeons pay Auris directly (no Stripe Connect) — a plain Customer +
  // Subscription in Auris's own Stripe account, same as any SaaS bill.

  def createCustomer(email: String, surgeonId: UUID): Future[String] = Future {
    val params = CustomerCreateParams.builder()
      .setEmail(email)
      .putMetadata("surgeonId", surgeonId.toString)
      .build()
    Customer.create(params).getId
  }

  /** Creates the annual membership subscription and returns the client secret
   *  of its first invoice's PaymentIntent, so the frontend can confirm payment
   *  the same way it does for booking deposits.
   */
  def createAnnualMembershipSubscription(
                                          customerId: String,
                                          surgeonId:  UUID,
                                          amount:     BigDecimal
                                        ): Future[SubscriptionResult] = Future {
    val amountMinorUnits = (amount * 100).setScale(0, BigDecimal.RoundingMode.HALF_UP).toLongExact

    val product = Product.create(
      ProductCreateParams.builder().setName("Auris annual membership").build()
    )

    val priceData = SubscriptionCreateParams.Item.PriceData.builder()
      .setCurrency(currency)
      .setUnitAmount(amountMinorUnits)
      .setProduct(product.getId)
      .setRecurring(
        SubscriptionCreateParams.Item.PriceData.Recurring.builder()
          .setInterval(SubscriptionCreateParams.Item.PriceData.Recurring.Interval.YEAR)
          .build()
      )
      .build()

    val params = SubscriptionCreateParams.builder()
      .setCustomer(customerId)
      .addItem(SubscriptionCreateParams.Item.builder().setPriceData(priceData).build())
      .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
      .setPaymentSettings(
        SubscriptionCreateParams.PaymentSettings.builder()
          .setSaveDefaultPaymentMethod(SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION)
          .build()
      )
      .putMetadata("surgeonId", surgeonId.toString)
      .addAllExpand(List("latest_invoice", "latest_invoice.confirmation_secret").asJava)
      .build()

    val subscription  = Subscription.create(params)
    val invoice       = subscription.getLatestInvoiceObject
    val clientSecret  = invoice.getConfirmationSecret.getClientSecret
    SubscriptionResult(subscription.getId, clientSecret)
  }
}
