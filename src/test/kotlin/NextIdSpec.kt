package mjs.ktor.features.zipkin

import assertk.assertThat
import assertk.assertions.hasLength
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

internal object NextIdSpec : Spek({
    describe("generates IDs") {
        it("that are 64 bits long by default") {
            assertThat(nextId()).hasLength(16)
        }
        it("that are 128 bits long when specified") {
            assertThat(nextId(IdLength.ID_128_BITS)).hasLength(32)
        }
    }
})
