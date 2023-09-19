package angstromio.flags

import angstromio.flags.internal.FlagParser
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.be
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import org.junit.jupiter.api.assertThrows

class FlagsTest : FunSpec() {

    private val mapArgType = FlagType.Map<String, List<Int>>(
        keyType = FlagType.String,
        valueType = FlagType.List(FlagType.Int)
    )

    init {
        test("Flags#duplicates fail") {
            val flags = Flags("test")

            flags.required(
                "flag.foo.bar",
                "A test flag",
                FlagType.String
            )

            assertThrows<DuplicateFlagException> {
                flags.required(
                    "flag.foo.bar",
                    "A test flag 1",
                    FlagType.String
                )
            }

            assertThrows<DuplicateFlagException> {
                flags.optional(
                    "flag.foo.bar",
                    "A test flag 2",
                    FlagType.String,
                    "Hello, World"
                )
            }
        }

        test("Flags#required") {
            val flags = Flags("test")

            val flag = flags.required(
                "entries",
                "A set of integer entries keyed by a string",
                mapArgType
            )

            flag.name should be("entries")
            flag.description should be("A set of integer entries keyed by a string")
            flag.hasDefault should be(false)
            flag.hasParameter should be(true)

            flag.value() should beNull()
            flag.isRegistered() should be(false)
            assertThrows<FlagException> {
                // has not yet been parsed
                flag.register()
            }

            val expected = mapOf(Pair("a", listOf(1, 3, 5, 7)), Pair("b", listOf(2, 4, 6)))
            flags.parse(arrayOf("-entries", "a=1,3,5,7,b=2,4,6"))

            flag.isRegistered() should be(true)
            flag.value() shouldNot beNull()

            flag.value() should be(expected)
        }

        test("Flags#parse failure with extra args") {
            val flags = Flags("test")

            flags.required(
                "entries",
                "A set of integer entries keyed by a string",
                mapArgType
            )

            when (val result = flags.parseResult(arrayOf("-entries", "a=1,3,5,7,b=2,4,6", "-ints", "1,3,5,7,9"))) {
                is Flags.FlagParseResult.Error ->
                    result.reason should be("Unknown flag -ints")

                else -> fail("")
            }
        }

        test("Flags#parse does not allow for reset of option values") {
            val flags = Flags("test")
            val useShortFormOption = flags.optional(
                name = "short",
                description = "Show short version of report",
                argType = FlagType.Boolean,
                default = false
            )
            val rendersOption = flags.optional(
                name = "renders",
                description = "Renders for showing information",
                flagType = FlagType.Choice<Renders>(),
                default = listOf(Renders.TEXT)
            )
            val sourcesOption = flags.optional(
                name = "sources",
                description = "Data sources",
                flagType = FlagType.Choice<TestEnum>(),
                default = listOf(TestEnum.PRODUCTION)
            )
            val outputOption = flags.required(
                name = "output",
                description = "Output file",
                flagType = FlagType.String
            )
            flags.parse(arrayOf("-output", "out.txt"))

            useShortFormOption.value() should be(false)
            outputOption.value() should be("out.txt")
            rendersOption.value()?.size?.shouldBeEqual(1)
            rendersOption.value()?.shouldBe(listOf(Renders.TEXT))
            sourcesOption.value()?.size?.shouldBeEqual(1)
            sourcesOption.value()?.shouldBe(listOf(TestEnum.PRODUCTION))
            outputOption.flagValue.valueOrigin should be(FlagParser.ValueOrigin.SET_BY_USER)
            useShortFormOption.flagValue.valueOrigin should be(FlagParser.ValueOrigin.SET_DEFAULT_VALUE)
            rendersOption.flagValue.valueOrigin should be(FlagParser.ValueOrigin.SET_DEFAULT_VALUE)
            sourcesOption.flagValue.valueOrigin should be(FlagParser.ValueOrigin.SET_DEFAULT_VALUE)
        }

        test("Flags#multiple arguments") {
            val flags = Flags("test")
            val debugMode = flags.required(
                name = "debug",
                description = "Debug mode",
                flagType = FlagType.Boolean
            )
            val input = flags.required("input", "Input file", FlagType.String)
            val output = flags.required("output", "Output file", FlagType.String)
            flags.parse(arrayOf("-debug", "-input", "input.txt", "-output", "out.txt"))
            debugMode.value() should be(true)
            output.value() should be("out.txt")
            input.value() should be("input.txt")
        }

        test("Flags#list of inputs") {
            val flags = Flags("test")
            val output = flags.required("output", "Output file", FlagType.String)
            val inputs = flags.required("input.files", description = "Input files", FlagType.List(FlagType.String))
            flags.parse(arrayOf("-output", "out.txt", "-input.files", "input1.txt,input2.txt,input3.txt,input4.txt"))
            output.value() should be("out.txt")
            inputs.value()?.size?.shouldBeEqual(4)
        }


        test("Flags#parse enum choice with custom toString()") {
            val flags = Flags("test")
            val source = flags.optional(
                name = "sources",
                description = "Data sources",
                argType = FlagType.Choice<TestEnum> {
                    it.name[0].toString().lowercase()
                },
                default = null
            )
            flags.parse(arrayOf("-sources", "S"))
            source.value() should be(TestEnum.STAGING)
        }

        test("Flags#allow undefined flags") {
            val flags = Flags("test", allowUndefinedFlags = true)
            val addendums = flags.required(name = "addendums", description = "Addendums", FlagType.List(FlagType.Int))
            val debugMode = flags.required(name = "debug", description = "Debug mode", FlagType.Boolean)
            val output = flags.required(name = "output", description= "Output file", FlagType.String)

            flags.parse(arrayOf("-addendums", "2,3", "-debug", "-output", "out.txt", "-something", "else", "-in", "string"))

            addendums.value() should be (listOf(2, 3))
            debugMode.value() should be(true)
            output.value() should be("out.txt")
        }
    }
}