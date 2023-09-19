package angstromio.flags

data class FlagException (val name: String) : Exception("Flag $name has encountered an exception.")