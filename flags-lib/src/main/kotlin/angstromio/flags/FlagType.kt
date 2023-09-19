package angstromio.flags

/**
 * Possible types of arguments.
 *
 * Inheritors describe type of argument value. New types can be added by user.
 * In case of options type can have parameter or not.
 */
abstract class FlagType<T : Any>(val hasParameter: kotlin.Boolean) {
    /**
     * Text description of type for helpMessage.
     */
    abstract val description: kotlin.String

    /**
     * Function to convert string argument value to its type.
     * In case of error during conversion also provides help message.
     *
     * @param value value
     */
    abstract fun convert(value: kotlin.String, name: kotlin.String): T

    /**
     * Argument type for flags that can be only set/unset.
     */
    object Boolean : FlagType<kotlin.Boolean>(false) {
        override val description: kotlin.String
            get() = ""

        override fun convert(value: kotlin.String, name: kotlin.String): kotlin.Boolean =
            value != "false"
    }

    /**
     * Argument type for string values.
     */
    object String : FlagType<kotlin.String>(true) {
        override val description: kotlin.String
            get() = "{ String }"

        override fun convert(value: kotlin.String, name: kotlin.String): kotlin.String = value
    }

    /**
     * Argument type for integer values.
     */
    object Int : FlagType<kotlin.Int>(true) {
        override val description: kotlin.String
            get() = "{ Int }"

        override fun convert(value: kotlin.String, name: kotlin.String): kotlin.Int =
            value.toIntOrNull()
                ?: throw ParsingException("Option $name is expected to be integer number. $value is provided.")
    }

    /**
     * Argument type for double values.
     */
    object Double : FlagType<kotlin.Double>(true) {
        override val description: kotlin.String
            get() = "{ Double }"

        override fun convert(value: kotlin.String, name: kotlin.String): kotlin.Double =
            value.toDoubleOrNull()
                ?: throw ParsingException("Option $name is expected to be double number. $value is provided.")
    }

    companion object {
        /**
         * Helper for arguments that have limited set of possible values represented as enumeration constants.
         */
        inline fun <reified T : Enum<T>> Choice(
            noinline toVariant: ((kotlin.String) -> T)? = null,
            noinline toString: (T) -> kotlin.String = { it.toString().lowercase() }
        ): Choice<T> {
            return Choice(
                enumValues<T>().toList(),
                toVariant ?: {
                    enumValues<T>().find { e -> toString(e).equals(it, ignoreCase = true) }
                        ?: throw IllegalArgumentException("No enum constant $it")
                },
                toString
            )
        }

        inline fun <reified T : Any> List(
            valueType: FlagType<T>
        ): ListLike<T> = ListLike(valueType)

        inline fun <reified K : Any, reified V : Any> Map(
            keyType: FlagType<K>,
            valueType: FlagType<V>
        ): MapLike<K, V> = MapLike(keyType, valueType)
    }

    /**
     * Type for arguments that have limited set of possible values.
     */
    class Choice<T: Any>(choices: List<T>,
                         val toVariant: (kotlin.String) -> T,
                         val variantToString: (T) -> kotlin.String = { it.toString() }): FlagType<T>(true) {
        private val choicesMap: Map<kotlin.String, T> = choices.associateBy { variantToString(it) }

        init {
            require(choicesMap.size == choices.size) {
                "Command line representations of enum choices are not distinct"
            }
        }

        override val description: kotlin.String
            get() {
                return "{ Value should be one of ${choicesMap.keys} }"
            }

        override fun convert(value: kotlin.String, name: kotlin.String) =
            try {
                toVariant(value)
            } catch (e: Exception) {
                throw ParsingException("Option $name is expected to be one of ${choicesMap.keys}. $value is provided.")
            }
    }

    class ListLike<T : Any>(
        private val valueType: FlagType<T>
    ) : FlagType<List<T>>(true) {

        override val description: kotlin.String
            get() {
                return "{ Value should be in the form of a comma-separated sequence }"
            }

        override fun convert(value: kotlin.String, name: kotlin.String): List<T> {
            return if (value.isEmpty()) emptyList()
            else value.split(",").map { t -> valueType.convert(t.trim(), name) }
        }
    }

    class MapLike<K : Any, V : Any>(
        private val keyType: FlagType<K>,
        private val valueType: FlagType<V>
    ) : FlagType<Map<K, V>>(true) {
        override val description: kotlin.String
            get() {
                return "{ Value should be in the form of key/value pairs }"
            }

        override fun convert(value: kotlin.String, name: kotlin.String): Map<K, V> {
            val coll = mutableMapOf<kotlin.String, kotlin.String>()
            value.split(",").map { str ->
                if (str.contains("=")) {
                    val (key, v) = str.split("=")
                    coll.put(key.trim(), v.trim())
                } else {
                    val k: kotlin.String = coll.entries.last().key
                    coll.put(k, coll[k] + "," + str)
                }
            }
            return coll.map { (k, v) ->
                keyType.convert(k, name) to valueType.convert(v, name)
            }.toMap()
        }
    }
}

class ParsingException(message: String) : Exception(message)