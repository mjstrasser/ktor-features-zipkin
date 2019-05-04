package mjs.ktor.features.zipkin

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockHttpResponse
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.client.tests.utils.TestClientBuilder
import io.ktor.client.tests.utils.clientTest
import io.ktor.client.tests.utils.config
import io.ktor.client.tests.utils.test
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ClientIdsTest {
    private val traceId = nextId()
    private val spanId = nextId()
    private val parentSpanId = nextId()
    private val echoEngine = MockEngine {
        MockHttpResponse(call = call, status = HttpStatusCode.OK, headers = this.headers)
    }

    @Test
    fun `should set X-B3-TraceId and X-B3-ParentSpanId headers from X-B3-TraceId and X-B3-SpanId`() {
        clientTest(echoEngine) {
            configParts(TracingParts(false, traceId, spanId))
            test {
                val response = it.get<HttpResponse>()
                with(response.headers) {
                    assertThat(contains("b3")).isFalse()
                    assertThat(get(TRACE_ID_HEADER)).isEqualTo(traceId)
                    assertThat(get(SPAN_ID_HEADER)).isNotEqualTo(spanId)
                    assertThat(get(PARENT_SPAN_ID_HEADER)).isEqualTo(spanId)
                }
            }
        }
    }

    @Test
    fun `should set b3 header components trace ID and parent span ID from trace ID and span ID`() {
        clientTest(echoEngine) {
            configParts(TracingParts(true, traceId, spanId, parentSpanId, Sampled.ACCEPT))
            test {
                val response = it.get<HttpResponse>()
                with(response.headers) {
                    assertThat(contains("b3")).isTrue()
                    val parts = get(B3_HEADER)!!.split("-")
                    assertThat(parts[0]).isEqualTo(traceId)
                    assertThat(parts[1]).isNotEqualTo(spanId)
                    assertThat(parts[2]).isEqualTo("1")
                    assertThat(parts[3]).isEqualTo(spanId)
                }
            }
        }
    }

    private fun TestClientBuilder<*>.configParts(parts: TracingParts) {
        config {
            install(ClientIds) {
                tracingParts = parts
            }
        }
    }
}
