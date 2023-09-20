/** Based on kotlinx-cli ArgParser.kt */

package angstromio.flags.internal

import angstromio.flags.FlagType
import angstromio.flags.GlobalFlag
import kotlin.reflect.KProperty

/**
 * Queue of arguments descriptors.
 * Arguments can have several values, so one descriptor can be returned several times.
 */
internal class ArgumentsQueue(argumentsDescriptors: List<FlagDescriptor<*, *>>) {
    /**
     * Map of arguments descriptors and their current usage number.
     */
    private val argumentsUsageNumber = linkedMapOf(*argumentsDescriptors.map { it to 0 }.toTypedArray())

    /**
     * Get next descriptor from queue.
     */
    fun pop(): String? {
        if (argumentsUsageNumber.isEmpty())
            return null

        val (currentDescriptor, usageNumber) = argumentsUsageNumber.iterator().next()
        currentDescriptor.number?.let {
            // Parse all arguments for current argument description.
            if (usageNumber + 1 >= currentDescriptor.number) {
                // All needed arguments were provided.
                argumentsUsageNumber.remove(currentDescriptor)
            } else {
                argumentsUsageNumber[currentDescriptor] = usageNumber + 1
            }
        }
        return currentDescriptor.name
    }
}

/**
 * A property delegate that provides access to the flag/option value.
 */
interface FlagValueDelegate<T> {
    /**
     * The value of an option or flag parsed from command line.
     *
     * Accessing this value before [FlagParser.parse] method is called will result in an exception.
     *
     * @see CLIEntity.value
     */
    var value: T

    /** Provides the value for the delegated property getter. Returns the [value] property.
     * @throws IllegalStateException in case of accessing the value before [FlagParser.parse] method is called.
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    /** Sets the [value] to the [FlagValueDelegate.value] property from the delegated property setter.
     * This operation is possible only after command line flags were parsed with [FlagParser.parse]
     * @throws IllegalStateException in case of resetting value before command line flag are parsed.
     */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

/**
 * Argument parsing result.
 * Contains name of command which was called.
 *
 * @property commandName name of command which was called.
 */
internal sealed class FlagParserResult(open val commandName: String) {
    data class Help(override val commandName: String, val usage: String) : FlagParserResult(commandName)
    data class OK(override val commandName: String) : FlagParserResult(commandName)
    data class Error(override val commandName: String, val reason: String?) : FlagParserResult(commandName)
}

internal data class FlagParserHelpException(val usage: String) : Exception(usage)
internal data class FlagValueRequiredException(override val message: String) : Exception(message)
internal data class FlagUndefinedException(override val message: String) : Exception(message)
internal data class FlagRequiredMissingException(override val message: String) : Exception(message)
internal data class FlagTooManyArgumentsException(override val message: String) : Exception(message)

/**
 * Flag parser.
 *
 * @property programName the name of the current program.
 * @property allowUndefinedFlags specifies whether the extra unmatched arguments in a command line string
 * can be skipped without producing an error message.
 */
internal class FlagParser(
    val programName: String,
    val allowUndefinedFlags: Boolean = false,
    val includeGlobalFlags: Boolean = true
) {

    companion object {
        private const val UNDEFINED_FLAGS_VARARGS = "UNDEFINED_FLAGS"
    }

    /**
     * Map of options: key - full name of option, value - pair of descriptor and parsed values.
     */
    private val options = mutableMapOf<String, ParsingValue<*, *>>()

    /**
     * Map of arguments: key - full name of argument, value - pair of descriptor and parsed values.
     */
    private val arguments = mutableMapOf<String, ParsingValue<*, *>>()

    /**
     * Map with declared options.
     */
    private val declaredOptions = mutableListOf<CLIEntityWrapper>()

    /**
     * Map with declared arguments.
     */
    private val declaredArguments = mutableListOf<CLIEntityWrapper>()

    /**
     * State of parser. Stores last parsing result or null.
     */
    private var parsingState: FlagParserResult? = null

    /**
     * Used prefix form for full option form.
     */
    private val optionFullFormPrefix = "-"

    /**
     * Name with all commands that should be executed.
     */
    private val fullCommandName = mutableListOf(programName)

    /**
     * Flag to recognize if CLI entities can be treated as options.
     */
    private var treatAsOption = true

    /**
     * The way an option/argument has got its value.
     */
    enum class ValueOrigin {
        /* The value was parsed from command line arguments. */
        SET_BY_USER,

        /* The value was missing in command line, therefore the default value was used. */
        SET_DEFAULT_VALUE,

        /* The value is not initialized by command line values or  by default values. */
        UNSET,

        /* The value was redefined after parsing manually (usually with the property setter). */
        REDEFINED,

        /* The value is undefined, because parsing wasn't called. */
        UNDEFINED
    }

    /**
     * Declares a named option and returns an object which can be used to access the option value
     * after all arguments are parsed or to delegate a property for accessing the option value to.
     *
     * By default, the option supports only a single value, is optional, and has no default value,
     * therefore its value's type is `T?`.
     *
     * You can alter the option properties by chaining extensions for the option type on the returned object:
     *   - [AbstractSingleOption.default()] to provide a default value that is used when the option is not specified;
     *   - [SingleNullableOption.required()] to make the option non-optional;
     *   - [AbstractSingleOption.delimiter()] to allow specifying multiple values in one command line argument with a delimiter;
     *   - [AbstractSingleOption.multiple()] to allow specifying the option several times.
     *
     * @param type The type describing how to parse an option value from a string,
     * an instance of [FlagType], e.g. [FlagType.String] or [FlagType.Choice].
     * @param name the name of the optional flag, can be omitted if the option name is inferred
     * from the name of a property delegated to this option.
     * @param description the description of the option used when rendering the usage information.
     * @param deprecatedWarning the deprecation message for the option.
     * Specifying anything except `null` makes this option deprecated. The message is rendered in a help message and
     * issued as a warning when the option is encountered when parsing command line arguments.
     */
    fun <T : Any> option(
        type: FlagType<T>,
        name: String? = null,
        description: String? = null,
        deprecatedWarning: String? = null
    ): SingleNullableOption<T> {
        val option = SingleNullableOption(
            OptionDescriptor(
                optionFullFormPrefix,
                type,
                name,
                description,
                deprecatedWarning = deprecatedWarning
            ), CLIEntityWrapper()
        )
        option.owner.entity = option
        declaredOptions.add(option.owner)
        return option
    }

    /**
     * Check usage of required property for arguments.
     * Make sense only for several last arguments.
     */
    private fun inspectRequiredAndDefaultUsage() {
        var previousArgument: ParsingValue<*, *>? = null
        arguments.forEach { (_, currentArgument) ->
            previousArgument?.let { previous ->
                // Previous argument has default value.
                if (previous.descriptor.defaultValueSet) {
                    if (!currentArgument.descriptor.defaultValueSet && currentArgument.descriptor.required) {
                        error(
                            "Default value of argument ${previous.descriptor.name} will be unused,  " +
                                    "because next argument ${currentArgument.descriptor.name} is always required and has no default value."
                        )
                    }
                }
                // Previous argument is optional.
                if (!previous.descriptor.required) {
                    if (!currentArgument.descriptor.defaultValueSet && currentArgument.descriptor.required) {
                        error(
                            "Argument ${previous.descriptor.name} will be always required, " +
                                    "because next argument ${currentArgument.descriptor.name} is always required."
                        )
                    }
                }
            }
            previousArgument = currentArgument
        }
    }

    /**
     * Declares an argument and returns an object which can be used to access the argument value
     * after all arguments are parsed or to delegate a property for accessing the argument value to.
     *
     * By default, the argument supports only a single value, is required, and has no default value,
     * therefore its value's type is `T`.
     *
     * You can alter the argument properties by chaining extensions for the argument type on the returned object:
     *   - [AbstractSingleArgument.default()] to provide a default value that is used when the argument is not specified;
     *   - [SingleArgument.optional()] to allow omitting the argument;
     *   - [AbstractSingleArgument.multiple()] to require the argument to have exactly the number of values specified;
     *   - [AbstractSingleArgument.vararg()] to allow specifying an unlimited number of values for the _last_ argument.
     *
     * @param type The type describing how to parse an option value from a string,
     * an instance of [FlagType], e.g. [FlagType.String] or [FlagType.Choice].
     * @param fullName the full name of the argument, can be omitted if the argument name is inferred
     * from the name of a property delegated to this argument.
     * @param description the description of the argument used when rendering the usage information.
     * @param deprecatedWarning the deprecation message for the argument.
     * Specifying anything except `null` makes this argument deprecated. The message is rendered in a help message and
     * issued as a warning when the argument is encountered when parsing command line arguments.
     */
    fun <T : Any> argument(
        type: FlagType<T>,
        fullName: String? = null,
        description: String? = null,
        deprecatedWarning: String? = null
    ): SingleArgument<T, DefaultRequiredType.Required> {
        val argument = SingleArgument<T, DefaultRequiredType.Required>(
            FlagDescriptor(
                type, fullName, 1,
                description, deprecatedWarning = deprecatedWarning
            ), CLIEntityWrapper()
        )
        argument.owner.entity = argument
        declaredArguments.add(argument.owner)
        return argument
    }

    /**
     * Save value as argument value.
     *
     * @param arg string with argument value.
     * @param argumentsQueue queue with active argument descriptors.
     */
    private fun saveAsArg(arg: String, argumentsQueue: ArgumentsQueue): Boolean {
        // Find next uninitialized arguments.
        val name = argumentsQueue.pop()
        name?.let {
            val argumentValue = arguments[name]!!
//            argumentValue.descriptor.deprecatedWarning?.let { printWarning(it) }
            argumentValue.addValue(arg)
            return true
        }
        return false
    }

    /**
     * Treat value as argument value.
     *
     * @param arg string with argument value.
     * @param argumentsQueue queue with active argument descriptors.
     */
    private fun treatAsArgument(arg: String, argumentsQueue: ArgumentsQueue) {
        if (!saveAsArg(arg, argumentsQueue)) {
            throw FlagTooManyArgumentsException("Too many arguments! Couldn't process argument $arg!")
        }
    }

    /**
     * Save value as option value.
     */
    private fun <T : Any, U : Any> saveAsOption(parsingValue: ParsingValue<T, U>, value: String) {
        parsingValue.addValue(value)
    }

    /**
     * Try to recognize and save command line element as full form of option.
     *
     * @param candidate string with candidate in options.
     * @param argIterator iterator over command line arguments.
     */
    private fun recognizeAndSaveOptionFullForm(candidate: String, argIterator: Iterator<String>): Boolean {
        if (candidate == optionFullFormPrefix) {
            // All other arguments after `--` are treated as non-option arguments.
            treatAsOption = false
            return false
        }
        if (!candidate.startsWith(optionFullFormPrefix))
            return false

        val optionString = candidate.substring(optionFullFormPrefix.length)
        val argValue = options[optionString]
        if (argValue != null) {
            saveStandardOptionForm(argValue, argIterator)
            return true
        }
        return false
    }

    /**
     * Save option without parameter.
     *
     * @param argValue argument value with all information about option.
     */
    private fun saveOptionWithoutParameter(argValue: ParsingValue<*, *>) {
        // Boolean flags.
        if (argValue.descriptor.name == "help") {
            throw FlagParserHelpException(makeUsage())
        }
        saveAsOption(argValue, "true")
    }

    /**
     * Save option described with standard separated form `--name value`.
     *
     * @param argValue argument value with all information about option.
     * @param argIterator iterator over command line arguments.
     */
    private fun saveStandardOptionForm(argValue: ParsingValue<*, *>, argIterator: Iterator<String>) {
        if (argValue.descriptor.type.hasParameter) {
            if (argIterator.hasNext()) {
                saveAsOption(argValue, argIterator.next())
            } else {
                // An error, option with value without value.
                throw FlagValueRequiredException("No value for ${argValue.descriptor.textDescription}")
            }
        } else {
            saveOptionWithoutParameter(argValue)
        }
    }

    /**
     * Parses the provided array of command line arguments.
     * After a successful parsing, the options and arguments declared in this parser get their values and can be accessed
     * with the properties delegated to them.
     *
     * @param args the array with command line arguments.
     *
     * @return an [FlagParserResult] if all arguments were parsed successfully.
     * Otherwise, prints the usage information and terminates the program execution.
     * @throws IllegalStateException in case of attempt of calling parsing several times.
     */
    fun parse(args: Array<out String>): FlagParserResult = parse(args.asList())

    private fun getSystemPropertiesAsOptionList(): List<String> =
        System.getProperties().filter { (key, _) -> GlobalFlag.get(key.toString()) != null }.map { (key, value) ->
            listOf("-$key", value.toString())
        }.flatten()

    private fun parse(args: List<String>): FlagParserResult {
        check(parsingState == null) { "Parsing of command line options can be called only once." }

        // Add help option.
        val helpDescriptor = OptionDescriptor<Boolean, Boolean>(
            optionFullFormPrefix,
            FlagType.Boolean, "help", description = "Usage info"
        )
        val helpOption = SingleNullableOption(helpDescriptor, CLIEntityWrapper())
        helpOption.owner.entity = helpOption
        declaredOptions.add(helpOption.owner)

        // Add default list with arguments if there can be extra free arguments.
        if (allowUndefinedFlags) {
            argument(FlagType.String, UNDEFINED_FLAGS_VARARGS).vararg()
        }

        // Clean options and arguments maps.
        options.clear()
        arguments.clear()

        // Map declared options and arguments to maps.
        declaredOptions.forEachIndexed { index, option ->
            val value = option.entity?.delegate as ParsingValue<*, *>
            value.descriptor.name?.let {
                // Add option.
                if (options.containsKey(it)) {
                    error("Option with name $it was already added.")
                }
                options[it] = value

            } ?: error("Option was added, but unnamed. Added option under №${index + 1}")
        }

        if (includeGlobalFlags) {
            // Map declared globals to maps.
            GlobalFlag.getAll().forEachIndexed { index, flag ->
                val value = flag.flagValue.owner.entity?.delegate as ParsingValue<*, *>
                value.descriptor.name?.let {
                    // Add option.
                    if (options.containsKey(it)) {
                        error("Global flag with name $it was already added.")
                    }
                    options[it] = value

                } ?: error("Global flag was added, but unnamed. Added global flag under №${index + 1}")
            }
        }

        declaredArguments.forEachIndexed { index, argument ->
            val value = argument.entity?.delegate as ParsingValue<*, *>
            value.descriptor.name?.let {
                // Add option.
                if (arguments.containsKey(it)) {
                    error("Argument with full name $it was already added.")
                }
                arguments[it] = value
            } ?: error("Argument was added, but unnamed. Added argument under №${index + 1}")
        }
        // Make inspections for arguments.
        inspectRequiredAndDefaultUsage()

        listOf(arguments, options).forEach {
            it.forEach { (_, value) ->
                value.valueOrigin = ValueOrigin.UNSET
            }
        }

        val argumentsQueue = ArgumentsQueue(arguments.map { it.value.descriptor as FlagDescriptor<*, *> })

        val argIterator = if (includeGlobalFlags) {
            // add system properties
            (args + getSystemPropertiesAsOptionList()).listIterator()
        } else {
            args.listIterator()
        }
        try {
            while (argIterator.hasNext()) {
                val arg = argIterator.next()
                // Parse arguments from command line.
                if (treatAsOption && arg.startsWith('-')) {
                    // Candidate in being option.
                    // Option is found.
                    if (!(recognizeAndSaveOptionFullForm(
                            arg,
                            argIterator
                        ))
                    ) {
                        // State is changed so next options are arguments.
                        if (!treatAsOption) {
                            // Argument is found.
                            treatAsArgument(argIterator.next(), argumentsQueue)
                        } else {
                            run {
                                // Try save as argument.
                                if (!saveAsArg(arg, argumentsQueue)) {
                                    throw FlagUndefinedException("Unknown flag $arg")
                                }
                            }
                        }
                    }
                } else {
                    // Argument is found.
                    treatAsArgument(arg, argumentsQueue)
                }
            }
            // Post-process results of parsing.
            options.values.union(arguments.values).forEach { value ->
                // Not initialized, append default value if needed.
                if (value.isEmpty()) {
                    value.addDefaultValue()
                }
                if (value.valueOrigin != ValueOrigin.SET_BY_USER && value.descriptor.required && value.descriptor.name != UNDEFINED_FLAGS_VARARGS) {
                    throw FlagRequiredMissingException("Value for ${value.descriptor.textDescription} should be always provided in command line.")
                }
            }

            parsingState = FlagParserResult.OK(programName)
        } catch (exception: Exception) {
            parsingState = FlagParserResult.Error(programName, exception.message)
        }
        return parsingState!!
    }

    /**
     * Creates a message with the usage information.
     */
    private fun makeUsage(): String {
        val result = StringBuilder()
        result.append("Usage: ${fullCommandName.joinToString(" ")} options_list\n")
        if (arguments.isNotEmpty()) {
            result.append("Arguments: \n")
            arguments.forEach {
                result.append(it.value.descriptor.helpMessage)
            }
        }
        if (options.isNotEmpty()) {
            result.append("Options: \n")
            options.forEach {
                result.append(it.value.descriptor.helpMessage)
            }
        }
        return result.toString()
    }
}