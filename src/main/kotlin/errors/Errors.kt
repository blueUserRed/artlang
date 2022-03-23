package errors

class Errors {

    abstract class ArtError(val errorCode: Int, val srcCode: String) {
        abstract val message: String
        abstract val until: Int
        abstract val to: Int
        abstract val line: Int

        fun constructString(): String {
            val builder = StringBuilder()
            builder.append(Ansi.red).append(message).append(Ansi.white).append("\n")
            for (i in (line - 2)..(line + 2)) if (i > 0) {
                val padAmount = getPadAmount(line)
                builder
                    .append(i.toString().padStart(padAmount, '0'))
                    .append("   ")
                    .append(Utils.getLine(srcCode, i))
                    .append("\n")

                if (i == line) {
                    builder.append(Ansi.red)
                    repeat(padAmount + 3) { builder.append(" ") }
                    for (cur in 0 until (to + 1)) builder.append(if (cur >= until) "^" else " ")
                    builder.append("--------- here").append(Ansi.white).append("\n")
                }

            }
            builder.append(Ansi.reset)
            return builder.toString()
        }

        private fun getPadAmount(line: Int): Int = (line + 2).toString().length

    }

    class UnknownCharacterError(
        val character: Char,
        val pos: Int,
        override val line: Int,
        srcCode: String
    ) : ArtError(0, srcCode) {
        override val message: String
            get() = "Illegal Character '$character'"
        override val until: Int = pos
        override val to: Int = pos
    }

    class UnterminatedStringError(
        val pos: Int,
        override val line: Int,
        srcCode: String
    ) : ArtError(1, srcCode) {
        override val message: String = "Unterminated String"
        override val until: Int = pos
        override val to: Int = pos
    }

}