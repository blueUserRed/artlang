package tokenizer

import java.lang.RuntimeException

object Tokenizer {

    private var cur: Int = 0
    private var code: String = ""
    private var tokens: MutableList<Token> = mutableListOf()
    private var path: String = ""

    fun tokenize(s: String, file: String): List<Token> {
        cur = 0
        code = s
        tokens = mutableListOf()
        path = file

        while (!end()) when (val current = current()) {
            '(' -> { emit(TokenType.L_PAREN, "(", null); consume() }
            ')' -> { emit(TokenType.R_PAREN, ")", null); consume() }
            '{' -> { emit(TokenType.L_BRACE, "{", null); consume() }
            '}' -> { emit(TokenType.R_BRACE, "}", null); consume() }
            ';' -> { emit(TokenType.SEMICOLON, ";", null); consume() }
            ' ', '\t', '\n', '\r' -> consume()
            else -> {
                if (current.isLetter() || current == '_') {
                    identifier()
                    continue
                }
                throw RuntimeException("Unknown char '$current'")
            }
        }
        return tokens
    }

    private fun identifier() {
        val start = cur
        var next = current()
        while ((next.isLetterOrDigit() || next == '_')) {
            consume()
            if (end()) break
            next = current()
        }
        when (val identifier = code.substring(start until cur)) {
            "fn" -> emit(TokenType.K_FN, "fn", null, start)
            "print" -> emit(TokenType.K_FN, "print", null, start)
            else -> emit(TokenType.IDENTIFIER, identifier, identifier, start)
        }
    }

    private fun current(): Char = code[cur]

    private fun consume(): Char = code[cur++]

    private fun peek(): Char = code[cur + 1]

    private fun end(): Boolean = cur >= code.length

    private fun tryConsume(c: Char): Boolean {
        return if (current() == c) {
            consume()
            true
        } else false
    }

    private fun emit(type: TokenType, lexeme: String, literal: Any?, position: Int = cur) {
        tokens.add(Token(type, lexeme, literal, path, position))
    }

}