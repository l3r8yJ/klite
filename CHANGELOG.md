# 1.4.3 (unreleased)
* core: the new core module, to make it possible to use jdbc module without the server
* core: mapOfNotNull() now accepts keys of any type
* core: Converter.supports() now finds supported converters automatically, even if not used previously
* jdbc: db.count() introduced
* jdbc: box @JvmInline classes when loading from DB
* jdbc: allow same column multiple times in or() and deprecate NullOrOp()
* server: do not wrap exceptions already containing parameter name in annotated routes
* server: TypedHttpClient introduced as a more generic foundation for JsonHttpClient

# 1.4.2
* server: fallback to `docker compose` without dash
* server: shorten json stack traces by cutting repeating class/package names
* jdbc: emptyArray literal support '{}' - replaces previous EmptyOf() handling in toValues()
* jdbc: jsonb() and stuff that should work for both insert and update must use SqlComputed() now
* jdbc: CrudRepository now skips null where tokens for easier conditionals
* jobs: do not allow running of same jobs in parallel by default - useful in case of multiple instances

# 1.4.1
* server: skip "broken pipe" and "connection reset" exceptions by default
* server: request log now contains exception name that caused the status code to change
* jdbc: orExpr() and or() functions for where expressions
* jackson: be able to override JavaTimeModule settings more easily

# 1.4.0
* server: MimeTypes is now a singleton object
* jdbc: DBMigrator introduced to replace Liquibase in most cases
* jdbc: ClosedRange and OpenRange support
* jdbc: SqlExpr implements equals for easier parameter-based mocking