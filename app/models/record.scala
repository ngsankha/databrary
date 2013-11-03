package models

import org.joda.time.Period
import dbrary._
import site._

/** Types of Records that are relevant for data organization.
  * Records that represent data buckets or other kinds of slot groupings (e.g., participants, days, conditions, etc.) can be assigned a particular RecordCategory for the purpose of display and templating.
  * For now, all instances are hard-coded.
  */
sealed abstract class RecordCategory private (val id : RecordCategory.Id, val name : String) extends TableRowId[RecordCategory] {
  /** The default set of metrics which define records in this category. */
  def template : Seq[Metric]
}

/** Interface to record categories.
  * These are all hard-coded so bypass the database, though they are stored in record_category. */
object RecordCategory extends HasId[RecordCategory] {
  def get(id : Id) : Option[RecordCategory] = id match {
    case PARTICIPANT => Some(Participant)
    case VISIT => Some(Visit)
    case _ => None
  }

  def getAll : Seq[RecordCategory] =
    Seq(Participant, Visit)

  private final val PARTICIPANT : Id = asId(-500)
  private final val VISIT : Id = asId(-200)
  /** RecordCategory representing participants, individuals whose data is contained in a particular sesion.
    * Participants usually are associated with birthdate, gender, and other demographics. */
  final val Participant = new RecordCategory(PARTICIPANT, "participant") {
    val template = Seq(Metric.Ident, Metric.Birthdate, Metric.Gender, Metric.Race, Metric.Ethnicity)
  }
  final val Visit = new RecordCategory(VISIT, "visit") {
    val template = Seq(Metric.Ident)
  }
}

/** A set of Measures. */
final class Record private (val id : Record.Id, val volume : Volume, val category_ : Option[RecordCategory] = None, val consent : Consent.Value = Consent.NONE) extends TableRowId[Record] with SitePage with InVolume {
  private[this] var _category = category_
  def category : Option[RecordCategory] = _category
  def categoryId = category.map(_.id)

  /** Update the given values in the database and this object in-place. */
  def change(category : Option[RecordCategory] = _category)(implicit db : Site.DB) : Unit = {
    if (category == _category)
      return
    SQL("UPDATE record SET category = ? WHERE id = ?", id, category.map(_.id)).run()
    _category = category
  }

  /** A specific measure of the given type and metric. */
  def measure[T](metric : MetricT[T])(implicit db : Site.DB) : Option[T] = MeasureT.get[T](this.id, metric)
  def measure(metric : Metric)(implicit db : Site.DB) : Option[Measure] = Measure.get(this.id, metric)
  private[this] val _measures = CachedVal[Seq[Measure], Site.DB](Measure.getRecord(this.id)(_))
  /** All measures in this record. Cached. */
  def measures(implicit db : Site.DB) : Seq[Measure] = _measures

  /** Add or change a measure on this record.
    * This is not type safe so may generate SQL exceptions, and may invalidate measures on this object. */
  def setMeasure(metric : Metric, value : String)(implicit db : Site.DB) : Boolean = Measure(id, metric, value).set
  /** Remove a measure from this record.
    * This may invalidate measures on this object. */
  def deleteMeasure(metric : Metric)(implicit db : Site.DB) = Measure.delete(id, metric)

  private val _ident = CachedVal[Option[String], Site.DB](measure(Metric.Ident)(_))
  /** Cached version of `measure(Metric.Ident)`.
    * This may become invalid if the value is changed. */
  def ident(implicit db : Site.DB) : Option[String] = _ident

  private val _birthdate = CachedVal[Option[Date], Site.DB](measure(Metric.Birthdate)(_))
  /** Cached version of `measure(Metric.Birthdate)`.
    * This may become invalid if the value is changed. */
  def birthdate(implicit db : Site.DB) : Option[Date] = _birthdate

  private val _gender = CachedVal[Option[String], Site.DB](measure(Metric.Gender)(_))
  /** Cached version of `measure(Metric.Gender)`.
    * This may become invalid if the value is changed. */
  def gender(implicit db : Site.DB) : Option[String] = _gender

  private val _daterange = CachedVal[Range[Date], Site.DB] { implicit db =>
    SQL("SELECT record_daterange(?)", id).single(SQLCols[Range[Date]])
  }
  /** The range of acquisition dates covered by associated slots. Cached. */
  def daterange(implicit db : Site.DB) : Range[Date] = _daterange.normalize

  /** The range of ages as defined by `daterange - birthdate`. */
  def agerange(implicit db : Site.DB) : Option[Range[Age]] = birthdate.map(dob => daterange.map(d => Age(dob, d)))

  /** The age at test for a specific date, as defined by `date - birthdate`. */
  def age(date : Date)(implicit db : Site.DB) : Option[Age] = birthdate.map(dob => Age(dob, date))

  /** Effective permission the site user has over a given metric in this record, specifically in regards to the measure datum itself.
    * Record permissions depend on volume permissions, but can be further restricted by consent levels.
    */
  def dataPermission(metric : Metric)(implicit site : Site) : HasPermission =
    Permission.data(volume.permission, _ => consent, metric.classification)

  /** The set of slots to which this record applies. */
  def slots(implicit db : Site.DB) : Seq[Slot] =
    Slot.volumeRow(volume).SQL("JOIN slot_record ON slot.id = slot_record.slot WHERE slot_record.record = {record} ORDER BY slot.source, slot.segment").
    on('record -> id).list
  /** Attach this record to a slot. */
  def addSlot(s : Slot)(implicit db : Site.DB) = Record.addSlot(id, s.id)

  def pageName(implicit site : Site) = category.fold("")(_.name.capitalize + " ") + ident.getOrElse("Record ["+id+"]")
  def pageParent(implicit site : Site) = Some(volume)
  def pageURL(implicit site : Site) = controllers.routes.Record.view(volume.id, id)
  def pageActions(implicit site : Site) = Seq(
    Action("view", controllers.routes.Record.view(volumeId, id), Permission.VIEW),
    Action("edit", controllers.routes.Record.edit(volumeId, id), Permission.EDIT)
  )
}

object Record extends TableId[Record]("record") {
  private[models] def make(volume : Volume)(id : Id, category : Option[RecordCategory.Id], consent : Option[Consent.Value]) =
    new Record(id, volume, category.flatMap(RecordCategory.get(_)), consent.getOrElse(Consent.NONE))
  private val columns = Columns[
    Id,  Option[RecordCategory.Id], Option[Consent.Value]](
    'id, 'category,                 SelectAs("record_consent(record.id)", "record_consent"))
  private[models] val row = columns.join(Volume.row, "record.volume = volume.id") map {
    case (rec ~ vol) => (make(vol) _).tupled(rec)
  }
  private[models] def volumeRow(vol : Volume) = columns.map(make(vol) _)
  private[models] def measureRow[T](vol : Volume, metric : MetricT[T]) = {
    val mt = metric.measureType
    volumeRow(vol).leftJoin(mt.column, "record.id = " + mt.table + ".record AND " + mt.table + ".metric = {metric}") map {
      case (record ~ meas) =>
        metric match {
          case Metric.Ident => record._ident() = meas
          case _ => ()
        }
        (record, meas)
    }
  }

  /** Retrieve a specific record by id. */
  def get(id : Id)(implicit site : Site) : Option[Record] =
    row.SQL("WHERE record.id = {id} AND", Volume.condition).
      on(Volume.conditionArgs('id -> id) : _*).singleOpt

  /** Retrieve the set of records on the given slot. */
  private[models] def getSlot(slot : Slot)(implicit db : Site.DB) : Seq[Record] =
    volumeRow(slot.volume).SQL("JOIN slot_record ON record.id = slot_record.record WHERE slot_record.slot = {slot} ORDER BY record.category").
      on('slot -> slot.id).list

  /** Retrieve all the categorized records associated with the given volume.
    * @param category restrict to the specified category, or include all categories
    * @return records sorted by category, ident */
  private[models] def getVolume(volume : Volume, category : Option[RecordCategory] = None)(implicit db : Site.DB) : Seq[Record] = {
    val metric = Metric.Ident
    measureRow(volume, metric).map(_._1).
      SQL("WHERE record.volume = {volume}",
        (if (category.isDefined) "AND record.category = {category}" else ""),
        "ORDER BY " + (if (category.isEmpty) "record.category, " else ""),
        metric.measureType.column.select + ", record.id").
      on('volume -> volume.id, 'metric -> metric.id, 'category -> category.map(_.id)).
      list
  }

  /** Retrieve the records in the given volume with a measure of the given value.
    * @param category restrict to the specified category, or include all categories
    * @param metric search by metric
    * @param value measure value that must match
    */
  def findMeasure[T](volume : Volume, category : Option[RecordCategory] = None, metric : MetricT[T], value : T)(implicit db : Site.DB) : Seq[Record] =
    measureRow(volume, metric).map(_._1).
      SQL("WHERE record.volume = {volume}",
        (if (category.isDefined) "AND record.category = {category}" else ""),
        "AND " + metric.measureType.column.select + " = {value}").
      on('volume -> volume.id, 'metric -> metric.id, 'category -> category.map(_.id), 'value -> value).
      list

  /** Create a new record, initially unattached. */
  def create(volume : Volume, category : Option[RecordCategory] = None)(implicit db : Site.DB) : Record = {
    val args = SQLArgs('volume -> volume.id, 'category -> category.map(_.id))
    val id = SQL("INSERT INTO record " + args.insert + " RETURNING id").
      on(args : _*).single(scalar[Id])
    new Record(id, volume, category)
  }

  private[models] def addSlot(r : Record.Id, s : Slot.Id)(implicit db : Site.DB) : Unit = {
    val args = SQLTerms('record -> r, 'slot -> s)
    args.query("INSERT INTO slot_record " + args.insert).recoverWith {
      case SQLDuplicateKeyException => ()
    }.run
  }
  private[models] def removeSlot(r : Record.Id, s : Slot.Id)(implicit db : Site.DB) : Unit = {
    val args = SQLArgs('record -> r, 'slot -> s)
    SQL("DELETE FROM slot_record WHERE " + args.where).on(args : _*).execute
  }

  private[models] object View extends Table[Record]("record_view") {
    private[models] val row = Record.row

    private def volumeRow(vol : Volume) = Columns[
      Id,  Option[RecordCategory.Id], Option[String], Option[Date], Option[String], Option[Date],                            Option[Consent.Value]](
      'id, 'category,                 'ident,         'birthdate,   'gender,        SelectAs("container.date", "record_date"), SelectAs("slot.consent", "record_consent")).
      map { (id, category, ident, birthdate, gender, date, consent) =>
        val r = new Record(id, vol, category.flatMap(RecordCategory.get _), consent.getOrElse(Consent.NONE))
        r._ident() = ident
        r._birthdate() = birthdate
        r._gender() = gender
        date foreach { d =>
          r._daterange() = Range.singleton(d)(PGDateRange)
        }
        r
      }.
      from("slot_record JOIN record_view ON slot_record.record = record_view.id")
    def getSlots(vol : Volume) =
      Slot.volumeRow(vol).?.join(volumeRow(vol).?, _ + " FULL JOIN " + _ + " ON slot.id = slot_record.slot AND container.volume = record_view.volume") map {
        case (slot ~ rec) => (slot, rec)
      }
  }
}
