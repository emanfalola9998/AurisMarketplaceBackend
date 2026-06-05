// ─── app/co/auris/controllers/AdminController.scala ──────────────────────────

package co.auris.controllers

import co.auris.actions.JwtAuthAction
import co.auris.models._
import co.auris.repositories.{BookingRepository, SurgeonRepository, UserRepository}
import play.api.libs.json._
import play.api.mvc._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject() (
                                  cc:                ControllerComponents,
                                  authAction:        JwtAuthAction,
                                  surgeonRepository: SurgeonRepository,
                                  userRepository:    UserRepository,
                                  bookingRepository: BookingRepository
                                )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // ─── GET /api/admin/applications ─────────────────────────────────────────

  def listApplications: Action[AnyContent] = authAction.async { implicit request =>
    request.requireAdmin {
      val status = request.getQueryString("status")
        .flatMap(s => ApplicationStatus.values.find(_.entryName == s))
        .getOrElse(ApplicationStatus.Pending)

      surgeonRepository.listApplicationsByStatus(status).map { apps =>
        Ok(Json.obj(
          "items"      -> Json.toJson(apps),
          "totalCount" -> apps.length,
          "status"     -> status.entryName
        ))
      }
    }
  }

  // ─── GET /api/admin/applications/:id ─────────────────────────────────────

  def getApplication(id: UUID): Action[AnyContent] = authAction.async { implicit request =>
    request.requireAdmin {
      // Find application by ID then join to surgeon profile
      surgeonRepository.listApplicationsByStatus(ApplicationStatus.Pending).flatMap { apps =>
        apps.find(_.id == id) match {
          case None =>
            // Check other statuses
            Future.successful(NotFound(apiError("NOT_FOUND", "Application not found.")))

          case Some(app) =>
            surgeonRepository.findById(app.surgeonId).map {
              case None         => NotFound(apiError("NOT_FOUND", "Surgeon profile not found."))
              case Some(surgeon) =>
                Ok(Json.obj(
                  "application" -> Json.toJson(app),
                  "surgeon"     -> Json.toJson(surgeon)
                ))
            }
        }
      }
    }
  }

  // ─── PUT /api/admin/applications/:id/approve ─────────────────────────────

  def approveApplication(id: UUID): Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireAdmin {
      // 1. Find all applications (across all statuses) to locate this one
      findApplicationById(id).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Application not found.")))

        case Some(app) if app.status == ApplicationStatus.Approved =>
          Future.successful(Conflict(apiError("ALREADY_APPROVED", "Application is already approved.")))

        case Some(app) =>
          for {
            _ <- surgeonRepository.approveApplication(app.id, request.userId)
            _ <- surgeonRepository.setProfileLive(app.surgeonId, live = true)
            _ <- logAudit(request.userId, "application_approved", "surgeon_application", app.id)
          } yield Ok(Json.obj("message" -> "Application approved. Surgeon profile is now live."))
      }
    }
  }

  // ─── PUT /api/admin/applications/:id/reject ───────────────────────────────

  def rejectApplication(id: UUID): Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireAdmin {
      val notes = (request.body \ "notes").asOpt[String]
      val flags = (request.body \ "flags").asOpt[List[String]].getOrElse(Nil)

      findApplicationById(id).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Application not found.")))

        case Some(app) if app.status == ApplicationStatus.Rejected =>
          Future.successful(Conflict(apiError("ALREADY_REJECTED", "Application is already rejected.")))

        case Some(app) =>
          for {
            _ <- surgeonRepository.updateApplicationStatus(
              app.id, ApplicationStatus.Rejected,
              Some(request.userId), notes, flags, app.score
            )
            _ <- logAudit(request.userId, "application_rejected", "surgeon_application", app.id)
          } yield Ok(Json.obj("message" -> "Application rejected."))
      }
    }
  }

  // ─── PUT /api/admin/applications/:id/request-info ────────────────────────

  def requestMoreInfo(id: UUID): Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireAdmin {
      val notes = (request.body \ "notes").asOpt[String]
      val flags = (request.body \ "flags").asOpt[List[String]].getOrElse(Nil)

      findApplicationById(id).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Application not found.")))

        case Some(app) =>
          for {
            _ <- surgeonRepository.updateApplicationStatus(
              app.id, ApplicationStatus.MoreInfoRequired,
              Some(request.userId), notes, flags, app.score
            )
          } yield Ok(Json.obj("message" -> "More information requested from the surgeon."))
      }
    }
  }

  // ─── GET /api/admin/surgeons ──────────────────────────────────────────────

  def listActiveSurgeons: Action[AnyContent] = authAction.async { implicit request =>
    request.requireAdmin {
      val page     = request.getQueryString("page").flatMap(_.toIntOption).getOrElse(1)
      val pageSize = request.getQueryString("pageSize").flatMap(_.toIntOption).getOrElse(50)

      surgeonRepository.search(
        specialty = None,
        city      = None,
        query     = request.getQueryString("q"),
        page      = page,
        pageSize  = pageSize
      ).map { case (surgeons, total) =>
        Ok(Json.obj(
          "items"      -> Json.toJson(surgeons),
          "totalCount" -> total,
          "page"       -> page,
          "pageSize"   -> pageSize
        ))
      }
    }
  }

  // ─── PUT /api/admin/surgeons/:id/suspend ─────────────────────────────────

  def suspendSurgeon(id: UUID): Action[JsValue] = authAction(parse.json).async { implicit request =>
    request.requireAdmin {
      surgeonRepository.findById(id).flatMap {
        case None =>
          Future.successful(NotFound(apiError("NOT_FOUND", "Surgeon not found.")))
        case Some(surgeon) =>
          for {
            _ <- surgeonRepository.setProfileLive(id, live = false)
            _ <- userRepository.setActive(surgeon.userId, active = false)
            _ <- userRepository.revokeAllRefreshTokensForUser(surgeon.userId)
            _ <- logAudit(request.userId, "surgeon_suspended", "surgeon_profile", id)
          } yield Ok(Json.obj("message" -> "Surgeon suspended and signed out of all sessions."))
      }
    }
  }

  // ─── GET /api/admin/analytics ────────────────────────────────────────────

  def analytics: Action[AnyContent] = authAction.async { implicit request =>
    request.requireAdmin {
      // Stub — wire to aggregation queries once full analytics are needed
      Future.successful(Ok(Json.obj(
        "pendingApplications" -> 0,
        "activeSurgeons"      -> 0,
        "totalBookings"       -> 0,
        "totalPatients"       -> 0
      )))
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private def findApplicationById(id: UUID): Future[Option[SurgeonApplication]] = {
    val allStatuses = ApplicationStatus.values.toList
    allStatuses.foldLeft(Future.successful(Option.empty[SurgeonApplication])) { (accFuture, status) =>
      accFuture.flatMap {
        case Some(found) => Future.successful(Some(found))
        case None =>
          surgeonRepository.listApplicationsByStatus(status).map(_.find(_.id == id))
      }
    }
  }

  private def logAudit(
                        actorId:    UUID,
                        action:     String,
                        targetType: String,
                        targetId:   UUID
                      ): Future[Unit] =
    // TODO: wire to AuditLogRepository
    Future.successful(())

  private def apiError(code: String, message: String): JsValue =
    Json.toJson(ApiError(code, message))
}