package angstromio.flags

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.be
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import org.junit.jupiter.api.assertThrows

class GlobalFlagsTest : FunSpec() {

    private val globalFlagSystemPropertyName = "globalflagstest.my.property"

    init {

        beforeEach {
            System.clearProperty(globalFlagSystemPropertyName)
            GlobalFlag.clear()
        }

        afterSpec {
            System.clearProperty(globalFlagSystemPropertyName)
        }

        test("GlobalFlag") {
            System.setProperty(globalFlagSystemPropertyName, "99")

            val flags = Flags("test")

            val myGlobalFlag = GlobalFlag<Int>(
                name = globalFlagSystemPropertyName,
                description = "Test GlobalFlag",
                flagType = FlagType.Int,
                default = 4
            )

            myGlobalFlag.value() should beNull() // not parsed until flags are parsed


            GlobalFlag.getAll().size shouldBeEqual 1
            GlobalFlag.get(globalFlagSystemPropertyName) should be(myGlobalFlag)

            val myOtherGlobalFlag = GlobalFlag<Boolean>(
                name = "globalflagstest.notSet",
                description = "This isn't set as a system property so will get the default",
                flagType = FlagType.Boolean,
                default = true
            )

            myOtherGlobalFlag.value() should beNull()  // not parsed until flags are parsed

            GlobalFlag.getAll().size shouldBeEqual 2
            GlobalFlag.get("globalflagstest.notSet") should be(myOtherGlobalFlag)

            flags.parse(emptyArray()) // parse flags

            myGlobalFlag.value() shouldNot beNull()
            myGlobalFlag.value() should be(99)
            myGlobalFlag.let(137) {
                myGlobalFlag.value() should be(137)
            }
            myGlobalFlag.value() should be(99)


            myOtherGlobalFlag.value() shouldNot beNull()
            myOtherGlobalFlag.value() should be(true)
            myOtherGlobalFlag.let(false) {
                myOtherGlobalFlag.value() should be(false)
            }
            myOtherGlobalFlag.value() should be(true)
        }

        test("GlobalFlag#help") {
            System.setProperty(globalFlagSystemPropertyName, "99")

            val flags = Flags("test")

            GlobalFlag(
                name = globalFlagSystemPropertyName,
                description = "Test GlobalFlag",
                flagType = FlagType.Int,
                default = 42
            )

            val e = assertThrows<ParsingException> {
                flags.parse(arrayOf("-help"))
            }

            e.message?.contains("-D$globalFlagSystemPropertyName") should be(true)
        }
    }
}