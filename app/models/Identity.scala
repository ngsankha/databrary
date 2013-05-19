package models

import play.api.db.slick.Config.driver.simple._
import collection.mutable.HashMap

class Identity(entity : Entity) {
  final override def hashCode = id
  final def equals(o : Identity) = o.id == id

  private def cache = {
    IdentityCache.add(this)
    this
  }

  final def id = entity.id
  final def name = entity.name
  final def name_=(n : String) = entity.name = n
  final def access(implicit db : Session) : Permission.Value = entity.access

  def user : Option[User] = None

  def commit(implicit db : Session) = 
    entity.commit

  final def authorizeParents(all : Boolean = false)(implicit db : Session) = Authorize.getParents(id, all)
  final def authorizeChildren(all : Boolean = false)(implicit db : Session) = Authorize.getChildren(id, all)
}

object Nobody extends Identity(Entity(Entity.NOBODY, "Everybody"))
object Root   extends Identity(Entity(Entity.ROOT,   "Databrary"))

class User(entity : Entity, account : Account) extends Identity(entity) {
  final override def user = Some(this)

  final def username = account.username
  final def email = account.email
  final def email_=(e : String) = account.email = e
  final def openid = account.openid
  final def openid_=(o : Option[String]) = account.openid = o

  override def commit(implicit db : Session) = {
    super.commit
    account.commit
  }
}

private object IdentityCache extends HashMap[Int, Identity] {
  def add(i : Identity) : Unit = update(i.id, i)
  add(Nobody)
  add(Root)
}

/* TODO: replace with proper Shape instances */
object Identity {
  private[models] def byEntity(q : Query[Entity.type, Entity]) =
    for {
      (e, a) <- q leftJoin Account on (_.id === _.id)
    } yield (e, a.?)
  def byName(n : String) = {
    // should clearly be improved and/or indexed
    val w = "%" + n.split("\\s+").filter(!_.isEmpty).mkString("%") + "%"
    for {
      (e, a) <- Entity leftJoin Account on (_.id === _.id)
      if a.username === n || DBFunctions.ilike(e.name, w)
    } yield (e, a.?)
  }

  def build(ea : (Entity, Option[Account])) = ea match { case (e,a) => 
    e.id match {
      case Entity.NOBODY => Nobody
      case Entity.ROOT => Root
      case _ => a.fold(new Identity(e))(new User(e, _))
    }
  }

  def get(i : Int)(implicit db : Session) : Identity =
    IdentityCache.getOrElseUpdate(i, 
      byEntity(Entity.byId(i)).firstOption.map(build _).orNull)

  def create(n : String)(implicit db : Session) : Identity =
    new Identity(Entity.create(n)).cache
}

object User {
  private[this] def byAccount(q : Query[Account.type, Account]) =
    for {
      a <- q
      e <- a.entity
    } yield (e, a)

  private[this] def build(ea : (Entity, Account)) = ea match { case (e,a) =>
    new User(e, a)
  }

  def get(i : Int)(implicit db : Session) : Option[User] = Option(
    IdentityCache.getOrElseUpdate(i, 
      byAccount(Account.byId(i)).firstOption.map(build _).orNull)
    .asInstanceOf[User]
  )
  def getUsername(u : String)(implicit db : Session) : Option[User] = 
    byAccount(Account.byUsername(u)).firstOption.map(build(_))
  def getOpenid(o : String, u : Option[String] = None)(implicit db : Session) : Option[User] = {
    val qao = Account.byOpenid(o)
    byAccount(u.fold(qao)(u => qao.filter(_.username === u))).firstOption.map(build(_))
  }
}
