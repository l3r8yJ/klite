package klite.jdbc

import klite.PropValue
import klite.toValues
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass

interface BaseEntity<ID> {
  val id: ID
}

interface Entity: BaseEntity<UUID>

abstract class BaseRepository(protected val db: DataSource, val table: String) {
  protected open val orderAsc get() = "order by createdAt"
  protected open val orderDesc get() = "$orderAsc desc"
}

abstract class CrudRepository<E: Entity>(db: DataSource, table: String): BaseCrudRepository<E, UUID>(db, table)

abstract class BaseCrudRepository<E: BaseEntity<ID>, ID>(db: DataSource, table: String): BaseRepository(db, table) {
  @Suppress("UNCHECKED_CAST")
  private val entityClass = this::class.supertypes.first().arguments.first().type!!.classifier as KClass<E>
  open val defaultOrder get() = orderDesc

  protected open fun ResultSet.mapper(): E = create(entityClass)
  protected open fun E.persister() = toValues()

  open fun get(id: ID): E = db.select(table, id) { mapper() }
  open fun save(entity: E) = db.upsert(table, entity.persister())
  open fun list(vararg where: PropValue<E>?, order: String = defaultOrder): List<E> =
    db.select(table, where.filterNotNull(), order) { mapper() }
  open fun count(vararg where: PropValue<E>?): Long = db.count(table, where.filterNotNull())
  open fun by(vararg where: PropValue<E>?): E? = list(*where).firstOrNull()
}
