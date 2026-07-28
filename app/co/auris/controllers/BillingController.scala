// ─── app/co/auris/controllers/BillingController.scala ─────────────────────────
//
// Surgeon billing: the annual membership subscription, and the admin-facing
// platform-fee ledger (1% per booking + annual fees) that Auris's manual
// surgeon payouts are reconciled against.

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models.ApiError
import co.auris.services.{BillingError, BillingService}
import play.api.libs.json._
import play.api.mvc._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class BillingController @Inject() (
                                    cc:             ControllerComponents,
                                    authAction:     JwtAuthAction,
                                    billingService: BillingService
                                  )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // ─── POST /api/surgeons/membership/subscribe ──────────────────────────────

  def subscribeToMembership: Action[AnyContent] = authAction.async { implicit request =>
    request.requireSurgeon {
      billingService.subscribeToMembership(request.userId).map {
        case Left(BillingError.SurgeonNotFound)   => NotFound(apiError("NOT_FOUND", "Surgeon profile not found."))
        case Left(BillingError.AlreadySubscribed) => Conflict(apiError("ALREADY_SUBSCRIBED", "Your membership is already active."))
        case Right(sub)                           => Ok(Json.obj("stripeClientSecret" -> sub.clientSecret))
      }
    }
  }

  // ─── GET /api/admin/platform-fees ──────────────────────────────────────────

  def listPlatformFees: Action[AnyContent] = authAction.async { implicit request =>
    request.requireAdmin {
      billingService.listUnpaidFeesBySurgeon().map { summaries =>
        Ok(Json.obj("items" -> Json.toJson(summaries)))
      }
    }
  }

  // ─── PUT /api/admin/platform-fees/:id/mark-paid ───────────────────────────

  def markFeePaidOut(id: UUID): Action[AnyContent] = authAction.async { implicit request =>
    request.requireAdmin {
      billingService.markFeePaidOut(id).map(_ => Ok(Json.obj("message" -> "Marked as paid out.")))
    }
  }

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))
}
