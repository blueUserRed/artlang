package ast

import tokenizer.Token

abstract class Statement {

    abstract fun <T> accept(visitor: StatementVisitor<T>): T

    class ExpressionStatement(val exp: Expression) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class Function(val statements: Block, val name: Token) : Statement() {

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

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

    class VariableAssignment(val name: Token, val expr: Expression) : Statement() {

        override fun <T> accept(visitor: StatementVisitor<T>): T = visitor.visit(this)
    }

}