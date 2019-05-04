package mjs.ktor.features.zipkin

import kotlin.random.Random

private val random = Random(System.nanoTime())

enum class IdLength { ID_64_BITS, ID_128_BITS }

fun nextId(idLength: IdLength = IdLength.ID_64_BITS) = when (idLength) {
    IdLength.ID_64_BITS -> String.format("%016x", random.nextLong())
    IdLength.ID_128_BITS -> String.format("%016x%016x", random.nextLong(), random.nextLong())
}
