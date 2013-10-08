package controllers

import play.api._
import          Play.current
import          mvc._
import          data._
import               Forms._
import          libs.openid._
import          libs.concurrent._
import                          Execution.Implicits.defaultContext
import          db.DB
import          i18n.Messages
import scala.concurrent.Future
import org.mindrot.jbcrypt.BCrypt
import site._
import models._

object Login extends SiteController {

  type LoginForm = Form[(Option[String],String,String)]
  private[this] val loginForm : LoginForm = Form(tuple(
    "email" -> optional(email),
    "password" -> text,
    "openid" -> text(0, 256)
  ))

  def viewLogin(err : Option[String] = None) : templates.Html =
    views.html.account.login(err.fold(loginForm)(loginForm.withGlobalError(_)))
  def viewLogin(err : String) : templates.Html =
    viewLogin(Some(err))
  def needLogin =
    Forbidden(viewLogin(Some(Messages("login.noCookie"))))

  def view = SiteAction { implicit request =>
    Ok(request.user.fold(viewLogin())(views.html.party.view(_)))
  }

  def ajaxView = SiteAction { implicit request =>
    Ok(request.user.fold(views.html.modal.login(loginForm))(views.html.modal.profile(_)))
  }

  private[this] def login(a : Account)(implicit request : Request[_], db : site.Site.DB) = {
    implicit val arequest = new SiteRequest.Auth(request, a, db)
    Audit.action(Audit.Action.open)
    Redirect(routes.Party.view(a.id)).withSession("user" -> a.id.unId.toString)
  }

  def post = Action.async { implicit request =>
    val form = loginForm.bindFromRequest
    form.fold(
      form => Future.successful(BadRequest(views.html.account.login(form))),
      { case (email, password, openid) => DB.withConnection { implicit db =>
        val acct = email.flatMap(Account.getEmail _)
        def error() : SimpleResult = {
          acct.foreach(a => Audit.actionFor(Audit.Action.attempt, a.id, dbrary.Inet(request.remoteAddress)))
          BadRequest(views.html.account.login(form.copy(data = form.data.updated("password", "")).withGlobalError(Messages("login.bad"))))
        }
        if (!password.isEmpty) {
          Future.successful(acct.filter(a => !a.password.isEmpty && BCrypt.checkpw(password, a.password)).fold(error)(login))
        } else if (!openid.isEmpty)
          OpenID.redirectURL(openid, routes.Login.openID(email.getOrElse("")).absoluteURL(), realm = Some("http://" + request.host))
            .map(Redirect(_))
            .recover { case e : OpenIDError => InternalServerError(viewLogin(e.toString)) }
        else
          Future.successful(acct.filterNot(_ => Site.isSecure).fold(error)(login))
      } }
    )
  }

  def openID(email : String) = Action.async { implicit request =>
    OpenID.verifiedId
      .map(info => DB.withConnection { implicit db =>
        Account.getOpenid(info.id, maybe(email)).fold[SimpleResult](
          BadRequest(views.html.account.login(loginForm.fill((maybe(email), "", info.id)).withError("openid", "login.openID.notFound")))
        )(login)
      }).recover { case e : OpenIDError => InternalServerError(viewLogin(e.toString)) }
  }

  def logout = SiteAction { implicit request =>
    if (request.user.isDefined)
      Audit.action(Audit.Action.close)
    Redirect(routes.Static.index).withNewSession
  }
}
