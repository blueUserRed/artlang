package ast

import passes.TypeChecker
import tokenizer.Token

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

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

}