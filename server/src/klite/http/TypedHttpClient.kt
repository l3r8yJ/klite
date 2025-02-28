package klite.http

import klite.error
import klite.info
import klite.logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

typealias RequestModifier =  HttpRequest.Builder.() -> HttpRequest.Builder

/**
 * Configure a default java.net.HttpClient in your registry with proper default timeout
 */
open class TypedHttpClient(
  protected val urlPrefix: String = "",
  val reqModifier: RequestModifier = { this },
  val errorHandler: (HttpResponse<*>, String) -> Nothing = { res, body -> throw IOException("Failed with ${res.statusCode()}: $body") },
  val retryCount: Int = 0,
  val retryAfter: Duration = 1.seconds,
  private val maxLoggedLen: Int = 1000,
  val http: HttpClient,
  val contentType: String
) {
  val logger = logger(Exception().stackTrace.first { it.className !== javaClass.name }.className).apply {
    info("Using $urlPrefix")
  }

  private fun buildReq(urlSuffix: String) = HttpRequest.newBuilder().uri(URI("$urlPrefix$urlSuffix"))
    .setHeader("Content-Type", "application/json; charset=UTF-8").setHeader("Accept", "application/json")
    .timeout(10.seconds).reqModifier()

  private suspend fun <T> request(urlSuffix: String, type: KType, payload: String? = null, builder: RequestModifier): T {
    val req = buildReq(urlSuffix).builder().build()
    val start = System.nanoTime()
    val res = http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).await()
    val ms = (System.nanoTime() - start) / 1000_000
    val body = res.body().trim() // TODO: NPE -> return nullable type
    if (res.statusCode() < 300) {
      logger.info("${req.method()} $urlSuffix ${cut(payload)} in $ms ms: ${cut(body)}")
      return parse(body, type)
    }
    else {
      logger.error("Failed ${req.method()} $urlSuffix ${cut(payload)} in $ms ms: ${res.statusCode()}: $body")
      errorHandler(res, body)
    }
  }

  private fun cut(s: String?) = if (s == null) "" else if (s.length <= maxLoggedLen) s else s.substring(0, maxLoggedLen) + "..."

  suspend fun <T> retryRequest(urlSuffix: String, type: KType, payload: String? = null, builder: RequestModifier): T {
    for (i in 0..retryCount) {
      try {
        return request(urlSuffix, type, payload, builder)
      } catch (e: IOException) {
        if (i < retryCount) {
          logger.error("Failed $urlSuffix, retry ${i + 1} after $retryAfter", e)
          delay(retryAfter.inWholeMilliseconds)
        }
        else {
          logger.error("Failed $urlSuffix: ${cut(payload)}", e)
          throw e
        }
      }
    }
    error("Unreachable")
  }

  suspend inline fun <reified T> request(urlSuffix: String, payload: String? = null, noinline builder: RequestModifier): T = retryRequest(urlSuffix, typeOf<T>(), payload, builder)

  suspend fun <T> get(urlSuffix: String, type: KType, modifier: RequestModifier? = null): T = retryRequest(urlSuffix, type) { GET().apply(modifier) }
  suspend inline fun <reified T> get(urlSuffix: String, noinline modifier: RequestModifier? = null): T = get(urlSuffix, typeOf<T>(), modifier)

  suspend fun <T> post(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let { retryRequest(urlSuffix, type, it) { POST(HttpRequest.BodyPublishers.ofString(it)).apply(modifier) } }
  suspend inline fun <reified T> post(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = post(urlSuffix, o, typeOf<T>(), modifier)

  suspend fun <T> put(urlSuffix: String, o: Any?, type: KType, modifier: RequestModifier? = null): T = render(o).let { retryRequest(urlSuffix, type, it) { PUT(HttpRequest.BodyPublishers.ofString(it)).apply(modifier) } }
  suspend inline fun <reified T> put(urlSuffix: String, o: Any?, noinline modifier: RequestModifier? = null): T = put(urlSuffix, o, typeOf<T>(), modifier)

  suspend fun <T> delete(urlSuffix: String, type: KType, modifier: RequestModifier? = null): T = retryRequest(urlSuffix, type) { DELETE().apply(modifier) }
  suspend inline fun <reified T> delete(urlSuffix: String, noinline modifier: RequestModifier? = null): T = delete(urlSuffix, typeOf<T>(), modifier)

  private fun HttpRequest.Builder.apply(modifier: RequestModifier?) = modifier?.let { it() } ?: this

  protected open fun render(o: Any?): String = o.toString()

  @Suppress("UNCHECKED_CAST")
  protected open fun <T> parse(body: String, type: KType): T = when (type.classifier) {
    Unit::class -> Unit as T
    else -> body as T
  }
}

fun HttpClient.Builder.connectTimeout(duration: Duration): HttpClient.Builder = connectTimeout(duration.toJavaDuration())
fun HttpRequest.Builder.timeout(duration: Duration): HttpRequest.Builder = timeout(duration.toJavaDuration())
