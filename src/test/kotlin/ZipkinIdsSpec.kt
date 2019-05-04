package mjs.ktor.features.zipkin

import assertk.assertThat
import assertk.assertions.*
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import mjs.ktor.features.zipkin.ZipkinIds.Feature.tracingPartsKey
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal object ZipkinIdsSpec : Spek({
    val traceId by memoized { nextId() }
    val spanId by memoized { nextId() }

    describe("matches path prefixes") {
        it("initiates a trace when the path starts with a specified prefix") {
            withTestApplication {
                application.install(ZipkinIds) {
                    initiateTracePathPrefixes = arrayOf("/api/v1", "/api/v3")
                }
                handleRequest(HttpMethod.Post, "/api/v1/service").apply {
                    assertThat(request.call.attributes.contains(tracingPartsKey)).isTrue()
                }
            }
        }
        it("does not initiate a trace when the path does not start with a specified prefix") {
            withTestApplication {
                application.install(ZipkinIds) {
                    initiateTracePathPrefixes = arrayOf("/api/v1", "/api/v3")
                }
                handleRequest(HttpMethod.Post, "/health").apply {
                    assertThat(request.call.attributes.contains(tracingPartsKey)).isFalse()
                }
                handleRequest(HttpMethod.Post, "/api/v2").apply {
                    assertThat(request.call.attributes.contains(tracingPartsKey)).isFalse()
                }
            }
        }
    }
    describe("returns HTTP headers from the request") {
        it("does not return headers if the feature is not installed") {
            withTestApplication {
                handleRequest(HttpMethod.Get, "/").apply {
                    with(response.headers) {
                        assertThat(get(B3_HEADER)).isNull()
                        assertThat(get(TRACE_ID_HEADER)).isNull()
                        assertThat(get(SPAN_ID_HEADER)).isNull()
                        assertThat(get(PARENT_SPAN_ID_HEADER)).isNull()
                        assertThat(get(SAMPLED_HEADER)).isNull()
                        assertThat(get(DEBUG_HEADER)).isNull()
                    }
                }
            }
        }
        it("returns a b3 header if present in request") {
            withTestApplication {
                application.install(ZipkinIds)
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(B3_HEADER, "$traceId-$spanId")
                }.apply {
                    with(response.headers) {
                        assertThat(get(B3_HEADER)).isEqualTo("$traceId-$spanId")
                        assertThat(get(TRACE_ID_HEADER)).isNull()
                        assertThat(get(SPAN_ID_HEADER)).isNull()
                        assertThat(get(PARENT_SPAN_ID_HEADER)).isNull()
                        assertThat(get(SAMPLED_HEADER)).isNull()
                        assertThat(get(DEBUG_HEADER)).isNull()
                    }
                }
            }
        }
        it("returns X-B3-TraceId and X-B3-SpanId headers if present in request") {
            withTestApplication {
                application.install(ZipkinIds)
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(TRACE_ID_HEADER, traceId)
                    addHeader(SPAN_ID_HEADER, spanId)
                }.apply {
                    with(response.headers) {
                        assertThat(get(B3_HEADER)).isNull()
                        assertThat(get(TRACE_ID_HEADER)).isEqualTo(traceId)
                        assertThat(get(SPAN_ID_HEADER)).isEqualTo(spanId)
                        assertThat(get(PARENT_SPAN_ID_HEADER)).isNull()
                        assertThat(get(SAMPLED_HEADER)).isNull()
                        assertThat(get(DEBUG_HEADER)).isNull()
                    }
                }
            }
        }
    }
    describe("sets the attribute in the ApplicationCall") {
        it("does not set the attribute if the feature is not installed") {
            withTestApplication {
                handleRequest(HttpMethod.Get, "/").apply {
                    assertThat(request.call.attributes.getOrNull(tracingPartsKey)).isNull()
                }
            }
        }
        it("sets the the attribute if a b3 header is present in request") {
            withTestApplication {
                application.install(ZipkinIds)
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(B3_HEADER, "$traceId-$spanId")
                }.apply {
                    request.call.attributes[tracingPartsKey].let { attribute ->
                        assertThat(attribute.traceId).isEqualTo(traceId)
                        assertThat(attribute.spanId).isEqualTo(spanId)

                    }
                }
            }
        }
        it("sets the the attribute if X-B3-TraceId and X-B3-SpanId headers are present in request") {
            withTestApplication {
                application.install(ZipkinIds)
                handleRequest(HttpMethod.Get, "/") {
                    addHeader(TRACE_ID_HEADER, traceId)
                    addHeader(SPAN_ID_HEADER, spanId)
                }.apply {
                    request.call.attributes[tracingPartsKey].let { attribute ->
                        assertThat(attribute.traceId).isEqualTo(traceId)
                        assertThat(attribute.spanId).isEqualTo(spanId)

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
                    assertThat(request.call.attributes.getOrNull(tracingPartsKey)).isNull()
                    with(response.headers) {
                        assertThat(get(B3_HEADER)).isNull()
                        assertThat(get(TRACE_ID_HEADER)).isNull()
                        assertThat(get(SPAN_ID_HEADER)).isNull()
                        assertThat(get(PARENT_SPAN_ID_HEADER)).isNull()
                        assertThat(get(SAMPLED_HEADER)).isNull()
                        assertThat(get(DEBUG_HEADER)).isNull()
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
                            assertThat(get(B3_HEADER)).isNull()
                            assertThat(get(TRACE_ID_HEADER)).isNotNull()
                            assertThat(get(SPAN_ID_HEADER)).isNotNull()
                            assertThat(get(PARENT_SPAN_ID_HEADER)).isNull()
                            assertThat(get(SAMPLED_HEADER)).isNull()
                            assertThat(get(DEBUG_HEADER)).isNull()
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
                            assertThat(get(B3_HEADER)).isNotNull()
                            assertThat(get(TRACE_ID_HEADER)).isNull()
                            assertThat(get(SPAN_ID_HEADER)).isNull()
                            assertThat(get(PARENT_SPAN_ID_HEADER)).isNull()
                            assertThat(get(SAMPLED_HEADER)).isNull()
                            assertThat(get(DEBUG_HEADER)).isNull()
                        }
                    }
                }

            }
            it("sets the ApplicationCall attribute") {
                withTestApplication {
                    application.install(ZipkinIds)
                    handleRequest(HttpMethod.Get, "/").apply {
                        assertThat(request.call.attributes.getOrNull(tracingPartsKey)).isNotNull()
                    }
                }

            }
        }
    }
})
