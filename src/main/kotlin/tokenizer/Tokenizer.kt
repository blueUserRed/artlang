package tokenizer

import errors.Errors
import errors.artError
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
            '~' -> { emit(TokenType.TILDE, "~", null); consume() }
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
                    if (canPeek() && peek() == '>') {
                        consume(); consume()
                        emit(TokenType.YIELD_ARROW, "=>", null, cur - lastLineBreakPos - 2)
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
            "byte" -> emit(TokenType.T_BYTE, "byte", null, start - lastLineBreakPos)
            "short" -> emit(TokenType.T_SHORT, "short", null, start - lastLineBreakPos)
            "int" -> emit(TokenType.T_INT, "int", null, start - lastLineBreakPos)
            "long" -> emit(TokenType.T_LONG, "long", null, start - lastLineBreakPos)
            "float" -> emit(TokenType.T_FLOAT, "float", null, start - lastLineBreakPos)
            "double" -> emit(TokenType.T_DOUBLE, "double", null, start - lastLineBreakPos)
            "str" -> emit(TokenType.T_STRING, "str", null, start - lastLineBreakPos)
            "bool" -> emit(TokenType.T_BOOLEAN, "bool", null, start - lastLineBreakPos)
            "return" -> emit(TokenType.K_RETURN, "return", null, start - lastLineBreakPos)
            "break" -> emit(TokenType.K_BREAK, "break", null, start - lastLineBreakPos)
            "continue" -> emit(TokenType.K_CONTINUE, "continue", null, start - lastLineBreakPos)
            "null" -> emit(TokenType.K_NULL, "null", null, start - lastLineBreakPos)
            "super" -> emit(TokenType.K_SUPER, "super", null, start - lastLineBreakPos)
            "as" -> emit(TokenType.K_AS, "as", null, start - lastLineBreakPos)
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

        val builder = StringBuilder()

        if (end()) {
            artError(Errors.UnterminatedStringError(start - lastLineBreakPos, curLine, code))
            return
        }

        while (current() != endChar) {

            when (val c = consume()) {
                '\\' -> {
                    if (end()) {
                        artError(Errors.UnterminatedStringError(start - lastLineBreakPos, curLine, code))
                        return
                    }
                    when (val c1 = consume()) {
                        '"' -> builder.append('"')
                        '\'' -> builder.append('\'')
                        'f' -> builder.append("\u000C")
                        't' -> builder.append('\t')
                        'b' -> builder.append('\b')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        '\\' -> builder.append('\\')
                        'u' -> doUnicodeStringEscape(start)?.let { builder.append(it) }
                        else -> {
                            artError(Errors.IllegalStringEscapeError(
                                c1,
                                cur - lastLineBreakPos - 1,
                                curLine,
                                code
                            ))
                        }
                    }
                }
                '\n' -> {
                    cur--
                    artError(Errors.UnterminatedStringError(start - lastLineBreakPos, curLine, code))
                    return
                }
                else -> builder.append(c)
            }

            if (end()) {
                artError(Errors.UnterminatedStringError(start - lastLineBreakPos, curLine, code))
                return
            }
        }

        consume() //consume ending char

        val rawString = code.substring(start until cur)
        val string = builder.toString()
        emit(TokenType.STRING, rawString, string, start - lastLineBreakPos)
    }

    /**
     * tokenizes a unicode string escape
     */
    private fun doUnicodeStringEscape(start: Int): String? {
        var c = 0
        repeat(4) {
            if (end() || consume() == '\n') {
                artError(Errors.UnterminatedStringError(start - lastLineBreakPos, curLine, code))
                return null
            }
            val curChar = last()
            try {
                c *= 16
                c += Integer.parseInt(curChar.toString(), 16)
            } catch (e: NumberFormatException) {
                artError(Errors.IllegalStringEscapeError(
                    curChar,
                    cur - 1 - lastLineBreakPos, curLine, code
                ))
                return null
            }
        }
        return String(Character.toChars(c))
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

        while (!end()) {
            if (consume() == '_') continue
            if (!last().isLetterOrDigit()) break
            num *= radix
            try {
                num += last().digitToInt(radix)
            } catch (e: NumberFormatException) {
                break
            }
        }

        cur--

        if (end() || radix != 10 || !tryConsume('.')) {
            if (!tryConsume('#')) {
                emit(TokenType.INT, code.substring(start until cur), num.toInt(), start - lastLineBreakPos)
                return
            }
            if (tryConsume('i', 'I')) {
                emit(TokenType.INT, code.substring(start until cur), num.toInt(), start - lastLineBreakPos)
            } else if (tryConsume('b', 'B')) {
                emit(TokenType.BYTE, code.substring(start until cur), num.toByte(), start - lastLineBreakPos)
            } else if (tryConsume('s', 'S')) {
                emit(TokenType.SHORT, code.substring(start until cur), num.toShort(), start - lastLineBreakPos)
            } else if (tryConsume('l', 'L')) {
                emit(TokenType.LONG, code.substring(start until cur), num, start - lastLineBreakPos)
            } else if (tryConsume('f', 'F')) {
                emit(TokenType.FLOAT, code.substring(start until cur), num.toFloat(), start - lastLineBreakPos)
            } else if (tryConsume('d', 'D')) {
                emit(TokenType.DOUBLE, code.substring(start until cur), num.toDouble(), start - lastLineBreakPos)
            } else {
                artError(Errors.InvalidNumLiteralTypeIdentifier(cur - lastLineBreakPos, curLine, consume(), code))
            }
            return
        }

        val dotIndex = cur - 1

        var afterComma = 0.0
        var numIts = 1
        var isFirstIt = true
        var wasntFloat = false
        while(!end()) {
            if (consume() == '_') continue
            if (!last().isDigit()) {
                if (isFirstIt) cur--
                if (isFirstIt) wasntFloat = true
                break
            }
            isFirstIt = false
            afterComma += last().digitToInt(10) / 10.0.pow(numIts)
            numIts++
        }
        cur--

        if (wasntFloat) {
            emit(TokenType.INT, code.substring(start until cur), num.toInt(), start - lastLineBreakPos)
            cur = dotIndex
            return
        }

        val commaNum = num + afterComma
        if (!tryConsume('#')) {
            emit(TokenType.FLOAT, code.substring(start until cur), commaNum.toFloat(), start - lastLineBreakPos)
            return
        }
        if (tryConsume('i', 'I')) {
            emit(TokenType.INT, code.substring(start until cur), commaNum.toInt(), start - lastLineBreakPos)
        } else if (tryConsume('b', 'B')) {
            emit(TokenType.BYTE, code.substring(start until cur), commaNum.toInt().toByte(), start - lastLineBreakPos)
        } else if (tryConsume('s', 'S')) {
            emit(TokenType.SHORT, code.substring(start until cur), commaNum.toInt().toShort(), start - lastLineBreakPos)
        } else if (tryConsume('l', 'L')) {
            emit(TokenType.LONG, code.substring(start until cur), commaNum.toLong(), start - lastLineBreakPos)
        } else if (tryConsume('f', 'F')) {
            emit(TokenType.FLOAT, code.substring(start until cur), commaNum.toFloat(), start - lastLineBreakPos)
        } else if (tryConsume('d', 'D')) {
            emit(TokenType.DOUBLE, code.substring(start until cur), commaNum, start - lastLineBreakPos)
        } else {
            artError(Errors.InvalidNumLiteralTypeIdentifier(cur - lastLineBreakPos, curLine, consume(), code))
        }
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
    private fun tryConsume(vararg cs: Char): Boolean {
        for (c in cs) if (current() == c) {
            consume()
            return true
        }
        return false
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
