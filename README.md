# Ktor: ZipkinIds feature 

[![Build Status](https://travis-ci.org/mjstrasser/ktor-features-zipkin.svg?branch=master)](https://travis-ci.org/mjstrasser/ktor-features-zipkin)

A [Ktor](https://ktor.io) installable feature for particiapting in [Zipkin](https://zipkin.io)
distributed tracing.

## Why might you want this?

You are building microservices in Ktor and want to instrument them for tracing using Zipkin.

## What does it do?

### Incoming headers

The feature reads incoming HTTP tracing headers, either multiple headers:

* `X-B3-TraceId`
* `X-B3-SpanId`
* `X-B3-ParentSpanId`
* `X-B3-Sampled` or `X-B3-Flags`

or a single `b3` header. See [zipkin-b3-propagation](https://github.com/apache/incubator-zipkin-b3-propagation)
for details.

### Initiating tracing

The feature initiates tracing if configured, optionally only for specified paths.

### Response headers

The feature returns tracing headers in the response that match those received or initiated.

### Client requests

The feature propagates tracing into downstream client requests where installed into Ktor clients.

### Logging MDC items

Optionally you can install the current tracing information into [Slf4j](https://slf4j.org)
mapped diagnostic context (MDC). 

## How do you use it?

There are two parts: a server feature and a client feature.

### Server feature

An example is:

```kotlin
fun Application.module() {

    install(ZipkinIds) {
        initiateTracePathPrefixes = arrayOf("/api")
        b3Header = true
    }

    // Other feature installations

    routing {
        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }

        post("/api/v1/service") {
            try {
                val message = call.receive<Message>()
                val result = call.processMessage(message)
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "There was an error processing your request")
            }
        }
    }
}
```

In this example, the feature is installed so it will initiate tracing only for requests starting with
`"/api"`. 

* Requests to `/health` without any tracing headers do not initiate tracing.
* Requests to `/api/v1/service` without tracing headers initiate tracing.
* Tracing initiated by the service respond with `b3` headers and use them in client calls.
* If requests contain tracing headers, those headers and their type (either `b3` or `X-B3-*`) will
  be maintained.
* Tracing information is stored in the [ApplicationCall](https://ktor.io/servers/calls.html) instance.

### Client feature

To propagate tracing information into client calls, define extension functions on
[ApplicationCall](https://ktor.io/servers/calls.html), for example:

```kotlin
suspend fun ApplicationCall.processMessage(message: Message) {

    val url = "https://provider.com/api/v1/other-service"
    try {
        val client = HttpClient(CIO) {
            tracingParts?.let { it ->
                install(ClientIds) {
                    tracingParts = it
                }
            }
        }
        val response = client.post<String>(url) {
            body = TextContent(
                json.writeValueAsString(OutgoingMessage(message)),
                contentType = ContentType.Application.Json
            )
        }
    } catch (e: Exception) {
        logger.error("Client request failure", e)
    }
}
```

In this example:

* When the client is being constructed, if the `ApplicationCall` contains a `tracingParts` attribute,
  install the `ClientIds` feature with that attribute.

### Logging MDC

Install the keys into logging MDC in the application as part of call logging:

```kotlin
    install(CallLogging) {
        level = Level.INFO
        zipkinMdc()
        filter { call -> call.request.path().startsWith("/") }
    }
```

Keys available are:

* `b3Id`
* `traceId`
* `spanId`
* `parentSpanId`

They can be set in `logback.xml` like so:

```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{YYYY-MM-dd HH:mm:ss.SSS} [%thread] [%X{b3Id}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

```

## Gradle dependency

```groovy
repositories {
    // ...
    maven { url 'https://kotlin.bintray.com/ktor' }
    maven { url 'https://dl.bintray.com/mjstrasser/maven'}
}

dependencies {
    // ...
    implementation 'com.michaelstrasser:ktor-features-zipkin:0.2.8'
}
```
