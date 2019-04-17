package mjs.ktor.features.zipkin

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.features.CallLogging
import io.ktor.http.Headers
import io.ktor.request.path
import io.ktor.response.ApplicationResponse
import io.ktor.response.header
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import mjs.ktor.features.zipkin.ZipkinIds.Configuration
import kotlin.random.Random

private val random = Random(System.nanoTime())

enum class IdLength { ID_64_BITS, ID_128_BITS }

fun nextId(idLength: IdLength = IdLength.ID_64_BITS) = when (idLength) {
    IdLength.ID_64_BITS -> String.format("%016x", random.nextLong())
    IdLength.ID_128_BITS -> String.format("%016x%016x", random.nextLong(), random.nextLong())
}

/**
 * Ktor feature that handles Zipkin headers for trace ID and span ID. It behaves similarly to
 * [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth).
 *
 * When the feature is installed, headers are handled as follows:
 *
 * - If the request contains X-B3-TraceId and X-B3-SpanId headers, set the same headers
 *   into the response.
 *
 * - If the request contains a b3 header, set the same header into the response.
 *
 * - If the request does not contain any of these headers and its path begins with
 *   one of the configured prefixes, set new ID values
 *   into either X-B3-TraceId and X-B3-SpanId, or a b3 header, by configuration.
 *
 * @see [Configuration]
 */
class ZipkinIds {

    /**
     * Configuration for ZipkinIds.
     *
     * - [b3Header]: generate `b3` headers instead of `X-B3-TraceId` and `X-B3-SpanId` headers.
     * - [idLength]: generate either 64-bit (default) or 128-bit trace ID values (@see [IdLength]).
     * - [initiateTracePathPrefixes]: only generate trace and span ID values if the request path
     *   begins with one of the specified prefixes.
     */
    class Configuration {
        var b3Header = false
        var idLength = IdLength.ID_64_BITS
        var initiateTracePathPrefixes = arrayOf("/")
    }

    /**
     * Installable feature for [ZipkinIds].
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, ZipkinIds> {

        val tracingPartsKey = AttributeKey<TracingParts>("tracingParts")

        override val key = AttributeKey<ZipkinIds>("ZipkinIds")

        /**
         * Phase of [ApplicationCallPipeline] into which the feature is installed.
         */
        private val phase: PipelinePhase = PipelinePhase("ZipkinIds")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ZipkinIds {
            val configuration = Configuration().apply(configure)
            val instance = ZipkinIds()
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Setup, phase)

            pipeline.intercept(phase) {
                val call = call // Copied from ktor code: not sure why we do this
                val traceAndSpan = readIdsFromRequest(call.request.headers) ?: generateIdsOnPathMatch(
                    call.request.path(),
                    configuration
                )
                if (traceAndSpan != null) {
                    call.attributes.put(tracingPartsKey, traceAndSpan)
                    setHeaders(call.response, traceAndSpan)
                }
            }

            return instance
        }

        private fun readIdsFromRequest(headers: Headers): TracingParts? {
            val parts = TracingParts.parse(headers)
            return if (parts.isEmpty()) null else parts
        }

        private fun generateIdsOnPathMatch(path: String, configuration: Configuration) =
            if (foundPrefixMatch(path, configuration.initiateTracePathPrefixes)) {
                TracingParts(configuration.b3Header, nextId(configuration.idLength), nextId())
            } else null

        private fun foundPrefixMatch(path: String, prefixes: Array<String>) =
            prefixes.map { prefix -> path.startsWith(prefix) }
                .fold(false) { acc, startsWith -> acc || startsWith }

        private fun setHeaders(response: ApplicationResponse, tracingParts: TracingParts) {
            tracingParts.asHeaders().forEach { (name, value) ->
                response.header(name, value)
            }
        }
    }
}

/**
 * A [TracingParts] that is retrieved or or set by [ZipkinIds] feature or `null`.
 */
val ApplicationCall.tracingParts: TracingParts? get() = attributes.getOrNull(ZipkinIds.tracingPartsKey)

/**
 * Keys for Slf4j MDC.
 */
const val TRACE_ID_KEY = "traceId"
const val SPAN_ID_KEY = "spanId"
const val PARENT_SPAN_ID_KEY = "parentSpanId"
const val B3_ID = "b3Id"

/**
 * Put the Zipkin IDs into the logging MDC.
 */
fun CallLogging.Configuration.zipkinMdc() {
    mdc(TRACE_ID_KEY) { it.tracingParts?.traceId }
    mdc(SPAN_ID_KEY) { it.tracingParts?.spanId }
    mdc(PARENT_SPAN_ID_KEY) { it.tracingParts?.parentSpanId }
    mdc(B3_ID) { it.tracingParts?.asB3Header() }
}
