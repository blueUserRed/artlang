package errors

import ast.AstNode
import passes.MinMaxPosFinder
import tokenizer.Token

class Errors {

    abstract class ArtError(val errorCode: Int, val srcCode: String) {
        abstract val message: String
        abstract val ranges: MutableMap<Int, Pair<Int, Int>>

        fun constructString(): String {
            val builder = StringBuilder()
            builder.append(Ansi.red).append(message).append(Ansi.white).append("\n")
            val (minLine, maxLine) = getMinAndMaxLine()
            val padAmount = getPadAmount(maxLine)
            for (i in (minLine - 2)..(maxLine + 2)) if (i > 0) {

                builder
                    .append(Ansi.blue)
                    .append(i.toString().padStart(padAmount, '0'))
                    .append(Ansi.reset)
                    .append("   ")
                    .append(Utils.getLine(srcCode, i))
                    .append("\n")

                if (i in ranges.keys) {
                    val from = ranges[i]!!.first
                    val until = ranges[i]!!.second
                    builder.append(Ansi.red)
                    repeat(padAmount + 3) { builder.append(" ") }
                    for (cur in 0 until (until + 1)) builder.append(if (cur >= from) "~" else " ")
                    builder.append(" <--------- here").append(Ansi.white).append("\n")
                }

            }
            builder.append(Ansi.reset)
            return builder.toString()
        }

        private fun getPadAmount(maxLine: Int): Int = (maxLine + 2).toString().length

        private fun getMinAndMaxLine(): Pair<Int, Int> {
            var max = -1
            var min = Integer.MAX_VALUE
            for (line in ranges.keys) {
                if (line > max) max = line
                if (line < min) min = line
            }
            return min to max
        }

        protected fun getRangesFromTokens(tokens: List<Token>): MutableMap<Int, Pair<Int, Int>> {
            val toRet = mutableMapOf<Int, Pair<Int, Int>>()
            for (token in tokens) {
                toRet[token.line] = token.pos to token.pos + token.lexeme.length
            }
            for (token in tokens) {
                if (toRet[token.line] == null)
                    toRet[token.line] = token.pos to token.pos + token.lexeme.length
                else toRet[token.line] = Math.min(toRet[token.line]!!.first, token.pos) to
                        Math.max(toRet[token.line]!!.second, token.pos + token.lexeme.length)
            }
            return toRet
        }

    }

    class UnknownCharacterError(
        val character: Char,
        val pos: Int,
        val line: Int,
        srcCode: String
    ) : ArtError(0, srcCode) {

        override val message: String
            get() = "Illegal Character '$character'"

        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(line to (pos to pos))
    }

    class UnterminatedStringError(
        val pos: Int,
        val line: Int,
        srcCode: String
    ) : ArtError(1, srcCode) {

        override val message: String = "Unterminated String"

        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(line to (pos to pos))
    }

    class ModifiersInTopLevelError(val modifiers: List<Token>, srcCode: String) : ArtError(2, srcCode) {
        override val message: String = "Modifiers are not allowed in the top level"

        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = getRangesFromTokens(modifiers)
    }

    class SyntaxError(val token: Token, override val message: String, srcCode: String) : ArtError(3, srcCode) {
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(token.line to (token.pos to token.pos + token.lexeme.length))
    }

    class InvalidMainFunctionDeclarationError(
        val reason: String,
        srcCode: String,
        val tokens: List<Token>
    ) : ArtError(4, srcCode) {

        override val message: String
            get() = "Invalid main function declaration: $reason"

        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = getRangesFromTokens(tokens)
    }

    class InvalidModifierError(
        val reason: String,
        val modifier: Token,
        srcCode: String
    ) : ArtError(5, srcCode) {
        override val message: String
            get() = "Illegal modifier: $reason"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(modifier.line to (modifier.pos to modifier.pos + modifier.lexeme.length))
    }

    class VarDeclarationWithoutBlockError(
        val declaration: AstNode.VariableDeclaration,
        srcCode: String
    ) : ArtError(6, srcCode) {
        override val message: String
            get() = "Cant declare variable in if/else or loop without a body surrounding it"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = declaration.accept(MinMaxPosFinder())
    }

    class InvalidAssignmentTargetError(val assignmentTarget: AstNode, srcCode: String) : ArtError(7, srcCode) {
        override val message: String
            get() = "Invalid assignment target, expected variable or field"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = assignmentTarget.accept(MinMaxPosFinder())
    }

    class InvalidIncrementTargetError(val incrementTarget: AstNode, srcCode: String) : ArtError(8, srcCode) {
        override val message: String
            get() = "Invalid increment/decrement target, expected variable or field"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = incrementTarget.accept(MinMaxPosFinder())
    }

    class InvalidArrayGetTargetError(val arrayGetTarget: AstNode, srcCode: String) : ArtError(9, srcCode) {
        override val message: String
            get() = "Invalid array-get target, expected variable or field"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = arrayGetTarget.accept(MinMaxPosFinder())
    }

}
