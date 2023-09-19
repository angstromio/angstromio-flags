package angstromio.flags

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.be
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

class FlagTest : FunSpec({

    test("Flag#let") {
        val flags = Flags("flag-test")
        val argType = FlagType.List(valueType = FlagType.Int)
        val expected = listOf(1, 3, 5, 7, 9, 11, 13)

        val flag = flags.required(
            name = "ints",
            description = "List of integers",
            flagType = argType
        )

        flag.name should be("ints")
        flag.description should be("List of integers")
        flag.value() should beNull()
        flags.parse(arrayOf("-ints", "1,3,5,7,9,11,13"))
        flag.value() shouldNot beNull()
        flag.value() should be(expected)

        val letValue = listOf(2, 4, 6, 8, 10, 12, 14)
        flag.let(letValue) {
            flag.value() should be(letValue)
        }
        flag.value() should be(expected)
    }
})