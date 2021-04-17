package mjs.ktor.features.zipkin

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldHaveLength

class NextIdSpec : DescribeSpec({
    describe("`nextId()`: generates IDs") {
        it("that are 64 bits long by default") {
            nextId() shouldHaveLength 16
        }
        it("that are 128 bits long when specified") {
            nextId(IdLength.ID_128_BITS) shouldHaveLength 32
        }
    }
})
