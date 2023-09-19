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
 * A simple flags implementation. We support only two formats:
 *
 *    for flags with optional values (e.g. booleans):
 *      -flag, -flag value
 *    for flags with required values:
 *      -flag value
 *
 * That's it. These can be parsed without ambiguity.
 *
 * There is no support for mandatory arguments: That is not what
 * flags are for.
 */
class Flags private constructor(
    private val name: String = this::class.java.name,
    private val allowUndefinedFlags: Boolean = ALLOW_UNDEFINED_FLAGS
) {

    companion object {
        private const val ALLOW_UNDEFINED_FLAGS: Boolean = false

        operator fun invoke(name: String): Flags = Flags(name)

        operator fun invoke(name: String, allowUndefinedFlags: Boolean) =
            Flags(name, allowUndefinedFlags)
    }

    private val _parser: AtomicReference<FlagParser> = AtomicReference(newFlagParser())
    private val _flags: ConcurrentHashMap<String, Flag<*, *>> = ConcurrentHashMap()

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

    fun parseResult(args: Array<String>): FlagParseResult {
        synchronized(lock = this) {
            val parseResult = when (val result = _parser.get().parse(args)) {
                is FlagParserResult.OK -> {
                    _flags.forEach { (_, value) ->
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
    }

    fun parse(args: Array<String>) {
        return when (val result = parseResult(args)) {
            is FlagParseResult.Ok -> Unit
            is FlagParseResult.Error ->
                throw ParsingException(result.reason ?: "")
        }
    }

    fun <T : Any> optional(
        name: String,
        description: String,
        argType: FlagType<T>,
        default: T?
    ): Flag<T, out T?> {
        val parserValue = _parser.get().option(
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
        _flags[flag.name] = flag

        return flag
    }

    fun <T : Any> optional(
        name: String,
        description: String,
        flagType: FlagType<T>,
        default: Collection<T>
    ): Flag<T, List<T>> {
        val argValue =
            _parser.get()
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
        _flags[flag.name] = flag

        return flag
    }

    fun <T : Any> required(
        name: String,
        description: String,
        flagType: FlagType<T>
    ): Flag<T, T> {
        val parserValue = _parser.get().option(
            type = flagType,
            name = name,
            description = description
        ).required()

        val flag = Flag(
            name = name,
            description = description,
            flagType = flagType,
            flagValue = parserValue
        )

        checkForDuplicate(flag)
        _flags[flag.name] = flag

        return flag
    }

    fun reset() {
        this._flags.clear()
        this._parser.set(newFlagParser())
    }

    private fun newFlagParser(): FlagParser =
        FlagParser(
            programName = name,
            allowUndefinedFlags = allowUndefinedFlags
        )

    private fun checkForDuplicate(flag: Flag<*, *>) {
        if (_flags.containsKey(flag.name)) {
            throw DuplicateFlagException(flag.name)
        }
    }
}