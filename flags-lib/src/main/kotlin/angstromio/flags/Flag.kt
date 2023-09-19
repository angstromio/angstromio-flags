package angstromio.flags

import angstromio.flags.internal.FlagParser
import angstromio.flags.internal.Option
import java.util.concurrent.atomic.AtomicBoolean

class Flag<T : Any, TResult>(
    val name: String,
    val description: String,
    internal val flagType: FlagType<T>,
    internal val flagValue: Option<TResult>,
    private val default: (() -> T)? = null
) {
    private val registered = AtomicBoolean(/* initialValue = */ false)
    @Volatile
    private var valueFn: () -> TResult? = { flagValue.value }

    fun value(): TResult? = synchronized(lock = this) {
        return when (flagValue.valueOrigin) {
            FlagParser.ValueOrigin.UNDEFINED -> // not yet parsed
                null

            else ->
                valueFn.invoke()
        }
    }

    val hasParameter: Boolean = this.flagType.hasParameter
    val hasDefault: Boolean = this.default != null

    fun isRegistered(): Boolean = this.registered.get()
    fun register() {
        when (flagValue.valueOrigin) {
            FlagParser.ValueOrigin.UNDEFINED -> // not yet parsed
                throw FlagException(this.name)

            else ->
                // place in a registry?
                this.registered.set(true)
        }
    }

    fun <R : Any> let(t: TResult, fn: () -> R): R = letNullable(t, fn)

    private fun setValue(fn: () -> TResult?) {
        this.valueFn = fn
    }

    private fun <R : Any> letNullable(t: TResult?, fn: () -> R): R = synchronized(lock = this) {
        val previous = this.valueFn
        setValue { t }
        return try {
            fn.invoke()
        } finally {
            setValue(previous)
        }
    }
}