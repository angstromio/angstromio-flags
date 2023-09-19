package angstromio.flags

data class DuplicateFlagException(val name: String) : Exception("Flag $name is already defined.")