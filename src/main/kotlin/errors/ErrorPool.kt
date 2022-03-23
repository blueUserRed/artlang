package errors

object ErrorPool {

    val errors: MutableList<Errors.ArtError> = mutableListOf()

    fun addError(error: Errors.ArtError) = errors.add(error)

    fun hasErrors(): Boolean = errors.isNotEmpty()

    fun printErrors() { for (err in errors) {
        println(err.constructString())
        println("\n\n")
    } }

}

fun artError(error: Errors.ArtError) = ErrorPool.addError(error)
