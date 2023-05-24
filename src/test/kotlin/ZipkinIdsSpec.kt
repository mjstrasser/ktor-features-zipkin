package mjs.ktor.features.zipkin

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import mjs.ktor.features.zipkin.ZipkinIds.Plugin.tracingPartsKey

class ZipkinIdsSpec : DescribeSpec({

    data class Setup(val traceId: String, val spanId: String, val parentSpanId: String)

    fun setup() = Setup(nextId(), nextId(), nextId())

    describe("`ZipkinIds` feature") {
        describe("matches path prefixes") {
            it("initiates a trace when the path starts with a specified prefix") {
                withTestApplication {
                    application.install(ZipkinIds) {
                        initiateTracePathPrefixes = arrayOf("/api/v1", "/api/v3")
                    }
                    handleRequest(HttpMethod.Post, "/api/v1/service").apply {
                        request.call.attributes.contains(tracingPartsKey) shouldBe true
                    }
                }
            }
            it("does not initiate a trace when the path does not start with a specified prefix") {
                withTestApplication {
                    application.install(ZipkinIds) {
                        initiateTracePathPrefixes = arrayOf("/api/v1", "/api/v3")
                    }
                    handleRequest(HttpMethod.Post, "/health").apply {
                        request.call.attributes.contains(tracingPartsKey) shouldBe false
                    }
                    handleRequest(HttpMethod.Post, "/api/v2").apply {
                        request.call.attributes.contains(tracingPartsKey) shouldBe false
                    }
                }
            }
        }
        describe("returns HTTP headers from the request") {
            it("does not return headers if the feature is not installed") {
                withTestApplication {
                    handleRequest(HttpMethod.Get, "/").apply {
                        with(response.headers) {
                            get(B3_HEADER) shouldBe null
                            get(TRACE_ID_HEADER) shouldBe null
                            get(SPAN_ID_HEADER) shouldBe null
                            get(PARENT_SPAN_ID_HEADER) shouldBe null
                            get(SAMPLED_HEADER) shouldBe null
                            get(DEBUG_HEADER) shouldBe null
                        }
                    }
                }
            }
            it("returns a b3 header if present in request") {
                val (traceId, spanId, _) = setup()
                withTestApplication {
                    application.install(ZipkinIds)
                    handleRequest(HttpMethod.Get, "/") {
                        addHeader(B3_HEADER, "$traceId-$spanId")
                    }.apply {
                        with(response.headers) {
                            get(B3_HEADER) shouldBe "$traceId-$spanId"
                            get(TRACE_ID_HEADER) shouldBe null
                            get(SPAN_ID_HEADER) shouldBe null
                            get(PARENT_SPAN_ID_HEADER) shouldBe null
                            get(SAMPLED_HEADER) shouldBe null
                            get(DEBUG_HEADER) shouldBe null
                        }
                    }
                }
            }
            it("returns X-B3-TraceId and X-B3-SpanId headers if present in request") {
                val (traceId, spanId, _) = setup()
                withTestApplication {
                    application.install(ZipkinIds)
                    handleRequest(HttpMethod.Get, "/") {
                        addHeader(TRACE_ID_HEADER, traceId)
                        addHeader(SPAN_ID_HEADER, spanId)
                    }.apply {
                        with(response.headers) {
                            get(B3_HEADER) shouldBe null
                            get(TRACE_ID_HEADER) shouldBe traceId
                            get(SPAN_ID_HEADER) shouldBe spanId
                            get(PARENT_SPAN_ID_HEADER) shouldBe null
                            get(SAMPLED_HEADER) shouldBe null
                            get(DEBUG_HEADER) shouldBe null
                        }
                    }
                }
            }
        }
        describe("sets the attribute in the ApplicationCall") {
            it("does not set the attribute if the feature is not installed") {
                withTestApplication {
                    handleRequest(HttpMethod.Get, "/").apply {
                        request.call.attributes.getOrNull(tracingPartsKey) shouldBe null
                    }
                }
            }
            it("sets the the attribute if a b3 header is present in request") {
                val (traceId, spanId, _) = setup()
                withTestApplication {
                    application.install(ZipkinIds)
                    handleRequest(HttpMethod.Get, "/") {
                        addHeader(B3_HEADER, "$traceId-$spanId")
                    }.apply {
                        request.call.attributes[tracingPartsKey].let { attribute ->
                            attribute.traceId shouldBe traceId
                            attribute.spanId shouldBe spanId

                        }
                    }
                }
            }
            it("sets the the attribute if X-B3-TraceId and X-B3-SpanId headers are present in request") {
                val (traceId, spanId, _) = setup()
                withTestApplication {
                    application.install(ZipkinIds)
                    handleRequest(HttpMethod.Get, "/") {
                        addHeader(TRACE_ID_HEADER, traceId)
                        addHeader(SPAN_ID_HEADER, spanId)
                    }.apply {
                        request.call.attributes[tracingPartsKey].let { attribute ->
                            attribute.traceId shouldBe traceId
                            attribute.spanId shouldBe spanId

                        }
                    }
                }
            }
        }
        describe("may initiate tracing when the feature is installed") {
            it("does not initiate if the request does not match specified path prefixes") {
                withTestApplication {
                    application.install(ZipkinIds) {
                        initiateTracePathPrefixes = arrayOf("/api")
                    }
                    handleRequest(HttpMethod.Get, "/").apply {
                        request.call.attributes.getOrNull(tracingPartsKey) shouldBe null
                        with(response.headers) {
                            get(B3_HEADER) shouldBe null
                            get(TRACE_ID_HEADER) shouldBe null
                            get(SPAN_ID_HEADER) shouldBe null
                            get(PARENT_SPAN_ID_HEADER) shouldBe null
                            get(SAMPLED_HEADER) shouldBe null
                            get(DEBUG_HEADER) shouldBe null
                        }
                    }
                }
            }
            describe("initiates tracing for requests that match prefixes") {
                it("returns trace and span ID X-B3 headers if configured") {
                    withTestApplication {
                        application.install(ZipkinIds) {
                            b3Header = false
                        }
                        handleRequest(HttpMethod.Get, "/").apply {
                            with(response.headers) {
                                get(B3_HEADER) shouldBe null
                                get(TRACE_ID_HEADER) shouldNotBe null
                                get(SPAN_ID_HEADER) shouldNotBe null
                                get(PARENT_SPAN_ID_HEADER) shouldBe null
                                get(SAMPLED_HEADER) shouldBe null
                                get(DEBUG_HEADER) shouldBe null
                            }
                        }
                    }

                }
                it("returns a b3 header if configured") {
                    withTestApplication {
                        application.install(ZipkinIds) {
                            b3Header = true
                        }
                        handleRequest(HttpMethod.Get, "/").apply {
                            with(response.headers) {
                                get(B3_HEADER) shouldNotBe null
                                get(TRACE_ID_HEADER) shouldBe null
                                get(SPAN_ID_HEADER) shouldBe null
                                get(PARENT_SPAN_ID_HEADER) shouldBe null
                                get(SAMPLED_HEADER) shouldBe null
                                get(DEBUG_HEADER) shouldBe null
                            }
                        }
                    }

                }
                it("sets the ApplicationCall attribute") {
                    withTestApplication {
                        application.install(ZipkinIds)
                        handleRequest(HttpMethod.Get, "/").apply {
                            request.call.attributes.getOrNull(tracingPartsKey) shouldNotBe null
                        }
                    }

                }
            }
        }
    }
})
