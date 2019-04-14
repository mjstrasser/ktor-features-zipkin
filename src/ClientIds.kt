package mjs.ktor.features.zipkin

import io.ktor.client.HttpClient
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.HeadersBuilder
import io.ktor.util.AttributeKey

class ClientIds(val tracingParts: TracingParts) {

    class Configuration {
        lateinit var tracingParts: TracingParts

        internal fun build(): ClientIds = ClientIds(tracingParts)
    }

    companion object Feature : HttpClientFeature<Configuration, ClientIds> {

        override val key: AttributeKey<ClientIds> = AttributeKey("ClientIds")

        override fun prepare(block: Configuration.() -> Unit): ClientIds = Configuration().apply(block).build()

        override fun install(feature: ClientIds, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                setHeaders(context.headers, feature.tracingParts)
            }
        }

        private fun setHeaders(headers: HeadersBuilder, tracingParts: TracingParts) {
            if (tracingParts.b3Header) {
                headers.append(B3_HEADER, "${tracingParts.traceId}-${nextId()}")
            } else {
//                headers.append(TRACE_ID_HEADER, tracingParts.traceId)
                headers.append(SPAN_ID_HEADER, nextId())
            }
        }

    }
}
