package mjs.ktor.features

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.Headers
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext

const val TRACE_ID_HEADER = "X-B3-TraceId"
const val SPAN_ID_HEADER = "X-B3-SpanId"
const val PARENT_SPAN_ID_HEADER = "X-B3-ParentSpanId"
const val B3_HEADER = "b3"

data class TraceIds(val traceId: String, val spanId: String, val parentSpanId: String?)

class ZipkinIds(configuration: Configuration) {

    private val b3Header = configuration.b3Header

    // Feature configuration class
    class Configuration {
        var b3Header = false

    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, ZipkinIds.Configuration, ZipkinIds> {

        override val key = AttributeKey<ZipkinIds>("ZipkinIds")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ZipkinIds {
            // Call user code to configure a feature
            val configuration = Configuration().apply(configure)

            // Create a feature instance
            val feature = ZipkinIds(configuration)

            // Install an interceptor that will be run on each call and call feature instance
            pipeline.intercept(ApplicationCallPipeline.Call) {
                feature.intercept(this)
            }

            // Return a feature instance so that client code can use it
            return feature
        }
    }

    val traceIdsKey = AttributeKey<TraceIds>("TraceIds")

    // Body of the feature
    private fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val traceIds = readRequestIds(context.call.request.headers)
        if (traceIds != null) {
            context.call.attributes.put(traceIdsKey, traceIds)
        }
    }

    private fun readRequestIds(headers: Headers): TraceIds? {
        val b3Header = headers.get(B3_HEADER)
        if (b3Header != null) {
            val ids = b3Header.split("-")
            val traceId = ids[0]
            val spanId = ids[1]
            val parentSpanId = if (ids.size > 2) ids[2] else null
            return TraceIds(traceId, spanId, parentSpanId)
        }
        val traceId = headers.get(TRACE_ID_HEADER)
        val spanId = headers.get(SPAN_ID_HEADER)
        if (traceId != null && spanId != null) {
            val parentSpanId = headers.get(PARENT_SPAN_ID_HEADER)
            return TraceIds(traceId, spanId, parentSpanId)
        }
        return null
    }
}
