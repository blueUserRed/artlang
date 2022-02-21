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
            '"' -> string('"')
            '\'' -> string('\'')
            ' ', '\t', '\n', '\r' -> consume()

            else -> {

                if (current.isLetter() || current == '_') {
                    identifier()
                    continue
                }

                if (current == '+') {
                    if (canPeek() && peek() == '+') {
                        consume(); consume()
                        emit(TokenType.D_PLUS, "++", null, cur - 2)
                        continue
                    }
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.PLUS_EQ, "+=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.PLUS, "+", null)
                    consume()
                    continue
                }

                if (current == '-') {
                    if (canPeek() && peek() == '-') {
                        consume(); consume()
                        emit(TokenType.D_MINUS, "--", null, cur - 2)
                        continue
                    }
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.MINUS_EQ, "-=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.MINUS, "-", null)
                    consume()
                    continue
                }

                if (current == '*') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.STAR_EQ, "*=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.STAR, "*", null)
                    continue
                }

                if (current == '/') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.SLASH_EQ, "/=", null, cur - 2)
                        continue
                    }
                    if (canPeek() && peek() == '/') {
                        lineComment()
                        continue
                    }
                    if (canPeek() && peek() == '*') {
                        blockComment()
                        continue
                    }
                    emit(TokenType.SLASH, "/", null)
                    continue
                }

                if (current == '=') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.D_EQ, "==", null, cur - 2)
                        continue
                    }
                    emit(TokenType.EQ, "=", null)
                    continue
                }

                if (current == '<') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.LT_EQ, "<=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.LT, "<", null)
                    continue
                }

                if (current == '>') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.GT_EQ, ">=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.GT, ">", null)
                    continue
                }

                if (current == '!') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.NOT_EQ, "!=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.NOT, "!", null)
                    continue
                }

                throw RuntimeException("Unknown char '$current'") //TODO: replace with other Exception, do error reporting
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
            "print" -> emit(TokenType.K_PRINT, "print", null, start)
            "println" -> emit(TokenType.K_PRINTLN, "println", null, start)
            "class" -> emit(TokenType.K_CLASS, "class", null, start)
            "var" -> emit(TokenType.K_VAR, "var", null, start)
            "const" -> emit(TokenType.K_CONST, "const", null, start)
            "priv" -> emit(TokenType.K_PRIV, "priv", null, start)
            "pub" -> emit(TokenType.K_PUB, "pub", null, start)
            "abstract" -> emit(TokenType.K_ABSTRACT, "abstract", null, start)
            "static" -> emit(TokenType.K_STATIC, "static", null, start)
            "for" -> emit(TokenType.K_FOR, "for", null, start)
            "loop" -> emit(TokenType.K_LOOP, "loop", null, start)
            "else" -> emit(TokenType.K_ELSE, "else", null, start)
            "while" -> emit(TokenType.K_WHILE, "while", null, start)
            else -> emit(TokenType.IDENTIFIER, identifier, identifier, start)
        }
    }

    private fun string(endChar: Char) {
        val start = cur
        consume() //consume initial " or '
        while (current() != endChar) {
            consume()
            if (end()) throw RuntimeException("Unterminated String")
        }
        consume() //consume ending " or '
        val string = code.substring((start + 1)..(cur - 2))
        emit(TokenType.STRING, string, string, start)
    }

    private fun lineComment() {
        while (consume() != '\n' && !end());
    }

    private fun blockComment() {
        consume(); consume()
        while (!end()) if (tryConsume('*') && tryConsume('/')) break else consume()
    }

    private fun current(): Char = code[cur]

    private fun consume(): Char = code[cur++]

    private fun peek(): Char = code[cur + 1]

    private fun end(): Boolean = cur >= code.length

    private fun canPeek(): Boolean = cur < code.length - 1

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