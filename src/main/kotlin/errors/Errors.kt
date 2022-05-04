package errors

import ast.AstNode
import passes.MinMaxPosFinder
import Datatype
import tokenizer.Token

/**
 * contains the different errors
 */
class Errors {

    /**
     * base-class for all Errors
     * @param errorCode the unique code of the error, maybe useful in the future (for example, to provide more detailed
     *  explanations of errors)
     */
    abstract class ArtError(val errorCode: Int, val srcCode: String) {

        /**
         * the message displayed to the user
         */
        abstract val message: String

        /**
         * where in the file the error is located (Map<Linenumber, Pair<startInLine, stopInLine>>)
         */
        abstract val ranges: MutableMap<Int, Pair<Int, Int>>

        /**
         * constructs a string with the error-message and the lines where the error is located
         */
        fun constructString(): String {
            val builder = StringBuilder()
            builder
                .append(Ansi.red)
                .append(message)
                .append(Ansi.white)
                .append("\n")
            val (minLine, maxLine) = getMinAndMaxLine()
            val padAmount = getPadAmount(maxLine)
            val lines = srcCode.lines()
            for (i in (minLine - 2)..(maxLine + 2)) if (i > 0 && i <= lines.size) {

                builder
                    .append(Ansi.blue)
                    .append(i.toString().padStart(padAmount, '0'))
                    .append(Ansi.reset)
                    .append("   ")
                    .append(lines[i - 1])
                    .append("\n")

                if (i in ranges.keys) {
                    val from = ranges[i]!!.first
                    val until = ranges[i]!!.second
                    builder.append(Ansi.red)
                    repeat(padAmount + 3) { builder.append(" ") }
                    for (cur in 0 until Math.max(until, from + 1)) builder.append(if (cur >= from) "~" else " ")
                    builder
                        .append(" <--------- here")
                        .append(Ansi.white)
                        .append("\n")
                }

            }
            builder.append(Ansi.reset)
            return builder.toString()
        }

        /**
         * @return how much the line numbers need to be padded
         */
        private fun getPadAmount(maxLine: Int): Int = (maxLine + 2).toString().length

        /**
         * returns the amount of lines that need to be displayed
         */
        private fun getMinAndMaxLine(): Pair<Int, Int> {
            var max = -1
            var min = Integer.MAX_VALUE
            for (line in ranges.keys) {
                if (line > max) max = line
                if (line < min) min = line
            }
            return min to max
        }

        /**
         * gets the ranges of the error from tokens
         */
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

    class IllegalTypesInBinaryOperationError(
        val operator: String,
        val type1: Datatype,
        val type2: Datatype,
        val operation: AstNode,
        srcCode: String
    ) : ArtError(10, srcCode) {
        override val message: String
            get() = "Invalid types in binary operation '$operator': $type1 and $type2"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = operation.accept(MinMaxPosFinder())
    }

    class InvalidNumLiteralTypeIdentifier(
        val pos: Int,
        val line: Int,
        val character: Char,
        srcCode: String
    ) : ArtError(11, srcCode) {
        override val message: String
            get() = "Invalid literal type character: $character"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(line to (pos to pos))
    }

    class IncompatibleTypesError(
        val stmt: AstNode,
        val context: String,
        val type1: Datatype,
        val type2: Datatype,
        srcCode: String
    ) : ArtError(12, srcCode) {
        override val message: String = "incompatible types in $context: $type1 and $type2"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = stmt.accept(MinMaxPosFinder())
    }

    class DuplicateDefinitionError(
        val definitionToken: Token,
        val of: String,
        srcCode: String
    ) : ArtError(13, srcCode) {
        override val message: String = "duplicate definition of $of ${definitionToken.lexeme}"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(
                definitionToken.line to (definitionToken.pos to definitionToken.pos + definitionToken.lexeme.length)
            )
    }

    class UnknownIdentifierError(
        val identifier: Token,
        srcCode: String
    ) : ArtError(14, srcCode) {
        override val message: String = "Unknown identifier ${identifier.lexeme}"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(identifier.line to (identifier.pos to identifier.pos + identifier.lexeme.length))
    }

    class InheritanceLoopError(
        override val message: String,
        val name: Token,
        srcCode: String
    ) : ArtError(15, srcCode) {
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(name.line to (name.pos to name.pos + name.lexeme.length))
    }

    class ExpectedAnExpressionError(
        val notExpression: AstNode,
        srcCode: String
    ) : ArtError(16, srcCode) {
        override val message: String = "Expected an expression"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = notExpression.accept(MinMaxPosFinder())
    }

    class ArrayIndexTypeError(
        val expr: AstNode,
        val acutalType: Datatype,
        srcCode: String
    ) : ArtError(17, srcCode) {
        override val message: String = "Arrays can only be indexed by int, found $acutalType"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = expr.accept(MinMaxPosFinder())
    }

    class InvalidGetSetReceiverError(
        val receiver: AstNode,
        val action: String,
        srcCode: String
    ) : ArtError(18, srcCode) {
        override val message: String = "$action can only be used on an array"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = receiver.accept(MinMaxPosFinder())
    }

    class AssignToConstError(
        val assignment: AstNode,
        val name: String,
        srcCode: String
    ) : ArtError(19, srcCode) {
        override val message: String = "Cannot assign to const $name"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = assignment.accept(MinMaxPosFinder())
    }

    class PrivateMemberAccessError(
        val access: AstNode,
        val type: String,
        val name: String,
        srcCode: String
    ) : ArtError(20, srcCode) {
        override val message: String = "Cannot access private $type $name"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = access.accept(MinMaxPosFinder())
    }

    class ExpectedConditionError(
        val notCondition: AstNode,
        val actualType: Datatype,
        srcCode: String
    ) : ArtError(21, srcCode) {
        override val message: String = "Expected a condition, found type $actualType instead"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = notCondition.accept(MinMaxPosFinder())
    }

    class OperationNotApplicableError(
        val operation: String,
        val type: Datatype,
        val stmt: AstNode,
        srcCode: String
    ) : ArtError(22, srcCode) {
        override val message: String = "Operation $operation is cannot be used on type $type"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = stmt.accept(MinMaxPosFinder())
    }

    class InvalidTypeInArrayCreateError(
        val arrCreate: AstNode,
        val found: Datatype,
        srcCode: String
    ) : ArtError(23, srcCode) {
        override val message: String = "Expected type int in array create, found $found"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = arrCreate.accept(MinMaxPosFinder())
    }

    class EmptyArrayLiteralError(
        val arrLiteral: AstNode,
        srcCode: String
    ) : ArtError(24, srcCode) {
        override val message: String = "Array literal must not be empty"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = arrLiteral.accept(MinMaxPosFinder())
    }

    class FunctionRequiresOverrideModifierError(
        val funcName: Token,
        srcCode: String
    ) : ArtError(25, srcCode) {
        override val message: String = "Function ${funcName.lexeme} overrides another function, needs 'override'-modifier"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(funcName.line to (funcName.pos to funcName.pos + funcName.lexeme.length))
    }

    class FunctionDoesNotOverrideAnythingError(
        val overrideToken: Token,
        val name: String,
        srcCode: String
    ) : ArtError(26, srcCode) {
        override val message: String = "Function $name doesn't override anything"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(overrideToken.line to (overrideToken.pos to overrideToken.pos + overrideToken.lexeme.length))
    }

    class CantWeakenAccessModifiersError(
        val name: Token,
        srcCode: String
    ) : ArtError(27, srcCode) {
        override val message: String = "Cannot weaken access privileges from public to private in overriding " +
                "function ${name.lexeme}"
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = mutableMapOf(name.line to (name.pos to name.pos + name.lexeme.length))
    }

    class CantInferTypeError(
        val stmt: AstNode,
        srcCode: String,
    ) : ArtError(28, srcCode) {
        override val message: String = "Cannot infer type, explicit type declaration required."
        override val ranges: MutableMap<Int, Pair<Int, Int>>
            get() = stmt.accept(MinMaxPosFinder())
    }

}
