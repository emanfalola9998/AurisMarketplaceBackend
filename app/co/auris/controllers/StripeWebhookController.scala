// ─── app/co/auris/controllers/StripeWebhookController.scala ──────────────────
//
// Receives Stripe webhook events. Unauthenticated (Stripe calls this directly,
// not through JwtAuthAction) but signature-verified against stripe.webhookSecret
// via PaymentService.verifyWebhookSignature — the raw, unparsed request body is
// required for that check to match what Stripe actually signed.
//
// Always acknowledges with 200 for any event we don't act on, per Stripe's
// retry semantics — only signature failures get a non-2xx response.

package co.auris.controllers

import co.auris.services.{BillingService, BookingService, PaymentService}
import com.stripe.model.{Invoice, PaymentIntent}
import play.api.libs.json.Json
import play.api.mvc._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class StripeWebhookController @Inject() (
                                          cc:             ControllerComponents,
                                          bookingService: BookingService,
                                          billingService: BillingService,
                                          paymentService: PaymentService
                                        )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def handle: Action[RawBuffer] = Action(parse.raw).async { implicit request =>
    val payload   = request.body.asBytes().map(_.utf8String).getOrElse("")
    val sigHeader = request.headers.get("Stripe-Signature").getOrElse("")

    paymentService.verifyWebhookSignature(payload, sigHeader) match {
      case Failure(_) =>
        Future.successful(Unauthorized(Json.obj("code" -> "INVALID_SIGNATURE", "message" -> "Invalid Stripe signature.")))

      case Success(event) if event.getType == "payment_intent.succeeded" =>
        extractStripeObject[PaymentIntent](event) match {
          case None => Future.successful(Ok)
          case Some(intent) =>
            bookingIdFromMetadata(intent) match {
              case None => Future.successful(Ok)
              case Some(bookingId) =>
                bookingService.confirmBookingPayment(intent.getId, bookingId).map(_ => Ok)
            }
        }

      // Fires when the surgeon's annual membership subscription is paid —
      // both on first sign-up and on each yearly renewal.
      case Success(event) if event.getType == "invoice.paid" =>
        extractStripeObject[Invoice](event) match {
          case None => Future.successful(Ok)
          case Some(invoice) =>
            billingService.confirmMembershipPayment(invoice.getCustomer).map(_ => Ok)
        }

      case Success(_) =>
        Future.successful(Ok)
    }
  }

  private def extractStripeObject[T](event: com.stripe.model.Event)(implicit ct: scala.reflect.ClassTag[T]): Option[T] = {
    val deserializer = event.getDataObjectDeserializer
    val stripeObject = if (deserializer.getObject.isPresent) deserializer.getObject.get() else deserializer.deserializeUnsafe()
    stripeObject match {
      case obj: T => Some(obj)
      case _      => None
    }
  }

  private def bookingIdFromMetadata(intent: PaymentIntent): Option[UUID] =
    Option(intent.getMetadata.get("bookingId")).flatMap(s => Try(UUID.fromString(s)).toOption)
}
