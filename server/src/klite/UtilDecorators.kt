package klite

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

fun RouterConfig.enforceHttps(maxAge: Duration = 365.days) = before { e ->
  if (!e.isSecure) {
    e.header("Strict-Transport-Security", "max-age=${maxAge.inWholeSeconds}")
    e.redirect(e.fullUrl.toString().replace("http://", "https://"), StatusCode.PermanentRedirect)
  }
}

fun RouterConfig.useHashCodeAsETag() = decorator { e, handler ->
  e.handler()?.also { e.checkETagHashCode(it) }
}

fun HttpExchange.checkETagHashCode(o: Any) {
  if (eTagHashCode(o) == header("If-None-Match")) throw NotModifiedException()
}

fun HttpExchange.eTagHashCode(o: Any) = "W/\"${o.hashCode().toUInt().toString(36)}\"".also { header("ETag", it) }

fun HttpExchange.checkLastModified(at: Instant) {
  if (lastModified(at) == header("If-Modified-Since")) throw NotModifiedException()
}

fun HttpExchange.lastModified(at: Instant): String = DateTimeFormatter.RFC_1123_DATE_TIME.format(at.atOffset(ZoneOffset.UTC)).also {
  header("Last-Modified", it)
}
