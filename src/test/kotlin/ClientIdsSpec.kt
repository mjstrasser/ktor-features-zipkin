package mjs.ktor.features.zipkin

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.client.tests.utils.TestClientBuilder
import io.ktor.client.tests.utils.clientTest
import io.ktor.client.tests.utils.config
import io.ktor.client.tests.utils.test
import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@UseExperimental(InternalAPI::class)
internal object ClientIdsSpec : Spek({

    /** A mock Ktor engine that echoes the headers it receives. */
    val echoHandler: MockRequestHandler = { request: HttpRequestData ->
        respond(content = request.body.toString(), status = HttpStatusCode.OK, headers = request.headers)
    }
    val echoEngine = MockEngine(MockEngineConfig().apply { addHandler(echoHandler) })

    /** Configure a client with the specified [TracingParts] instance. */
    fun TestClientBuilder<*>.configParts(parts: TracingParts) {
        config {
            install(ClientIds) {
                tracingParts = parts
            }
        }
    }

    val traceId by memoized { nextId() }
    val spanId by memoized { nextId() }

    describe("setting headers into client requests") {
        describe("sets b3 header if specified") {
            it("with the received trace ID") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[B3_HEADER]!!.split("-")[0]).isEqualTo(traceId)
                    }
                }
            }
            it("with a new span ID") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[B3_HEADER]!!.split("-")[1]).isNotEqualTo(spanId)
                    }
                }
            }
            it("with parent span ID set to the received span ID") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[B3_HEADER]!!.split("-")[3]).isEqualTo(spanId)
                    }
                }
            }
            it("with sampled as received") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId, sampled = Sampled.ACCEPT)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[B3_HEADER]!!.split("-")[2]).isEqualTo("1")
                    }
                }
            }
        }
        describe("sets separate headers if specified") {
            it("with the received trace ID") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[TRACE_ID_HEADER]).isEqualTo(traceId)
                    }
                }
            }
            it("with a new span ID") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[SPAN_ID_HEADER]).isNotEqualTo(spanId)
                    }
                }
            }
            it("with parent span ID set to the received span ID") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[PARENT_SPAN_ID_HEADER]).isEqualTo(spanId)
                    }
                }
            }
            it("with sampled set to 1 when ACCEPT received") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId, sampled = Sampled.ACCEPT)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[SAMPLED_HEADER]).isEqualTo("1")
                    }
                }
            }
            it("with sampled set to 0 when DENY received") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId, sampled = Sampled.DENY)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers[SAMPLED_HEADER]).isEqualTo("0")
                    }
                }
            }
            it("with flags set to 1 when DEBUG received") {
                clientTest(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId, sampled = Sampled.DEBUG)
                    )
                    test { client ->
                        val headers = client.get<HttpResponse>().headers
                        assertThat(headers.contains(SAMPLED_HEADER)).isFalse()
                        assertThat(headers[DEBUG_HEADER]).isEqualTo("1")
                    }
                }
            }
        }
    }
})
