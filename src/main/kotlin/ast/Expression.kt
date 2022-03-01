package ast

import passes.TypeChecker
import tokenizer.Token
import passes.TypeChecker.Datatype

abstract class Expression {

    var type: TypeChecker.Datatype = TypeChecker.Datatype.VOID

    abstract fun <T> accept(visitor: ExpressionVisitor<T>): T

    class Binary(val left: Expression, val operator: Token, val right: Expression) : Expression() {

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

    class Literal(val literal: Token) : Expression() {

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

    class Variable(val name: Token) : Expression() {

        var index: Int = 0

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

    class Group(val grouped: Expression) : Expression() {

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

    class Unary(val exp: Expression, val operator: Token) : Expression() {

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

    class FunctionCall(val name: Token, val arguments: List<Expression>) : Expression() {

        var funcIndex: Int = 0

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

}