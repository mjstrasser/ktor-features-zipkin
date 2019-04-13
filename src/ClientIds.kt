package mjs.kotlin.features

import io.ktor.client.HttpClient
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.HeadersBuilder
import io.ktor.util.AttributeKey

class ClientIds(val traceAndSpan: TraceAndSpan) {

    class Configuration {
        lateinit var traceAndSpan: TraceAndSpan

        internal fun build(): ClientIds = ClientIds(traceAndSpan)
    }

    companion object Feature : HttpClientFeature<Configuration, ClientIds> {

        override val key: AttributeKey<ClientIds> = AttributeKey("ClientIds")

        override fun prepare(block: Configuration.() -> Unit): ClientIds = Configuration().apply(block).build()

        override fun install(feature: ClientIds, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                setHeaders(context.headers, feature.traceAndSpan)
            }
        }

        private fun setHeaders(headers: HeadersBuilder, traceAndSpan: TraceAndSpan) {
            if (traceAndSpan.b3Header) {
                headers.append(B3_HEADER, "${traceAndSpan.traceId}-${traceAndSpan.spanId}")
            } else {
                headers.append(TRACE_ID_HEADER, traceAndSpan.traceId)
                headers.append(SPAN_ID_HEADER, nextId())
            }
        }

    }
}
