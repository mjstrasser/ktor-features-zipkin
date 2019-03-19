package mjs.ktor.features

import assertk.assertThat
import assertk.assertions.isTrue
import io.ktor.http.HttpMethod
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ZipkinIdsTest {

    @Nested
    inner class Thing {

        @Test
        fun `install the feature`(): Unit = withTestApplication {
            handleRequest(HttpMethod.Get, "/").apply {
                assertThat(response.headers.contains(TRACE_ID_HEADER)).isTrue()
            }
        }
    }

}