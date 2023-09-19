package angstromio.flags

enum class TestEnum {
    LOCAL,
    STAGING,
    PRODUCTION;

    override fun toString(): String = name.lowercase()
}