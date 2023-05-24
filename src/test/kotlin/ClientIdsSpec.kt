package mjs.ktor.features.zipkin

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.tests.utils.TestClientBuilder
import io.ktor.client.tests.utils.config
import io.ktor.client.tests.utils.test
import io.ktor.client.tests.utils.testWithEngine
import io.ktor.http.HttpStatusCode

class ClientIdsSpec : DescribeSpec({

    data class Setup(val traceId: String, val spanId: String, val echoEngine: MockEngine)

    /** A mock Ktor engine that echoes the headers it receives. */
    val echoHandler: MockRequestHandler = { request: HttpRequestData ->
        respond(content = request.body.toString(), status = HttpStatusCode.OK, headers = request.headers)
    }

    fun setup() = Setup(nextId(), nextId(), MockEngine(MockEngineConfig().apply { addHandler(echoHandler) }))

    /** Configure a client with the specified [TracingParts] instance. */
    fun TestClientBuilder<*>.configParts(parts: TracingParts) {
        config {
            install(ClientIds) {
                tracingParts = parts
            }
        }
    }

    describe("`ClientIds` feature: setting headers into client requests") {
        describe("sets b3 header if specified") {
            it("with the received trace ID") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[B3_HEADER]!!.split("-")[0] shouldBe traceId
                    }
                }
            }
            it("with a new span ID") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[B3_HEADER]!!.split("-")[1] shouldNotBe spanId
                    }
                }
            }
            it("with parent span ID set to the received span ID") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[B3_HEADER]!!.split("-")[3] shouldBe spanId
                    }
                }
            }
            it("with sampled as received") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = true, traceId = traceId, spanId = spanId, sampled = Sampled.ACCEPT)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[B3_HEADER]!!.split("-")[2] shouldBe "1"
                    }
                }
            }
        }
        describe("sets separate headers if specified") {
            it("with the received trace ID") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[TRACE_ID_HEADER] shouldBe traceId
                    }
                }
            }
            it("with a new span ID") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[SPAN_ID_HEADER] shouldNotBe spanId
                    }
                }
            }
            it("with parent span ID set to the received span ID") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[PARENT_SPAN_ID_HEADER] shouldBe spanId
                    }
                }
            }
            it("with sampled set to 1 when ACCEPT received") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId, sampled = Sampled.ACCEPT)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[SAMPLED_HEADER] shouldBe "1"
                    }
                }
            }
            it("with sampled set to 0 when DENY received") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId, sampled = Sampled.DENY)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers[SAMPLED_HEADER] shouldBe "0"
                    }
                }
            }
            it("with flags set to 1 when DEBUG received") {
                val (traceId, spanId, echoEngine) = setup()
                testWithEngine(echoEngine) {
                    configParts(
                        TracingParts(useB3Header = false, traceId = traceId, spanId = spanId, sampled = Sampled.DEBUG)
                    )
                    test { client ->
                        val headers = client.get("").headers
                        headers.contains(SAMPLED_HEADER) shouldBe false
                        headers[DEBUG_HEADER] shouldBe "1"
                    }
                }
            }
        }
    }
})
