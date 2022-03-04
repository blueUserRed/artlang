package ast
import passes.TypeChecker.Datatype
import tokenizer.Token

abstract class AstNode {

    var type: Datatype = Datatype.VOID

    abstract fun <T> accept(visitor: AstNodeVisitor<T>): T

    class ExpressionStatement(val exp: AstNode) : AstNode() { //TODO: necessary?

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Function(val statements: Block, val name: Token, val modifiers: List<Token>) : AstNode() {

        var amountLocals: Int = 0
        var argTokens: MutableList<Pair<Token, Token>> = mutableListOf()
        var args: MutableList<Pair<String, Datatype>> = mutableListOf()
        var returnType: Datatype = Datatype.VOID
        var returnTypeToken: Token? = null

        fun getDescriptor(): String {
            val builder = StringBuilder()
            builder.append("(")
            for (arg in args) builder.append(getDescriptorFromType(arg.second))
            builder.append(")")
            builder.append(getDescriptorFromType(returnType))
            return builder.toString()
        }

        private fun getDescriptorFromType(arg: Datatype) = when (arg) {
            Datatype.INT -> "I"
            Datatype.BOOLEAN -> "Z"
            Datatype.FLOAT -> "F"
            Datatype.STRING -> "Ljava/lang/String;"
            Datatype.VOID -> "V"
            else -> TODO("not yet implemented")
        }

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Program(val funcs: Array<Function>, val classes: Array<ArtClass>) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class ArtClass(val name: Token, val funcs: Array<Function>) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Print(val toPrint: AstNode) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Block(val statements: Array<AstNode>) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class VariableDeclaration(val name: Token, val initializer: AstNode) : AstNode() {

        var index: Int = 0
        var typeToken: Token? = null

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class VariableAssignment(val name: Token, val toAssign: AstNode) : AstNode() {

        var index: Int = 0

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Loop(val body: AstNode) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class If(val ifStmt: AstNode, val elseStmt: AstNode?, val condition: AstNode) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class While(val body: AstNode, val condition: AstNode) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Return(val toReturn: AstNode?) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class VarIncrement(val name: Token, val toAdd: Byte) : AstNode() {

        var index: Int = 0

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Binary(val left: AstNode, val operator: Token, val right: AstNode) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Literal(val literal: Token) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Variable(val name: Token) : AstNode() {

        var index: Int = 0

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Group(val grouped: AstNode) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class Unary(val on: AstNode, val operator: Token) : AstNode() {

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class FunctionCall(val name: Token, val arguments: List<AstNode>) : AstNode() {

        var funcIndex: Int = 0

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

    class WalrusAssign(val name: Token, val toAssign: AstNode) : AstNode() {

        var index: Int = 0

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = visitor.visit(this)
    }

}
