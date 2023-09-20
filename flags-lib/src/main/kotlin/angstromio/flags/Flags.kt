package angstromio.flags

import angstromio.flags.internal.AbstractSingleOption
import angstromio.flags.internal.DefaultRequiredType
import angstromio.flags.internal.FlagParser
import angstromio.flags.internal.FlagParserResult
import angstromio.flags.internal.default
import angstromio.flags.internal.multiple
import angstromio.flags.internal.required
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * A simple flags (JVM command line input) implementation. We support only two formats:
 *
 *    for flags with optional values (e.g. booleans):
 *      -flag, -flag value
 *    for flags with required values:
 *      -flag value
 */
class Flags private constructor(
    private val name: String = this::class.java.name,
    private val allowUndefinedFlags: Boolean = ALLOW_UNDEFINED_FLAGS,
    private val includeGlobalFlags: Boolean = INCLUDE_GLOBAL_FLAGS
) {

    sealed interface FlagParseResult {
        /**
         * Indicates successful flag parsing.
         *
         * @param remainder A remainder list of un-parsed arguments.
         */
        data class Ok(val remainder: List<String>) : FlagParseResult

        /**
         * Indicates that an error occurred during flag-parsing.
         *
         * @param reason A string explaining the error that occurred.
         */
        data class Error(val reason: String?) : FlagParseResult {
            override fun toString(): String = reason ?: ""
        }
    }

    companion object {
        private const val ALLOW_UNDEFINED_FLAGS: Boolean = false
        private const val INCLUDE_GLOBAL_FLAGS: Boolean = true

        operator fun invoke(name: String): Flags = Flags(name)

        operator fun invoke(name: String, allowUndefinedFlags: Boolean) =
            Flags(name, allowUndefinedFlags)

        operator fun invoke(name: String, allowUndefinedFlags: Boolean, includeGlobalFlags: Boolean) =
            Flags(name, allowUndefinedFlags, includeGlobalFlags)
    }

    private val parser: AtomicReference<FlagParser> = AtomicReference(newFlagParser())
    private val flags: ConcurrentHashMap<String, Flag<*, *>> = ConcurrentHashMap()

    fun <T : Any> optional(
        name: String,
        description: String,
        argType: FlagType<T>,
        default: T?
    ): Flag<T, out T?> {
        val parserValue =
            parser.get()
                .option(
                    type = argType,
                    name = name,
                    description = description
                )

        val argValue: AbstractSingleOption<T, out T?, out DefaultRequiredType> = when (default) {
            null ->
                parserValue

            else ->
                parserValue.default(default)
        }

        val flag = Flag(
            name = name,
            description = description,
            flagType = argType,
            flagValue = argValue
        )

        checkForDuplicate(flag)
        flags[flag.name] = flag

        return flag
    }

    fun <T : Any> optional(
        name: String,
        description: String,
        flagType: FlagType<T>,
        default: Collection<T>
    ): Flag<T, List<T>> {
        val argValue =
            parser.get()
                .option(
                    type = flagType,
                    name = name,
                    description = description
                )
                .multiple()
                .default(default)

        val flag = Flag(
            name = name,
            description = description,
            flagType = flagType,
            flagValue = argValue
        )

        checkForDuplicate(flag)
        flags[flag.name] = flag

        return flag
    }

    fun <T : Any> required(
        name: String,
        description: String,
        flagType: FlagType<T>
    ): Flag<T, T> {
        val parserValue =
            parser.get()
                .option(
                    type = flagType,
                    name = name,
                    description = description
                )
                .required()

        val flag = Flag(
            name = name,
            description = description,
            flagType = flagType,
            flagValue = parserValue
        )

        checkForDuplicate(flag)
        flags[flag.name] = flag

        return flag
    }

    fun parse(args: Array<String>) = when (val result = parseResult(args)) {
        is FlagParseResult.Ok -> Unit
        is FlagParseResult.Error ->
            throw ParsingException(result.reason ?: "")
    }

    // TODO("consider making this public")
    internal fun parseResult(args: Array<String>): FlagParseResult = synchronized(lock = this) {
        val parseResult = when (val result = parser.get().parse(args)) {
            is FlagParserResult.OK -> {
                flags.forEach { (_, value) ->
                    value.register()
                }
                FlagParseResult.Ok(emptyList())
            }

            is FlagParserResult.Error -> {
                FlagParseResult.Error(result.reason)
            }

            is FlagParserResult.Help -> {
                System.err.println(result.usage)
                FlagParseResult.Ok(emptyList())
            }
        }

        return parseResult
    }

    internal fun reset() {
        this.flags.clear()
        this.parser.set(newFlagParser())
    }

    private fun newFlagParser(): FlagParser =
        FlagParser(
            programName = name,
            allowUndefinedFlags = allowUndefinedFlags,
            includeGlobalFlags = includeGlobalFlags
        )

    private fun checkForDuplicate(flag: Flag<*, *>) {
        if (flags.containsKey(flag.name)) {
            throw DuplicateFlagException(flag.name)
        }
    }
}