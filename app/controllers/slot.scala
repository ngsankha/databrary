package controllers

import play.api._
import          Play.current
import          mvc._
import          data._
import          i18n.Messages
import          libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import macros._
import macros.async._
import site._
import dbrary._
import models._

private[controllers] sealed class SlotController extends ObjectController[Slot] {
  private[controllers] def action(i : Container.Id, segment : Segment, p : Permission.Value = Permission.VIEW) =
    RequestObject.check(Slot.get(i, segment)(_), p)

  private[controllers] def Action(i : Container.Id, segment : Segment, p : Permission.Value = Permission.VIEW) =
    SiteAction andThen action(i, segment, p)

  def update(i : Container.Id, segment : Segment) =
    Action(i, segment, Permission.EDIT).async { implicit request =>
      val form = SlotController.editForm._bind
      for {
	_ <- cast[SlotController.ContainerEditForm](form).foreachAsync((form : SlotController.ContainerEditForm) =>
	  request.obj.container.change(name = form.name.get, date = form.date.get))
	_ <- form.consent.get.foreachAsync((c : Consent.Value) => request.obj.setConsent(c))
      } yield (result(request.obj))
    }
}

object SlotController extends SlotController {
  sealed trait SlotForm extends HtmlFormView {
    def actionName : String
    def formName : String = actionName + " Session"

    val consent = Field(OptionMapping(Mappings.enum(Consent)))
  }
  sealed trait ContainerForm extends SlotForm {
    val name = Field(OptionMapping(Mappings.maybeText))
    val date = Field(OptionMapping(Forms.optional(Forms.jodaLocalDate)))
  }

  sealed class EditForm(implicit request : Request[_])
    extends AHtmlForm[EditForm](
      routes.SlotHtml.update(request.obj.containerId, request.obj.segment),
      f => SlotHtml.viewEdit(Some(f)))
    with SlotForm {
    def actionName = "Update"
    override def formName = "Edit Session"
    consent.fill(Some(request.obj.consent))
  }
  final class ContainerEditForm(implicit request : Request[_])
    extends EditForm with ContainerForm {
    name.fill(Some(request.obj.container.name))
    date.fill(Some(request.obj.container.date))
  }
  def editForm(implicit request : Request[_]) : EditForm =
    if (request.obj.isFull) new ContainerEditForm
    else new EditForm

  final class ContainerCreateForm(implicit request : VolumeController.Request[_])
    extends HtmlForm[ContainerCreateForm](
      routes.SlotHtml.addContainer(request.obj.id),
      views.html.slot.edit(_, Nil, None)) 
    with ContainerForm {
    def actionName = "Create"
  }

  def zip(v : Volume.Id, i : Container.Id, segment : Segment) = Action(i, segment) { implicit request =>
    AssetController.zipResult(store.Zip.slot(request.obj), "databrary-" + request.obj.volumeId + "-" + request.obj.containerId + request.obj.pageCrumbName.fold("")("-" + _))
  }
}

object SlotHtml extends SlotController with HtmlController {
  import SlotController._

  private[controllers] def show(commentForm : Option[CommentController.SlotForm] = None, tagForm : Option[TagController.SlotForm] = None)(implicit request : Request[_]) = {
    val slot = request.obj
    for {
      records <- slot.records
      assets <- slot.assets
      comments <- slot.comments
      tags <- slot.tags
    } yield (views.html.slot.view(records, assets, comments, commentForm.getOrElse(new CommentController.SlotForm), tags, tagForm.getOrElse(new TagController.SlotForm)))
  }

  def view(v: Volume.Id, i : Container.Id, segment : Segment) = Action(i, segment).async { implicit request =>
    show().map(Ok(_))
  }

  private[controllers] def viewEdit(form : Option[EditForm] = None, recordForm : Option[RecordHtml.SelectForm] = None)(implicit request : Request[_]) =
    for {
      records <- request.obj.records
      all <- request.obj.volume.records()
      selectList = all diff records
    } yield (views.html.slot.edit(form getOrElse editForm, records, recordForm orElse Some(new RecordHtml.SelectForm), selectList))

  def edit(i : Container.Id, segment : Segment) =
    Action(i, segment, Permission.EDIT).async { implicit request =>
      editForm.Ok
    }

  def createContainer(v : models.Volume.Id) =
    VolumeController.Action(v, Permission.EDIT).async { implicit request =>
      new ContainerCreateForm().Ok
    }

  def addContainer(s : models.Volume.Id) =
    VolumeController.Action(s, Permission.CONTRIBUTE).async { implicit request =>
      val form = new ContainerCreateForm()._bind
      for {
	cont <- models.Container.create(request.obj, name = form.name.get.flatten, date = form.date.get.flatten)
	_ <- form.consent.get.foreachAsync((c : Consent.Value) =>
	  cont.setConsent(c))
      } yield (result(cont))
    }
}

object SlotApi extends SlotController with ApiController {
  def get(v : models.Volume.Id, c : models.Container.Id, segment : Segment) = Action(c, segment).async { request =>
    request.obj.slotJson(request.apiOptions).map(Ok(_))
  }
}
