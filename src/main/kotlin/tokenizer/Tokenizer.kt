package tokenizer

object Tokenizer {

    private var cur: Int = 0
    private var code: String = ""

    private fun current(): Char = code[cur]

    private fun consume(): Char = code[cur++]

    private fun peek(): Char = code[cur + 1]

    private fun tryConsume(c: Char): Boolean {
        return if (current() == c) {
            consume()
            true
        } else false
    }

}