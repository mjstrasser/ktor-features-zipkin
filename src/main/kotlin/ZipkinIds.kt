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

/**
 * Ktor feature that handles Zipkin headers for tracing.
 */
class ZipkinIds {

    /**
     * Configuration for [ZipkinIds].
     *
     * - [b3Header]: generate `b3` headers instead of separate `X-B3` headers.
     * - [idLength]: generate either 64-bit (default) or 128-bit trace ID values (@see
     *   [nextId] and [IdLength]).
     * - [initiateTracePathPrefixes]: only initiate tracing if the request path
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
