package errors

/**
 * stores all errors accumulated during compilation
 */
object ErrorPool {

    /**
     * the errors
     */
    val errors: MutableList<Errors.ArtError> = mutableListOf()

    /**
     * adds a new error to the pool
     */
    fun addError(error: Errors.ArtError) = errors.add(error)

    /**
     * true if the pool contains errors
     */
    fun hasErrors(): Boolean = errors.isNotEmpty()

    /**
     * prints the errors to the console
     */
    fun printErrors() { for (err in errors) {
        println(err.constructString())
        println("\n")
    } }

}

/**
 * adds a new error to the [ErrorPool]
 */
fun artError(error: Errors.ArtError) = ErrorPool.addError(error)
