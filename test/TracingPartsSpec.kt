package mjs.ktor.features.zipkin

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.ktor.http.HeadersBuilder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal object TracingPartsSpec : Spek({
    val traceId by memoized { nextId() }
    val spanId by memoized { nextId() }
    val parentSpanId by memoized { nextId() }

    describe("TracingParts: from HTTP headers") {
        fun headers(
            b3: String? = null,
            traceId: String? = null,
            spanId: String? = null,
            parentSpanId: String? = null,
            sampled: String? = null,
            flags: String? = null
        ) =
            HeadersBuilder().apply {
                b3?.let { append(B3_HEADER, b3) }
                traceId?.let { append(TRACE_ID_HEADER, traceId) }
                spanId?.let { append(SPAN_ID_HEADER, spanId) }
                parentSpanId?.let { append(PARENT_SPAN_ID_HEADER, parentSpanId) }
                sampled?.let { append(SAMPLED_HEADER, sampled) }
                flags?.let { append(DEBUG_HEADER, flags) }
            }.build()

        describe("parses b3 headers") {
            it("of form TTTTT-SSSSS into trace and span IDs") {
                assertThat(
                    TracingParts.parse(headers(b3 = "$traceId-$spanId"))
                ).isEqualTo(
                    TracingParts(useB3Header = true, traceId = traceId, spanId = spanId)
                )
            }
            it("of form TTTTT-SSSSS-S into trace and span IDs and sampled") {
                assertThat(
                    TracingParts.parse(headers(b3 = "$traceId-$spanId-1"))
                ).isEqualTo(
                    TracingParts(useB3Header = true, traceId = traceId, spanId = spanId, sampled = Sampled.ACCEPT)
                )
            }
            it("of form TTTTT-SSSSS--PPPPP into trace, span and parent span IDs, with default DEFER sampled") {
                assertThat(
                    TracingParts.parse(headers(b3 = "$traceId-$spanId--$parentSpanId"))
                ).isEqualTo(
                    TracingParts(
                        useB3Header = true, traceId = traceId, spanId = spanId,
                        parentSpanId = parentSpanId, sampled = Sampled.DEFER
                    )
                )
            }
            it("of value 0 into DENY sampled") {
                assertThat(
                    TracingParts.parse(headers(b3 = "0"))
                ).isEqualTo(
                    TracingParts(useB3Header = true, sampled = Sampled.DENY)
                )
            }
        }
        describe("parses multiple X-B3 headers") {
            it("returns an empty instance if no headers are found") {
                assertThat(
                    TracingParts.parse(
                        headers()
                    )
                ).isEqualTo(
                    TracingParts(false, null, null, null, Sampled.DEFER)
                )
            }
            it("parses X-B3-TraceId and X-B3-SpanId if only they are present") {
                assertThat(
                    TracingParts.parse(
                        headers(traceId = traceId, spanId = spanId)
                    )
                ).isEqualTo(
                    TracingParts(useB3Header = false, traceId = traceId, spanId = spanId)
                )
            }
            it("parses X-B3-TraceId, X-B3-SpanId and X-B3-ParentSpanId if they are present") {
                assertThat(
                    TracingParts.parse(headers(traceId = traceId, spanId = spanId, parentSpanId = parentSpanId))
                ).isEqualTo(
                    TracingParts(useB3Header = false, traceId = traceId, spanId = spanId, parentSpanId = parentSpanId)
                )
            }
            it("parses X-B3-Sampled: 1 as ACCEPT") {
                assertThat(
                    TracingParts.parse(headers(sampled = "1"))
                ).isEqualTo(
                    TracingParts(useB3Header = false, sampled = Sampled.ACCEPT)
                )
            }
            it("parses X-B3-Sampled: 0 as DENY") {
                assertThat(
                    TracingParts.parse(headers(sampled = "0"))
                ).isEqualTo(
                    TracingParts(useB3Header = false, sampled = Sampled.DENY)
                )
            }
            it("parses X-B3-Flags: 1 as DEBUG") {
                assertThat(
                    TracingParts.parse(headers(flags = "1"))
                ).isEqualTo(
                    TracingParts(useB3Header = false, sampled = Sampled.DEBUG)
                )
            }
        }
    }
    describe("TracingParts: to HTTP headers") {
        describe("creates b3 header") {
            it("of form TTTTT-SSSSS from only trace and span IDs") {
                assertThat(
                    TracingParts(useB3Header = true, traceId = traceId, spanId = spanId).asHeaders()
                ).isEqualTo(
                    mapOf("b3" to "$traceId-$spanId")
                )
            }
            it("of form TTTTT-SSSSS-S from trace and span IDs and sampled") {
                assertThat(
                    TracingParts(
                        useB3Header = true, traceId = traceId,
                        spanId = spanId, sampled = Sampled.ACCEPT
                    ).asHeaders()
                ).isEqualTo(
                    mapOf("b3" to "$traceId-$spanId-1")
                )
            }
            it("of form TTTTT-SSSSS-S-PPPPP from trace, span and parent span IDs and sampled") {
                assertThat(
                    TracingParts(
                        useB3Header = true, traceId = traceId, spanId = spanId,
                        parentSpanId = parentSpanId, sampled = Sampled.DENY
                    ).asHeaders()
                ).isEqualTo(
                    mapOf("b3" to "$traceId-$spanId-0-$parentSpanId")
                )
            }
            it("of form TTTTT-SSSSS--PPPPP from trace, span and parent span IDs and default DEFER sampled") {
                assertThat(
                    TracingParts(
                        useB3Header = true, traceId = traceId, spanId = spanId,
                        parentSpanId = parentSpanId, sampled = Sampled.DEFER
                    ).asHeaders()
                ).isEqualTo(
                    mapOf("b3" to "$traceId-$spanId--$parentSpanId")
                )
            }
            it("of value 0 with DENY sampled") {
                assertThat(
                    TracingParts(
                        useB3Header = true, sampled = Sampled.DENY
                    ).asHeaders()
                ).isEqualTo(
                    mapOf("b3" to "0")
                )
            }
        }
        describe("creates multiple headers") {
            it("X-B3-TraceId and X-B3-SpanId if only those values are set") {
                assertThat(
                    TracingParts(useB3Header = false, traceId = traceId, spanId = spanId).asHeaders()
                ).isEqualTo(
                    mapOf(TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId)
                )
            }
            it("X-B3-TraceId, X-B3-SpanId, X-B3-ParentSpanId and X-B3-Sampled if all are set") {
                assertThat(
                    TracingParts(
                        useB3Header = false, traceId = traceId, spanId = spanId,
                        parentSpanId = parentSpanId, sampled = Sampled.ACCEPT
                    ).asHeaders()
                ).isEqualTo(
                    mapOf(
                        TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId,
                        PARENT_SPAN_ID_HEADER to parentSpanId, SAMPLED_HEADER to "1"
                    )
                )
            }
            it("X-B3-TraceId, X-B3-SpanId and X-B3-ParentSpanId if they are set (DEFER sampled)") {
                assertThat(
                    TracingParts(
                        useB3Header = false, traceId = traceId, spanId = spanId,
                        parentSpanId = parentSpanId, sampled = Sampled.DEFER
                    ).asHeaders()
                ).isEqualTo(
                    mapOf(
                        TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId,
                        PARENT_SPAN_ID_HEADER to parentSpanId
                    )
                )
            }
            it("X-B3-TraceId, X-B3-SpanId, X-B3-ParentSpanId and X-B3-Flags if all are set") {
                assertThat(
                    TracingParts(
                        useB3Header = false, traceId = traceId, spanId = spanId,
                        parentSpanId = parentSpanId, sampled = Sampled.DEBUG
                    ).asHeaders()
                ).isEqualTo(
                    mapOf(
                        TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId,
                        PARENT_SPAN_ID_HEADER to parentSpanId, DEBUG_HEADER to "1"
                    )
                )
            }
            it("only X-B3-Sampled: 0 if sampled is DENY") {
                assertThat(
                    TracingParts(
                        useB3Header = false, sampled = Sampled.DENY
                    ).asHeaders()
                ).isEqualTo(
                    mapOf(
                        SAMPLED_HEADER to "0"
                    )
                )
            }
        }
    }
})
