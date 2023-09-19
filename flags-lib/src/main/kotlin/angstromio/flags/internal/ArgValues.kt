/** Based on kotlinx-cli ArgValues.kt */
package angstromio.flags.internal

import angstromio.flags.ParsingException

/**
 * Parsing value of option/flag.
 */
internal abstract class ParsingValue<T: Any, TResult: Any>(val descriptor: Descriptor<T, TResult>) {
    /**
     * Values of arguments.
     */
    protected lateinit var parsedValue: TResult

    /**
     * Value origin.
     */
    var valueOrigin = FlagParser.ValueOrigin.UNDEFINED
        internal set

    /**
     * Check if values of argument are empty.
     */
    abstract fun isEmpty(): Boolean

    /**
     * Check if value of argument was initialized.
     */
    protected fun valueIsInitialized() = ::parsedValue.isInitialized

    /**
     * Sace value from command line.
     *
     * @param stringValue value from command line.
     */
    protected abstract fun saveValue(stringValue: String)

    /**
     * Set value of delegated property.
     */
    fun setDelegatedValue(providedValue: TResult) {
        parsedValue = providedValue
        valueOrigin = FlagParser.ValueOrigin.REDEFINED
    }

    /**
     * Add parsed value from command line.
     *
     * @param stringValue value from command line.
     */
    internal fun addValue(stringValue: String) {
        // Check of possibility to set several values to one option/argument.
        if (descriptor is OptionDescriptor<*, *> && !descriptor.multiple &&
            !isEmpty() && descriptor.delimiter == null) {
            throw ParsingException("More than one value provided for ${descriptor.name}.")
        }
        // Show deprecated warning only first time of using option/argument.
        descriptor.deprecatedWarning?.let {
            if (isEmpty())
                println ("Warning: $it")
        }
        // Split value if needed.
        if (descriptor is OptionDescriptor<*, *> && descriptor.delimiter != null) {
            stringValue.split(descriptor.delimiter).forEach {
                saveValue(it)
            }
        } else {
            saveValue(stringValue)
        }
    }

    /**
     * Set default value to option.
     */
    fun addDefaultValue() {
        if (descriptor.defaultValueSet) {
            parsedValue = descriptor.defaultValue!!
            valueOrigin = FlagParser.ValueOrigin.SET_DEFAULT_VALUE
        }
    }

    /**
     * Provide name for CLI entity.
     *
     * @param name name for CLI entity.
     */
    fun provideName(name: String) {
        descriptor.name ?: run { descriptor.name = name }
    }
}

/**
 * Single flag value.
 *
 * @property descriptor descriptor of option/flag.
 */
internal abstract class AbstractFlagSingleValue<T: Any>(descriptor: Descriptor<T, T>):
    ParsingValue<T, T>(descriptor) {

    override fun saveValue(stringValue: String) {
        if (!valueIsInitialized()) {
            parsedValue = descriptor.type.convert(stringValue, descriptor.name!!)
            valueOrigin = FlagParser.ValueOrigin.SET_BY_USER
        } else {
            throw ParsingException("Tried to provide more than one value $parsedValue and $stringValue for ${descriptor.name}.")
        }
    }

    override fun isEmpty(): Boolean = !valueIsInitialized()
}

/**
 * Single flag value.
 *
 * @property descriptor descriptor of option/flag.
 */
internal class FlagSingleValue<T: Any>(descriptor: Descriptor<T, T>): AbstractFlagSingleValue<T>(descriptor),
    FlagValueDelegate<T> {
    override var value: T
        get() = if (!isEmpty()) parsedValue else error("Value for argument ${descriptor.name} isn't set. " +
                "FlagParser.parse(...) method should be called before.")
        set(value) = setDelegatedValue(value)
}

/**
 * Single nullable flag value.
 *
 * @property descriptor descriptor of option/flag.
 */
internal class FlagSingleNullableValue<T : Any>(descriptor: Descriptor<T, T>):
    AbstractFlagSingleValue<T>(descriptor), FlagValueDelegate<T?> {
    private var setToNull = false
    override var value: T?
        get() = if (!isEmpty() && !setToNull) parsedValue else null
        set(providedValue) = providedValue?.let {
            setDelegatedValue(it)
            setToNull = false
        } ?: run {
            setToNull = true
            valueOrigin = FlagParser.ValueOrigin.REDEFINED
        }
}

/**
 * Multiple flag values.
 *
 * @property descriptor descriptor of option/flag.
 */
internal class FlagMultipleValues<T : Any>(descriptor: Descriptor<T, List<T>>):
    ParsingValue<T, List<T>>(descriptor), FlagValueDelegate<List<T>> {

    private val addedValue = mutableListOf<T>()
    init {
        parsedValue = addedValue
    }

    override var value: List<T>
        get() = parsedValue
        set(value) = setDelegatedValue(value)

    override fun saveValue(stringValue: String) {
        addedValue.add(descriptor.type.convert(stringValue, descriptor.name!!))
        valueOrigin = FlagParser.ValueOrigin.SET_BY_USER
    }

    override fun isEmpty() = parsedValue.isEmpty()
}