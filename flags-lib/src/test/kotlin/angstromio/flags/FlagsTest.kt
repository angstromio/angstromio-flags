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

        test("Flag#let multi value") {
            val flags = Flags("flag-let")
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

        test("Flag#let single value") {
            val flags = Flags("flag-let")
            val argType = FlagType.Int
            val expected = 42

            val flag = flags.required(
                name = "int",
                description = "What is the answer?",
                flagType = argType
            )

            flag.name should be("int")
            flag.description should be("What is the answer?")
            flag.value() should beNull()
            flags.parse(arrayOf("-int", "42"))
            flag.value() shouldNot beNull()
            flag.value() should be(expected)

            val letValue = 101
            flag.let(letValue) {
                flag.value() should be(letValue)
            }
            flag.value() should be(expected)
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
            useShortFormOption.hasDefault should be(true)
            val rendersOption = flags.optional(
                name = "renders",
                description = "Renders for showing information",
                flagType = FlagType.Choice<Renders>(),
                default = listOf(Renders.TEXT)
            )
            rendersOption.hasDefault should be(true)
            val sourcesOption = flags.optional(
                name = "sources",
                description = "Data sources",
                flagType = FlagType.Choice<TestEnum>(),
                default = listOf(TestEnum.PRODUCTION)
            )
            sourcesOption.hasDefault should be(true)
            val outputOption = flags.required(
                name = "output",
                description = "Output file",
                flagType = FlagType.String
            )
            outputOption.hasDefault should be(false)

            // parse flags
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

        test("Flags#boolean without argument") {
            val flags = Flags("test")
            val debugMode = flags.optional(name = "debug", description = "Debug mode", FlagType.Boolean, default = false)
            flags.parse(arrayOf("-debug"))

            debugMode.value() should be(true)
        }

        test("Flags#boolean with argument") {
            val flags = Flags("test", allowUndefinedFlags = true)
            val debugMode = flags.optional(name = "debug", description = "Debug mode", FlagType.String, default = "false")
            flags.parse(arrayOf("-debug", "true"))

            debugMode.value().toBoolean() should be(true)
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

        test("Flags#allow undefined flags without sending undefined") {
            val flags = Flags("test", allowUndefinedFlags = true)
            val addendums = flags.required(name = "addendums", description = "Addendums", FlagType.List(FlagType.Int))
            val debugMode = flags.required(name = "debug", description = "Debug mode", FlagType.Boolean)
            val output = flags.required(name = "output", description= "Output file", FlagType.String)

            flags.parse(arrayOf("-addendums", "2,3", "-debug", "-output", "out.txt"))

            addendums.value() should be (listOf(2, 3))
            debugMode.value() should be(true)
            output.value() should be("out.txt")
        }

        test("Flags#help") {
            val flags = Flags("test")
            flags.required(
                name = "debug",
                description = "Debug mode",
                flagType = FlagType.Boolean
            )
            flags.optional(
                name = "sources",
                description = "Data sources",
                flagType = FlagType.Choice<TestEnum>(),
                default = listOf(TestEnum.PRODUCTION)
            )

            val e = assertThrows<ParsingException> {
                flags.parse(arrayOf("-help"))
            }
            e.message?.contains("-debug -> Debug mode (always required)") should be(true)
            e.message?.contains("-sources [production] -> Data sources { Value should be one of [local, staging, production] }")
            e.message?.contains("-help -> Usage info") should be(true)
        }
    }
}