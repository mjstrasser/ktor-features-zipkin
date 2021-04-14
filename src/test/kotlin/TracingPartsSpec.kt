package mjs.ktor.features.zipkin

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HeadersBuilder

class TracingPartsSpec : DescribeSpec({
    describe("Sampled: as header value") {
        it("converts DENY to 0") { Sampled.DENY.asHeader() shouldBe "0" }
        it("converts ACCEPT to 1") { Sampled.ACCEPT.asHeader() shouldBe "1" }
        it("converts DEBUG to d") { Sampled.DEBUG.asHeader() shouldBe "d" }
        it("converts anything else to ''") { Sampled.DEFER.asHeader() shouldBe "" }
    }
    describe("Sampled: parses header value") {
        it("from 0 to DENY") { Sampled.parse("0") shouldBe Sampled.DENY }
        it("from 1 to ACCEPT") { Sampled.parse("1") shouldBe Sampled.ACCEPT }
        it("from d to DEBUG") { Sampled.parse("d") shouldBe Sampled.DEBUG }
        it("from anything else to DEFER") { Sampled.parse("X") shouldBe Sampled.DEFER }
    }

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
                val traceId = nextId()
                val spanId = nextId()
                TracingParts.parse(
                    headers(b3 = "$traceId-$spanId")
                ) shouldBe TracingParts(
                    useB3Header = true, traceId = traceId, spanId = spanId
                )
            }
            it("of form TTTTT-SSSSS-S into trace and span IDs and sampled") {
                val traceId = nextId()
                val spanId = nextId()
                TracingParts.parse(
                    headers(b3 = "$traceId-$spanId-1")
                ) shouldBe TracingParts(
                    useB3Header = true, traceId = traceId, spanId = spanId, sampled = Sampled.ACCEPT
                )
            }
            it("of form TTTTT-SSSSS--PPPPP into trace, span and parent span IDs, with default DEFER sampled") {
                val traceId = nextId()
                val spanId = nextId()
                val parentSpanId = nextId()
                TracingParts.parse(
                    headers(b3 = "$traceId-$spanId--$parentSpanId")
                ) shouldBe TracingParts(
                    useB3Header = true, traceId = traceId, spanId = spanId,
                    parentSpanId = parentSpanId, sampled = Sampled.DEFER
                )
            }
            it("of value 0 into DENY sampled") {
                TracingParts.parse(
                    headers(b3 = "0")
                ) shouldBe TracingParts(
                    useB3Header = true, sampled = Sampled.DENY
                )
            }
            it("of value 1 into ACCEPT sampled") {
                TracingParts.parse(
                    headers(b3 = "1")
                ) shouldBe TracingParts(
                    useB3Header = true, sampled = Sampled.ACCEPT
                )
            }
            it("of value X into DEFER sampled") {
                TracingParts.parse(
                    headers(b3 = "X")
                ) shouldBe TracingParts(
                    useB3Header = true, sampled = Sampled.DEFER
                )
            }
        }
        describe("parses multiple X-B3 headers") {
            it("returns an empty instance if no headers are found") {
                TracingParts.parse(
                    headers()
                ) shouldBe TracingParts(
                    false, null, null, null, Sampled.DEFER
                )
            }
            it("parses X-B3-TraceId and X-B3-SpanId if only they are present") {
                val traceId = nextId()
                val spanId = nextId()
                TracingParts.parse(
                    headers(traceId = traceId, spanId = spanId)
                ) shouldBe TracingParts(
                    useB3Header = false, traceId = traceId, spanId = spanId
                )
            }
            it("parses X-B3-TraceId, X-B3-SpanId and X-B3-ParentSpanId if they are present") {
                val traceId = nextId()
                val spanId = nextId()
                val parentSpanId = nextId()
                TracingParts.parse(
                    headers(traceId = traceId, spanId = spanId, parentSpanId = parentSpanId)
                ) shouldBe TracingParts(
                    useB3Header = false, traceId = traceId, spanId = spanId, parentSpanId = parentSpanId
                )
            }
            it("parses X-B3-Sampled: 1 as ACCEPT") {
                TracingParts.parse(
                    headers(sampled = "1")
                ) shouldBe TracingParts(
                    useB3Header = false, sampled = Sampled.ACCEPT
                )
            }
            it("parses X-B3-Sampled: 0 as DENY") {
                TracingParts.parse(
                    headers(sampled = "0")
                ) shouldBe TracingParts(
                    useB3Header = false, sampled = Sampled.DENY
                )
            }
            it("parses X-B3-Flags: 1 as DEBUG") {
                TracingParts.parse(
                    headers(flags = "1")
                ) shouldBe TracingParts(
                    useB3Header = false, sampled = Sampled.DEBUG
                )
            }
        }
    }
    describe("TracingParts: to HTTP headers") {
        describe("creates b3 header") {
            it("of form TTTTT-SSSSS from only trace and span IDs") {
                val traceId = nextId()
                val spanId = nextId()
                TracingParts(
                    useB3Header = true, traceId = traceId, spanId = spanId
                ).asHeaders() shouldBe mapOf(
                    "b3" to "$traceId-$spanId"
                )
            }
            it("of form TTTTT-SSSSS-S from trace and span IDs and sampled") {
                val traceId = nextId()
                val spanId = nextId()
                TracingParts(
                    useB3Header = true, traceId = traceId,
                    spanId = spanId, sampled = Sampled.ACCEPT
                ).asHeaders() shouldBe mapOf(
                    "b3" to "$traceId-$spanId-1"
                )
            }
            it("of form TTTTT-SSSSS-S-PPPPP from trace, span and parent span IDs and sampled") {
                val traceId = nextId()
                val spanId = nextId()
                val parentSpanId = nextId()
                TracingParts(
                    useB3Header = true, traceId = traceId, spanId = spanId,
                    parentSpanId = parentSpanId, sampled = Sampled.DENY
                ).asHeaders() shouldBe mapOf("b3" to "$traceId-$spanId-0-$parentSpanId")
            }
            it("of form TTTTT-SSSSS--PPPPP from trace, span and parent span IDs and default DEFER sampled") {
                val traceId = nextId()
                val spanId = nextId()
                val parentSpanId = nextId()
                TracingParts(
                    useB3Header = true, traceId = traceId, spanId = spanId,
                    parentSpanId = parentSpanId, sampled = Sampled.DEFER
                ).asHeaders() shouldBe mapOf("b3" to "$traceId-$spanId--$parentSpanId")
            }
            it("of value 0 with DENY sampled") {
                TracingParts(
                    useB3Header = true, sampled = Sampled.DENY
                ).asHeaders() shouldBe mapOf("b3" to "0")
            }
        }
        describe("creates multiple headers") {
            it("X-B3-TraceId and X-B3-SpanId if only those values are set") {
                val traceId = nextId()
                val spanId = nextId()
                TracingParts(useB3Header = false, traceId = traceId, spanId = spanId).asHeaders() shouldBe mapOf(
                    TRACE_ID_HEADER to traceId,
                    SPAN_ID_HEADER to spanId
                )
            }
            it("X-B3-TraceId, X-B3-SpanId, X-B3-ParentSpanId and X-B3-Sampled if all are set") {
                val traceId = nextId()
                val spanId = nextId()
                val parentSpanId = nextId()
                TracingParts(
                    useB3Header = false, traceId = traceId, spanId = spanId,
                    parentSpanId = parentSpanId, sampled = Sampled.ACCEPT
                ).asHeaders() shouldBe mapOf(
                    TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId,
                    PARENT_SPAN_ID_HEADER to parentSpanId, SAMPLED_HEADER to "1"
                )
            }
            it("X-B3-TraceId, X-B3-SpanId and X-B3-ParentSpanId if they are set (DEFER sampled)") {
                val traceId = nextId()
                val spanId = nextId()
                val parentSpanId = nextId()
                TracingParts(
                    useB3Header = false, traceId = traceId, spanId = spanId,
                    parentSpanId = parentSpanId, sampled = Sampled.DEFER
                ).asHeaders() shouldBe mapOf(
                    TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId,
                    PARENT_SPAN_ID_HEADER to parentSpanId
                )
            }
            it("X-B3-TraceId, X-B3-SpanId, X-B3-ParentSpanId and X-B3-Flags if all are set") {
                val traceId = nextId()
                val spanId = nextId()
                val parentSpanId = nextId()
                TracingParts(
                    useB3Header = false, traceId = traceId, spanId = spanId,
                    parentSpanId = parentSpanId, sampled = Sampled.DEBUG
                ).asHeaders() shouldBe mapOf(
                    TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId,
                    PARENT_SPAN_ID_HEADER to parentSpanId, DEBUG_HEADER to "1"
                )
            }
            it("only X-B3-Sampled: 0 if sampled is DENY") {
                TracingParts(
                    useB3Header = false, sampled = Sampled.DENY
                ).asHeaders() shouldBe mapOf(
                    SAMPLED_HEADER to "0"
                )
            }
        }
    }
})
