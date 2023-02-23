package klite

import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility.PUBLIC
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

typealias PropValue<T> = Pair<KProperty1<T, *>, *>

fun <T: Any> T.toValues(vararg provided: PropValue<T>): Map<String, Any?> {
  val values = provided.associate { it.first.name to it.second }
  return toValuesSkipping(values.keys) + values
}

fun <T: Any> T.toValuesSkipping(vararg skip: KProperty1<T, *>) = toValuesSkipping(skip.map { it.name }.toSet())

@Suppress("UNCHECKED_CAST")
val <T: Any> T.propsSequence get() = (this::class.memberProperties as Collection<KProperty1<T, *>>).asSequence()

private fun <T: Any> T.toValuesSkipping(skipNames: Set<String>): Map<String, Any?> = toValues(propsSequence.filter { it.name !in skipNames })

fun <T> KProperty1<T, *>.valueOf(o: T) = try { get(o) } catch (e: InvocationTargetException) { throw e.targetException }

fun <T: Any> T.toValues(props: Sequence<KProperty1<T, *>>): Map<String, Any?> =
  props.filter { it.visibility == PUBLIC && it.javaField != null }.associate { it.name to it.valueOf(this) }


fun <T: Any> Map<String, Any?>.fromValues(type: KClass<T>, getValue: (KParameter) -> Any? = { Converter.from(get(it.name), it.type) }): T {
  val constructor = type.primaryConstructor!!
  val args = constructor.parameters.associateWith { getValue(it) }.filterNot { it.key.isOptional && it.value == null }
  return try {
    constructor.callBy(args)
  } catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("Cannot create $type using " + args.mapKeys { it.key.name }, e)
  }
}
