package mjs.ktor.features.zipkin

import io.ktor.http.Headers

const val B3_HEADER = "b3"
const val TRACE_ID_HEADER = "X-B3-TraceId"
const val SPAN_ID_HEADER = "X-B3-SpanId"
const val PARENT_SPAN_ID_HEADER = "X-B3-ParentSpanId"
const val SAMPLED_HEADER = "X-B3-Sampled"
const val DEBUG_HEADER = "X-B3-Flags"

enum class Sampled {
    DEFER, DENY, ACCEPT, DEBUG;

    fun asHeader(): String = when (this) {
        DENY -> "0"
        ACCEPT -> "1"
        DEBUG -> "d"
        else -> ""
    }

    companion object {
        fun parse(sampled: String?) = when (sampled) {
            "0" -> DENY
            "1" -> ACCEPT
            "d" -> DEBUG
            else -> DEFER
        }
    }
}

class B3HeaderParseException(message: String) : Exception(message)

data class TracingParts(
    val useB3Header: Boolean,
    val traceId: String? = null,
    val spanId: String? = null,
    val parentSpanId: String? = null,
    val sampled: Sampled = Sampled.DEFER
) {
    companion object {
        fun parseB3Header(header: String): TracingParts {
            val parts = header.split("-")
            return when (parts.size) {
                1 -> TracingParts(true, sampled = Sampled.parse(parts[0]))
                2 -> TracingParts(true, parts[0], parts[1])
                4 -> TracingParts(true, parts[0], parts[1], parts[3], Sampled.parse(parts[2]))
                else -> throw B3HeaderParseException("Header 'b3: $header' could not be parsed into parts")
            }
        }

        fun parse(headers: Headers): TracingParts =
            headers[B3_HEADER]?.let {
                parseB3Header(it)
            } ?: TracingParts(
                false,
                headers[TRACE_ID_HEADER],
                headers[SPAN_ID_HEADER],
                headers[PARENT_SPAN_ID_HEADER],
                headers[DEBUG_HEADER]?.let {
                    Sampled.DEBUG
                } ?: Sampled.parse(headers[SAMPLED_HEADER])
            )
    }

    fun asB3Header(): String {
        val parts = listOfNotNull(traceId, spanId, sampled.asHeader(), parentSpanId)
        return if (parts.size == 3) {
            "$traceId-$spanId"
        } else {
            parts.joinToString("-")
        }
    }

    fun asHeaders(): Map<String, String> {
        if (useB3Header) {
            return mapOf(B3_HEADER to asB3Header())
        }
        val headers = mutableMapOf<String, String>()
        if (traceId != null) {
            headers[TRACE_ID_HEADER] = traceId
        }
        if (spanId != null) {
            headers[SPAN_ID_HEADER] = spanId
        }
        if (parentSpanId != null) {
            headers[PARENT_SPAN_ID_HEADER] = parentSpanId
        }
        if (sampled == Sampled.ACCEPT || sampled == Sampled.DENY) {
            headers[SAMPLED_HEADER] = sampled.asHeader()
        }
        if (sampled == Sampled.DEBUG) {
            headers[DEBUG_HEADER] = "1"
        }
        return headers
    }

    fun isEmpty() = traceId == null && spanId == null && parentSpanId == null
}

