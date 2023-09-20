package angstromio.flags

import angstromio.flags.internal.AbstractSingleOption
import angstromio.flags.internal.CLIEntityWrapper
import angstromio.flags.internal.DefaultRequiredType
import angstromio.flags.internal.FlagParser
import angstromio.flags.internal.Option
import angstromio.flags.internal.OptionDescriptor
import angstromio.flags.internal.ParsingValue
import angstromio.flags.internal.SingleNullableOption
import angstromio.flags.internal.default
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


interface Flag<T : Any, TResult> {
    val name: String
    val description: String
    val flagType: FlagType<T>
    val flagValue: Option<TResult>
    val hasParameter: Boolean
    val hasDefault: Boolean
    fun isRegistered(): Boolean
    fun register()

    fun getValueFn(): () -> TResult?
    fun setValueFn(fn: () -> TResult?)
    fun value(): TResult? = synchronized(lock = this) {
        this.getValueFn().invoke()
    }

    fun <R : Any> let(t: TResult, fn: () -> R): R = letNullable(t, fn)

    fun <R: Any> letNull(fn: () -> R): R = letNullable(null, fn)

    private fun <R : Any> letNullable(t: TResult?, fn: () -> R): R = synchronized(lock = this) {
        val previous = getValueFn()
        setValueFn { t }
        return try {
            fn.invoke()
        } finally {
            setValueFn(previous)
        }
    }

    companion object {
        operator fun <T : Any, TResult> invoke(
            name: String,
            description: String,
            flagType: FlagType<T>,
            flagValue: Option<TResult>
        ): Flag<T, TResult> =
            FlagImpl(name, description, flagType, flagValue)
    }
}

class FlagImpl<T : Any, TResult>(
    override val name: String,
    override val description: String,
    override val flagType: FlagType<T>,
    override val flagValue: Option<TResult>
) : Flag<T, TResult> {
    private val registered = AtomicBoolean(/* initialValue = */ false)

    @Volatile
    private var valueFn: () -> TResult? = {
        when (flagValue.valueOrigin) {
            FlagParser.ValueOrigin.UNDEFINED -> // not yet parsed
                null

            else -> flagValue.value
        }
    }

    override val hasParameter: Boolean = this.flagType.hasParameter
    override val hasDefault: Boolean = (this.flagValue.owner.entity?.delegate as ParsingValue<*, *>).descriptor.defaultValue != null

    override fun getValueFn(): () -> TResult? = this.valueFn
    override fun setValueFn(fn: () -> TResult?) {
        this.valueFn = fn
    }

    override fun isRegistered(): Boolean = this.registered.get()
    override fun register() = synchronized(lock = this) {
        when (flagValue.valueOrigin) {
            FlagParser.ValueOrigin.UNDEFINED -> // not yet parsed
                throw FlagException(this.name)

            else ->
                // place in a registry?
                this.registered.set(true)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FlagImpl<*, *>

        if (name != other.name) return false
        if (description != other.description) return false
        if (flagType != other.flagType) return false
        if (flagValue != other.flagValue) return false
        if (registered != other.registered) return false
        if (valueFn != other.valueFn) return false
        if (hasParameter != other.hasParameter) return false
        if (hasDefault != other.hasDefault) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + flagType.hashCode()
        result = 31 * result + flagValue.hashCode()
        result = 31 * result + registered.hashCode()
        result = 31 * result + valueFn.hashCode()
        result = 31 * result + hasParameter.hashCode()
        result = 31 * result + hasDefault.hashCode()
        return result
    }
}

class GlobalFlag<T : Any>(
    override val name: String,
    override val description: String,
    override val flagType: FlagType<T>,
    private val default: T? = null
) : Flag<T, T> {
    private val registered = AtomicBoolean(/* initialValue = */ false)

    /** Global Flags are registered on creation */
    init {
        this.register()
    }

    companion object {
        /* global registry of global flags */
        private val globalFlags: ConcurrentHashMap<String, GlobalFlag<*>> = ConcurrentHashMap()

        fun clear() = globalFlags.clear()

        fun getAll(): List<GlobalFlag<*>> = globalFlags.map { (_, value) -> value }

        fun get(name: String): GlobalFlag<*>? = globalFlags[name]

        fun registerGlobalFlag(flag: GlobalFlag<*>) {
            if (globalFlags.containsKey(flag.name)) {
                throw DuplicateFlagException(flag.name)
            } else {
                globalFlags[flag.name] = flag
            }
        }
    }

    @Volatile
    private var valueFn: () -> T? = {
        when (flagValue.valueOrigin) {
            FlagParser.ValueOrigin.UNDEFINED -> // not yet parsed
                null

            else -> flagValue.value
        }
    }

    @Suppress("UNCHECKED_CAST")
    override val flagValue: Option<T> = getFlagValue() as Option<T>
    override val hasParameter: Boolean = this.flagType.hasParameter
    override val hasDefault: Boolean = this.default != null

    override fun getValueFn(): () -> T? = this.valueFn
    override fun setValueFn(fn: () -> T?) {
        this.valueFn = fn
    }

    override fun isRegistered(): Boolean = this.registered.get()
    override fun register() = synchronized(lock = this) {
        registerGlobalFlag(this)
        this.registered.set(true)
    }

    private fun getFlagValue(): AbstractSingleOption<T, out T?, out DefaultRequiredType> {
        val option = SingleNullableOption(
            OptionDescriptor(
                "-D",
                flagType,
                name,
                description
            ), CLIEntityWrapper()
        )
        option.owner.entity = option
        return if (default != null) option.default(default) else option
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GlobalFlag<*>

        if (name != other.name) return false
        if (description != other.description) return false
        if (flagType != other.flagType) return false
        if (default != other.default) return false
        if (registered != other.registered) return false
        if (valueFn != other.valueFn) return false
        if (flagValue != other.flagValue) return false
        if (hasParameter != other.hasParameter) return false
        if (hasDefault != other.hasDefault) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + flagType.hashCode()
        result = 31 * result + (default?.hashCode() ?: 0)
        result = 31 * result + registered.hashCode()
        result = 31 * result + valueFn.hashCode()
        result = 31 * result + flagValue.hashCode()
        result = 31 * result + hasParameter.hashCode()
        result = 31 * result + hasDefault.hashCode()
        return result
    }
}