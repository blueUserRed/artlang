package ast

import passes.TypeChecker.Datatype
import tokenizer.Token

abstract class Statement {

    abstract fun <T> accept(visitor: StatementVisitor<T>): T

    class ExpressionStatement(val exp: Expression) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class Function(val statements: Block, val name: Token) : Statement() {

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

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class Program(val funcs: Array<Function>) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class Print(val toPrint: Expression) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class Block(val statements: Array<Statement>) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class VariableDeclaration(val name: Token, val initializer: Expression) : Statement() {

        var index: Int = 0
        var type: Datatype = Datatype.VOID
        var typeToken: Token? = null

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class VariableAssignment(val name: Token, val expr: Expression) : Statement() {

        var index: Int = 0
        var type: Datatype = Datatype.VOID

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class Loop(val stmt: Statement) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class If(val ifStmt: Statement, val elseStmt: Statement?, val condition: Expression) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class While(val body: Statement, val condition: Expression) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class Return(val returnExpr: Expression?) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class VarIncrement(val name: Token, val toAdd: Byte) : Statement() {

        var index: Int = 0
        var type: Datatype = Datatype.VOID

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

}