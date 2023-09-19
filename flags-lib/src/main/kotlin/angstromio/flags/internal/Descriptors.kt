/** Based on kotlinx-cli Descriptors.kt */

package angstromio.flags.internal

import angstromio.flags.FlagType

/**
 * Common descriptor both for options and positional arguments.
 *
 * @property type option/flag type, one of [FlagType].
 * @property name option/flag name.
 * @property description text description of option/flag.
 * @property defaultValue default value for option/flag.
 * @property required if option/flag is required or not. If it's required and not provided in command line, error will be generated.
 * @property deprecatedWarning text message with information in case if option is deprecated.
 */
internal abstract class Descriptor<T : Any, TResult>(val type: FlagType<T>,
                                                     var name: String? = null,
                                                     val description: String? = null,
                                                     val defaultValue: TResult? = null,
                                                     val required: Boolean = false,
                                                     val deprecatedWarning: String? = null) {
    /**
     * Text description for help message.
     */
    abstract val textDescription: String
    /**
     * Help message for descriptor.
     */
    abstract val helpMessage: String

    /**
     * Provide text description of value.
     *
     * @param value value got getting text description for.
     */
    fun valueDescription(value: TResult?) = value?.let {
        if (it is List<*> && it.isNotEmpty())
            " [${it.joinToString()}]"
        else if (it !is List<*>)
            " [$it]"
        else null
    }

    /**
     * Flag to check if descriptor has set default value for option/argument.
     */
    val defaultValueSet by lazy {
        defaultValue != null && (defaultValue is List<*> && defaultValue.isNotEmpty() || defaultValue !is List<*>)
    }
}

/**
 * Option descriptor.
 *
 * Command line entity started with some prefix (-/--) and can have value as next entity in command line string.
 *
 * @property optionFullFormPrefix prefix used before full form of option.
 * @property type option type, one of [FlagType].
 * @property name option full name.
 * @property description text description of option.
 * @property defaultValue default value for option.
 * @property required if option is required or not. If it's required and not provided in command line, error will be generated.
 * @property multiple if option can be repeated several times in command line with different values. All values are stored.
 * @property delimiter delimiter that separate option provided as one string to several values.
 * @property deprecatedWarning text message with information in case if option is deprecated.
 */
internal class OptionDescriptor<T : Any, TResult>(
    val optionFullFormPrefix: String,
    type: FlagType<T>,
    name: String? = null,
    description: String? = null,
    defaultValue: TResult? = null,
    required: Boolean = false,
    val multiple: Boolean = false,
    val delimiter: String? = null,
    deprecatedWarning: String? = null) : Descriptor<T, TResult>(type, name, description, defaultValue,
    required, deprecatedWarning) {

    override val textDescription: String
        get() = "option $optionFullFormPrefix$name"

    override val helpMessage: String
        get() {
            val result = StringBuilder()
            result.append("    $optionFullFormPrefix$name")
            valueDescription(defaultValue)?.let {
                result.append(it)
            }
            description?.let {result.append(" -> $it")}
            if (required) result.append(" (always required)")
            result.append(" ${type.description}")
            deprecatedWarning?.let { result.append(" Warning: $it") }
            result.append("\n")
            return result.toString()
        }
}

/**
 * Flag descriptor.
 *
 * Command line entity which role is connected only with its position.
 *
 * @property type argument type, one of [FlagType].
 * @property name flag name.
 * @property number expected number of values. Null means any possible number of values.
 * @property description text description of the flag.
 * @property defaultValue default value for the flag.
 * @property required if argument is required or not. If it's required and not provided in command line and have no default value, error will be generated.
 * @property deprecatedWarning text message with information in case if argument is deprecated.
 */
internal class FlagDescriptor<T : Any, TResult>(
    type: FlagType<T>,
    name: String?,
    val number: Int? = null,
    description: String? = null,
    defaultValue: TResult? = null,
    required: Boolean = true,
    deprecatedWarning: String? = null) : Descriptor<T, TResult>(type, name, description, defaultValue,
    required, deprecatedWarning) {

    init {
        // Check arguments number correctness.
        number?.let {
            if (it < 1)
                error("Number of arguments for flag description $name should be greater than zero.")
        }
    }

    override val textDescription: String
        get() = "flag $name"

    override val helpMessage: String
        get() {
            val result = StringBuilder()
            result.append("    $name")
            valueDescription(defaultValue)?.let {
                result.append(it)
            }
            description?.let { result.append(" -> $it") }
            if (!required) result.append(" (optional)")
            result.append(" ${type.description}")
            deprecatedWarning?.let { result.append(" Warning: $it") }
            result.append("\n")
            return result.toString()
        }
}