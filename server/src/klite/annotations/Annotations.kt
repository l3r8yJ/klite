package klite.annotations

import klite.*
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.*
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaMethod

@Target(CLASS) annotation class Path(val value: String)
@Target(FUNCTION) annotation class GET(val value: String = "")
@Target(FUNCTION) annotation class POST(val value: String = "")
@Target(FUNCTION) annotation class PUT(val value: String = "")
@Target(FUNCTION) annotation class PATCH(val value: String = "")
@Target(FUNCTION) annotation class DELETE(val value: String = "")
@Target(FUNCTION) annotation class OPTIONS(val value: String = "")

@Target(VALUE_PARAMETER) annotation class PathParam
@Target(VALUE_PARAMETER) annotation class QueryParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class BodyParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class HeaderParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class CookieParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class SessionParam(val value: String = "")
@Target(VALUE_PARAMETER) annotation class AttrParam(val value: String = "")

/**
 * Adds all annotated methods as routes, sorted by path (matching more specific paths first).
 * Routes can also implement Before/After interfaces.
 *
 * Use the @XXXParam annotations to bind specific types of params.
 * Non-annotated binding of well known classes is possible, like [HttpExchange] and [Session].
 * Non-annotated custom class is interpreted as the whole POST/PUT body, e.g. a data class deserialized from json.
 */
fun Router.annotated(routes: Any) {
  val cls = routes::class
  val path = cls.annotation<Path>()?.value ?: ""
  val classDecorators = mutableListOf<Decorator>()
  if (routes is Before) classDecorators += routes.toDecorator()
  if (routes is After) classDecorators += routes.toDecorator()
  cls.functions.asSequence().mapNotNull { f ->
    val a = f.kliteAnnotation ?: return@mapNotNull null
    val method = RequestMethod.valueOf(a.annotationClass.simpleName!!)
    val subPath = a.annotationClass.members.first().call(a) as String
    val handler = classDecorators.wrap(toHandler(routes, f))
    subPath to Route(method, pathParamRegexer.from(path + subPath), f.annotations + cls.annotations, handler)
  }.sortedBy { it.first.replace(':', '~') }.forEach { add(it.second) }
}

inline fun <reified T: Any> Router.annotated() = annotated(require<T>())

private val packageName = GET::class.java.packageName
private val KAnnotatedElement.kliteAnnotation get() = annotations.filter { it.annotationClass.java.packageName == packageName }
  .let { if (it.size > 1) error("$this cannot have multiple klite annotations: $it") else it.firstOrNull() }

internal fun toHandler(instance: Any, f: KFunction<*>): Handler {
  val params = f.parameters.map(::Param)
  return {
    try {
      val args = params.associate { p -> p.param to p.valueFrom(this, instance) }.filter { !it.key.isOptional || it.value != null }
      f.callSuspendBy(args)
    } catch (e: InvocationTargetException) {
      throw e.targetException
    }
  }
}

private class Param(val param: KParameter) {
  val cls = param.type.classifier as KClass<*>
  val annotation: Annotation? = param.kliteAnnotation
  val name: String = annotation?.takeIf { it !is PathParam }?.value ?: param.name ?: ""

  fun valueFrom(e: HttpExchange, instance: Any) = try {
    if (param.kind == INSTANCE) instance
    else if (cls == HttpExchange::class) e
    else if (cls == Session::class) e.session
    else if (cls == InputStream::class) e.requestStream
    else {
      when (annotation) {
        is PathParam -> e.path(name)?.toType()
        is QueryParam -> e.query(name)?.toType()
        is HeaderParam -> e.header(name)?.toType()
        is CookieParam -> e.cookie(name)?.toType()
        is SessionParam -> e.session[name]?.toType()
        is AttrParam -> e.attr(name)
        is BodyParam -> e.body<Any?>(name)?.let { if (it is String) it.trimToNull()?.toType() else it }
        else -> e.body(param.type)
      }
    }
  } catch (e: Exception) {
    if (e.message?.contains(name) == true) throw e
    throw IllegalArgumentException("Cannot get $name: ${e.message}", e)
  }

  private val Annotation.value: String? get() = (javaClass.getMethod("value").invoke(this) as String).takeIf { it.isNotEmpty() }
  private fun String.toType() = Converter.from<Any>(this, param.type)
}

inline fun <reified T: Annotation> KClass<*>.annotation(): T? = java.getAnnotation(T::class.java)
inline fun <reified T: Annotation> KFunction<*>.annotation(): T? = javaMethod!!.getAnnotation(T::class.java)
