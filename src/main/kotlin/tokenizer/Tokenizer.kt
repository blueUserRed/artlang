package tokenizer

import java.lang.RuntimeException
import kotlin.math.pow

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
            '[' -> { emit(TokenType.L_BRACKET, "[", null); consume() }
            ']' -> { emit(TokenType.R_BRACKET, "]", null); consume() }
            ';' -> { emit(TokenType.SEMICOLON, ";", null); consume() }
            '%' -> { emit(TokenType.MOD, "%", null); consume() }
            ',' -> { emit(TokenType.COMMA, ",", null); consume() }
            '.' -> { emit(TokenType.DOT, ".", null); consume() }
            '\n' -> { emit(TokenType.SOFT_BREAK, "\n", null); consume() }
            '"' -> string('"')
            '\'' -> string('\'')
            ' ', '\t', '\r' -> consume()

            else -> {

                if (current.isLetter() || current == '_') {
                    identifier()
                    continue
                }

                if (current.isDigit()) {
                    number()
                    continue
                }

                if (current == '&' && canPeek() && peek() == '&') {
                    consume(); consume()
                    emit(TokenType.D_AND, "&&", null, cur - 2)
                    continue
                }

                if (current == '|' && canPeek() && peek() == '|') {
                    consume(); consume()
                    emit(TokenType.D_OR, "||", null, cur - 2)
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
                    consume()
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
                    consume()
                    continue
                }

                if (current == '=') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.D_EQ, "==", null, cur - 2)
                        continue
                    }
                    emit(TokenType.EQ, "=", null)
                    consume()
                    continue
                }

                if (current == '<') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.LT_EQ, "<=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.LT, "<", null)
                    consume()
                    continue
                }

                if (current == '>') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.GT_EQ, ">=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.GT, ">", null)
                    consume()
                    continue
                }

                if (current == '!') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.NOT_EQ, "!=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.NOT, "!", null)
                    consume()
                    continue
                }

                if (current == ':') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.WALRUS, ":=", null, cur - 2)
                        continue
                    }
                    emit(TokenType.COLON, ":", null)
                    consume()
                    continue
                }

                throw RuntimeException("Unknown char '$current'") //TODO: replace with other Exception, do error reporting
            }
        }
        tokens.add(Token(TokenType.EOF, "", null, path, cur))
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
            "class" -> emit(TokenType.K_CLASS, "class", null, start)
            "let" -> emit(TokenType.K_LET, "let", null, start)
            "const" -> emit(TokenType.K_CONST, "const", null, start)
//            "private" -> emit(TokenType.K_PRIVATE, "private", null, start)
            "for" -> emit(TokenType.K_FOR, "for", null, start)
            "loop" -> emit(TokenType.K_LOOP, "loop", null, start)
            "if" -> emit(TokenType.K_IF, "if", null, start)
            "else" -> emit(TokenType.K_ELSE, "else", null, start)
            "while" -> emit(TokenType.K_WHILE, "while", null, start)
            "true" -> emit(TokenType.BOOLEAN, "true", true, start)
            "false" -> emit(TokenType.BOOLEAN, "false", false, start)
            "int" -> emit(TokenType.T_INT, "int", null, start)
            "str" -> emit(TokenType.T_STRING, "str", null, start)
            "bool" -> emit(TokenType.T_BOOLEAN, "bool", null, start)
            "return" -> emit(TokenType.K_RETURN, "return", null, start)
            "break" -> emit(TokenType.K_BREAK, "break", null, start)
            "continue" -> emit(TokenType.K_CONTINUE, "continue", null, start)
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
        emit(TokenType.SOFT_BREAK, "\n", null)
    }

    private fun blockComment() {
        consume(); consume()
        while (!end()) if (tryConsume('*') && tryConsume('/')) break else consume()
    }

    private fun number() {

        val start = cur

        var radix = 10
        if (tryConsume('0')) {
            if (tryConsume('b')) radix = 2
            else if (tryConsume('o')) radix = 8
            else if (tryConsume('x')) radix = 16
            else cur--

        }

        var num = 0L
        while(!end() && consume().isLetterOrDigit()) {
            num *= radix
            try {
                num += last().digitToInt(radix)
            } catch (e: NumberFormatException) {
                cur--
                val decNum = num / radix
                emit(TokenType.INT, code.substring(start until cur), decNum.toInt(), start)
                return
//                return OnjToken(OnjTokenType.INT, if (negative) -decNum else decNum, start)
            }
        }
        cur--

        if (end() || radix != 10 || !tryConsume('.')) {
            emit(TokenType.INT, code.substring(start until cur), num.toInt(), start)
            return
        }
//            return OnjToken(OnjTokenType.INT, if (negative) -num else num, start)

        var afterComma = 0.0
        var numIts = 1
        while(!end() && consume().isDigit()) {
            afterComma += last().digitToInt(10) / 10.0.pow(numIts)
            numIts++
        }
        val commaNum = (num + afterComma)
//        return OnjToken(OnjTokenType.FLOAT, if (negative) -commaNum else commaNum, start)
        emit (TokenType.FLOAT, code.substring(start until cur), commaNum.toFloat(), start)
    }

    private fun current(): Char = code[cur]

    private fun consume(): Char = code[cur++]

    private fun peek(): Char = code[cur + 1]

    private fun end(): Boolean = cur >= code.length

    private fun canPeek(): Boolean = cur < code.length - 1

    private fun last(): Char = code[cur - 1]

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
