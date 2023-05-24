package mjs.ktor.features.zipkin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.*
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.HeadersBuilder
import io.ktor.util.AttributeKey

/**
 * Ktor client feature for setting Zipkin tracing headers into HTTP client requests.
 */
class ClientIds(val tracingParts: TracingParts) {

    /**
     * Configuration for [ClientIds].
     *
     * - [tracingParts]: the [TracingParts] instance with information to set into client headers.
     */
    class Configuration {
        lateinit var tracingParts: TracingParts

        internal fun build(): ClientIds = ClientIds(tracingParts)
    }

    /**
     * Installable feature of [ClientIds].
     */
    companion object Feature : HttpClientPlugin<Configuration, ClientIds> {

        override val key: AttributeKey<ClientIds> = AttributeKey("ClientIds")

        override fun prepare(block: Configuration.() -> Unit): ClientIds = Configuration().apply(block).build()

        override fun install(feature: ClientIds, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                setHeaders(context.headers, partsForClientCall(feature.tracingParts))
            }
        }

        /**
         * Generate a [TracingParts] instance for the client call based on the instance
         * configured.
         *
         * - The received trace ID is used.
         * - The received span ID becomes the parent span ID in the client.
         * - A new span ID is generated.
         * - The received sampling value is used.
         */
        private fun partsForClientCall(parts: TracingParts): TracingParts =
            TracingParts(parts.useB3Header, parts.traceId, nextId(), parts.spanId, parts.sampled)

        private fun setHeaders(headers: HeadersBuilder, tracingParts: TracingParts) {
            tracingParts.asHeaders().forEach { (name, value) ->
                headers.append(name, value)
            }
        }
    }

}
