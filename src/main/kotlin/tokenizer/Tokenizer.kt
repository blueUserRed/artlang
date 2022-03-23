package tokenizer

import errors.Errors
import errors.artError
import java.lang.RuntimeException
import kotlin.math.pow

/**
 * separates a string in a list of tokens
 */
object Tokenizer {

    /**
     * the current character
     */
    private var cur: Int = 0

    private var lastLineBreakPos: Int = 0
    private var curLine: Int = 1

    private var code: String = ""
    private var tokens: MutableList<Token> = mutableListOf()

    /**
     * the path to the source-file
     */
    private var path: String = ""

    /**
     * tokenizes a String
     * @param s the string
     * @param file the source fiel
     * @return the tokens
     */
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
            '\n' -> {
                emit(TokenType.SOFT_BREAK, "\n", null)
                consume()
                lastLineBreakPos = cur
                curLine++
            }
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
                    emit(TokenType.D_AND, "&&", null, cur - lastLineBreakPos - 2)
                    continue
                }

                if (current == '|' && canPeek() && peek() == '|') {
                    consume(); consume()
                    emit(TokenType.D_OR, "||", null, cur - lastLineBreakPos - 2)
                    continue
                }

                if (current == '+') {
                    if (canPeek() && peek() == '+') {
                        consume(); consume()
                        emit(TokenType.D_PLUS, "++", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.PLUS_EQ, "+=", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.PLUS, "+", null)
                    consume()
                    continue
                }

                if (current == '-') {
                    if (canPeek() && peek() == '-') {
                        consume(); consume()
                        emit(TokenType.D_MINUS, "--", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.MINUS_EQ, "-=", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.MINUS, "-", null)
                    consume()
                    continue
                }

                if (current == '*') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.STAR_EQ, "*=", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.STAR, "*", null)
                    consume()
                    continue
                }

                if (current == '/') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.SLASH_EQ, "/=", null, cur - lastLineBreakPos - 2)
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
                        emit(TokenType.D_EQ, "==", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.EQ, "=", null)
                    consume()
                    continue
                }

                if (current == '<') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.LT_EQ, "<=", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.LT, "<", null)
                    consume()
                    continue
                }

                if (current == '>') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.GT_EQ, ">=", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.GT, ">", null)
                    consume()
                    continue
                }

                if (current == '!') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.NOT_EQ, "!=", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.NOT, "!", null)
                    consume()
                    continue
                }

                if (current == ':') {
                    if (canPeek() && peek() == '=') {
                        consume(); consume()
                        emit(TokenType.WALRUS, ":=", null, cur - lastLineBreakPos - 2)
                        continue
                    }
                    emit(TokenType.COLON, ":", null)
                    consume()
                    continue
                }

                artError(Errors.UnknownCharacterError(consume(), cur - lastLineBreakPos - 1, curLine, code))
            }
        }
        tokens.add(Token(TokenType.EOF, "", null, path, cur - lastLineBreakPos, curLine))
        return tokens
    }

    /**
     * emits an identifier, if this identifier is a keyword it emits a keyword instead
     */
    private fun identifier() {
        val start = cur
        var next = current()
        while ((next.isLetterOrDigit() || next == '_')) {
            consume()
            if (end()) break
            next = current()
        }
        when (val identifier = code.substring(start until cur)) {
            "fn" -> emit(TokenType.K_FN, "fn", null, start - lastLineBreakPos)
            "print" -> emit(TokenType.K_PRINT, "print", null, start - lastLineBreakPos)
            "class" -> emit(TokenType.K_CLASS, "class", null, start - lastLineBreakPos)
            "let" -> emit(TokenType.K_LET, "let", null, start - lastLineBreakPos)
            "const" -> emit(TokenType.K_CONST, "const", null, start - lastLineBreakPos)
            "for" -> emit(TokenType.K_FOR, "for", null, start - lastLineBreakPos)
            "loop" -> emit(TokenType.K_LOOP, "loop", null, start - lastLineBreakPos)
            "if" -> emit(TokenType.K_IF, "if", null, start - lastLineBreakPos)
            "else" -> emit(TokenType.K_ELSE, "else", null, start - lastLineBreakPos)
            "while" -> emit(TokenType.K_WHILE, "while", null, start - lastLineBreakPos)
            "true" -> emit(TokenType.BOOLEAN, "true", true, start - lastLineBreakPos)
            "false" -> emit(TokenType.BOOLEAN, "false", false, start - lastLineBreakPos)
            "int" -> emit(TokenType.T_INT, "int", null, start - lastLineBreakPos)
            "str" -> emit(TokenType.T_STRING, "str", null, start - lastLineBreakPos)
            "bool" -> emit(TokenType.T_BOOLEAN, "bool", null, start - lastLineBreakPos)
            "return" -> emit(TokenType.K_RETURN, "return", null, start - lastLineBreakPos)
            "break" -> emit(TokenType.K_BREAK, "break", null, start - lastLineBreakPos)
            "continue" -> emit(TokenType.K_CONTINUE, "continue", null, start - lastLineBreakPos)
            else -> emit(TokenType.IDENTIFIER, identifier, identifier, start - lastLineBreakPos)
        }
    }

    /**
     * tokenizes a string
     * @param endChar the character that ends the string
     */
    private fun string(endChar: Char) {
        val start = cur
        consume() //consume initial " or '
        while (current() != endChar) {
            if (last() == '\n') {
                cur--
                artError(Errors.UnterminatedStringError(start - lastLineBreakPos, curLine, code))
                return
            }
            consume()
            if (end()) {
                artError(Errors.UnterminatedStringError(start - lastLineBreakPos, curLine, code))
                return
            }
        }
        consume() //consume ending " or '
        val string = code.substring((start + 1)..(cur - 2))
        emit(TokenType.STRING, string, string, start - lastLineBreakPos)
    }

    /**
     * skips to the next line, emits a soft break
     */
    private fun lineComment() {
        while (consume() != '\n' && !end());
        curLine++
        lastLineBreakPos = cur
        emit(TokenType.SOFT_BREAK, "\n", null)
    }

    /**
     * skips all characters until a `*`/ is encountered
     */
    private fun blockComment() {
        consume(); consume()
        while (!end()) {
            if (tryConsume('\n')) {
                curLine++
                lastLineBreakPos = cur
            }
            if (tryConsume('*') && tryConsume('/')) break else consume()
        }
    }

    /**
     * tokenizes a number
     */
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
                emit(TokenType.INT, code.substring(start until cur), decNum.toInt(), start - lastLineBreakPos)
                return
            }
        }
        cur--

        if (end() || radix != 10 || !tryConsume('.')) {
            emit(TokenType.INT, code.substring(start until cur), num.toInt(), start - lastLineBreakPos)
            return
        }

        var afterComma = 0.0
        var numIts = 1
        while(!end() && consume().isDigit()) {
            afterComma += last().digitToInt(10) / 10.0.pow(numIts)
            numIts++
        }
        val commaNum = (num + afterComma)
        emit(TokenType.FLOAT, code.substring(start until cur), commaNum.toFloat(), start - lastLineBreakPos)
    }

    /**
     * the current character
     */
    private fun current(): Char = code[cur]

    /**
     * return the current character and move on
     */
    private fun consume(): Char = code[cur++]

    /**
     * returns the next character
     */
    private fun peek(): Char = code[cur + 1]

    /**
     * true if the end is reached
     */
    private fun end(): Boolean = cur >= code.length

    /**
     * true if a call to [peek] is possible without an IndexOutOfBoundException
     */
    private fun canPeek(): Boolean = cur < code.length - 1

    /**
     * returns the last character
     */
    private fun last(): Char = code[cur - 1]

    /**
     * consumes the current character if it is equal to [c]
     * @param c the character that should be consumed
     * @return true if the character was consumed
     */
    private fun tryConsume(c: Char): Boolean {
        return if (current() == c) {
            consume()
            true
        } else false
    }

    /**
     * emits a token
     * @param type the TokenType
     * @param lexeme the lexeme of the token
     * @param literal the literal of the token
     * @param position the position of the first character of the token, default is [cur] - [lastLineBreakPos]
     * @param line the line of the first character of the token, default is [curLine]
     */
    private fun emit(
        type: TokenType,
        lexeme: String,
        literal: Any?,
        position: Int = cur - lastLineBreakPos,
        line: Int = curLine) {
        tokens.add(Token(type, lexeme, literal, path, position, line))
    }
}
