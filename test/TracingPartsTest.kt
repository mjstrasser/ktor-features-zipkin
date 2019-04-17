package mjs.ktor.features.zipkin

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.ktor.http.HeadersBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TracingPartsTest {
    private val traceId = nextId()
    private val spanId = nextId()
    private val parentSpanId = nextId()

    @Nested
    inner class B3HeaderParsing {

        @Test
        fun `should parse TTTTT-SSSSS into trace and span IDs`() {
            assertThat(TracingParts.parseB3Header("$traceId-$spanId")).isEqualTo(
                TracingParts(true, traceId, spanId)
            )
        }

        @Test
        fun `should parse TTTTT-SSSSS-S into trace and span IDs and sampled`() {
            assertThat(TracingParts.parseB3Header("$traceId-$spanId-1")).isEqualTo(
                TracingParts(true, traceId, spanId, null, Sampled.ACCEPT)
            )
        }

        @Test
        fun `should parse TTTTT-SSSSS-S-PPPPP into trace, span and parent span IDs, and sampled`() {
            assertThat(TracingParts.parseB3Header("$traceId-$spanId-0-$parentSpanId")).isEqualTo(
                TracingParts(true, traceId, spanId, parentSpanId, Sampled.DENY)
            )
            assertThat(TracingParts.parseB3Header("$traceId-$spanId-1-$parentSpanId")).isEqualTo(
                TracingParts(true, traceId, spanId, parentSpanId, Sampled.ACCEPT)
            )
            assertThat(TracingParts.parseB3Header("$traceId-$spanId-d-$parentSpanId")).isEqualTo(
                TracingParts(true, traceId, spanId, parentSpanId, Sampled.DEBUG)
            )
        }

        @Test
        fun `should parse TTTTT-SSSSS--PPPPP into trace, span and parent span IDs, with default DEFER sampled`() {
            assertThat(TracingParts.parseB3Header("$traceId-$spanId--$parentSpanId")).isEqualTo(
                TracingParts(true, traceId, spanId, parentSpanId, Sampled.DEFER)
            )
        }

        @Test
        fun `should parse 0 into DENY sampled`() {
            assertThat(TracingParts.parseB3Header("0")).isEqualTo(
                TracingParts(true, null, null, null, Sampled.DENY)
            )
        }
    }

    @Nested
    inner class MultipleHeaderParsing {
        @Test
        fun `should return empty TracingParts if no headers matched`() {
            assertThat(TracingParts.parse(HeadersBuilder().build())).isEqualTo(
                TracingParts(false, null, null, null, Sampled.DEFER)
            )
        }

        @Test
        fun `should parse X-B3-TraceId and X-B3-SpanId if only they are present`() {
            val headers = HeadersBuilder().apply {
                append(TRACE_ID_HEADER, traceId)
                append(SPAN_ID_HEADER, spanId)
            }.build()
            assertThat(TracingParts.parse(headers)).isEqualTo(
                TracingParts(false, traceId, spanId)
            )
        }

        @Test
        fun `should parse X-B3-TraceId, X-B3-SpanId and X-B3-ParentSpanId if present`() {
            val headers = HeadersBuilder().apply {
                append(TRACE_ID_HEADER, traceId)
                append(SPAN_ID_HEADER, spanId)
                append(PARENT_SPAN_ID_HEADER, parentSpanId)
            }.build()
            assertThat(TracingParts.parse(headers)).isEqualTo(
                TracingParts(false, traceId, spanId, parentSpanId, Sampled.DEFER)
            )
        }

        @Test
        fun `should parse X-B3-Sampled = 1 if present`() {
            val headers = HeadersBuilder().apply {
                append(TRACE_ID_HEADER, traceId)
                append(SPAN_ID_HEADER, spanId)
                append(PARENT_SPAN_ID_HEADER, parentSpanId)
                append(SAMPLED_HEADER, "1")
            }.build()
            assertThat(TracingParts.parse(headers)).isEqualTo(
                TracingParts(false, traceId, spanId, parentSpanId, Sampled.ACCEPT)
            )
        }

        @Test
        fun `should parse X-B3-Sampled = 0 if present`() {
            val headers = HeadersBuilder().apply {
                append(TRACE_ID_HEADER, traceId)
                append(SPAN_ID_HEADER, spanId)
                append(PARENT_SPAN_ID_HEADER, parentSpanId)
                append(SAMPLED_HEADER, "0")
            }.build()
            assertThat(TracingParts.parse(headers)).isEqualTo(
                TracingParts(false, traceId, spanId, parentSpanId, Sampled.DENY)
            )
        }

        @Test
        fun `should parse X-B3-Flags = 1 if present`() {
            val headers = HeadersBuilder().apply {
                append(TRACE_ID_HEADER, traceId)
                append(SPAN_ID_HEADER, spanId)
                append(PARENT_SPAN_ID_HEADER, parentSpanId)
                append(DEBUG_HEADER, "1")
            }.build()
            assertThat(TracingParts.parse(headers)).isEqualTo(
                TracingParts(false, traceId, spanId, parentSpanId, Sampled.DEBUG)
            )
        }
    }

    @Nested
    inner class B3HeaderCreation {
        @Test
        fun `should create a TTTTT-SSSSS header from only trace and span IDs`() {
            assertThat(TracingParts(true, traceId, spanId).asB3Header()).isEqualTo(
                "$traceId-$spanId"
            )
        }

        @Test
        fun `should create a TTTTT-SSSSS-S header from trace and span IDs and sampled`() {
            assertThat(TracingParts(true, traceId, spanId, null, Sampled.ACCEPT).asB3Header()).isEqualTo(
                "$traceId-$spanId-1"
            )
        }

        @Test
        fun `should create a TTTTT-SSSSS-S-PPPPP header from trace, span and parent IDs and sampled`() {
            assertThat(TracingParts(true, traceId, spanId, parentSpanId, Sampled.ACCEPT).asB3Header()).isEqualTo(
                "$traceId-$spanId-1-$parentSpanId"
            )
        }

        @Test
        fun `should create a TTTTT-SSSSS--PPPPP header from trace, span and parent IDs with default DEFER sampled`() {
            assertThat(TracingParts(true, traceId, spanId, parentSpanId, Sampled.DEFER).asB3Header()).isEqualTo(
                "$traceId-$spanId--$parentSpanId"
            )
        }

        @Test
        fun `should create a 0 header with only DENY sampled`() {
            assertThat(TracingParts(true, null, null, null, Sampled.DENY).asB3Header()).isEqualTo(
                "0"
            )
        }
    }

    @Nested
    inner class MultipleHeaderCreation {
        @Test
        fun `should create only trace ID and span ID headers if only they are set`() {
            assertThat(TracingParts(false, traceId, spanId).asHeaders()).isEqualTo(
                mapOf(TRACE_ID_HEADER to traceId, SPAN_ID_HEADER to spanId)
            )
        }

        @Test
        fun `should create trace ID, span ID, sampled and parent span ID headers if all are set`() {
            assertThat(TracingParts(false, traceId, spanId, parentSpanId, Sampled.ACCEPT).asHeaders()).isEqualTo(
                mapOf(
                    TRACE_ID_HEADER to traceId,
                    SPAN_ID_HEADER to spanId,
                    PARENT_SPAN_ID_HEADER to parentSpanId,
                    SAMPLED_HEADER to "1"
                )
            )
        }

        @Test
        fun `should create trace ID, span ID  and parent span ID headers they are set (defer sampled)`() {
            assertThat(TracingParts(false, traceId, spanId, parentSpanId, Sampled.DEFER).asHeaders()).isEqualTo(
                mapOf(
                    TRACE_ID_HEADER to traceId,
                    SPAN_ID_HEADER to spanId,
                    PARENT_SPAN_ID_HEADER to parentSpanId
                )
            )
        }

        @Test
        fun `should create trace ID, span ID, debug and parent span ID headers if they are set`() {
            assertThat(TracingParts(false, traceId, spanId, parentSpanId, Sampled.DEBUG).asHeaders()).isEqualTo(
                mapOf(
                    TRACE_ID_HEADER to traceId,
                    SPAN_ID_HEADER to spanId,
                    PARENT_SPAN_ID_HEADER to parentSpanId,
                    DEBUG_HEADER to "1"
                )
            )
        }

        @Test
        fun `should create only a sampled header with 0 if sampled is denied`() {
            assertThat(TracingParts(false, null, null, null, Sampled.DENY).asHeaders()).isEqualTo(
                mapOf(
                    SAMPLED_HEADER to "0"
                )
            )
        }
    }
}