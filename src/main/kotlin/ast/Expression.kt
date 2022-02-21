package ast

import tokenizer.Token

abstract class Expression {

    abstract fun <T> accept(visitor: ExpressionVisitor<T>): T

    class Binary(val left: Expression, val operator: Token, val right: Expression) : Expression() {

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

    class Literal(val literal: Token) : Expression() {

        override fun <T> accept(visitor: ExpressionVisitor<T>): T = visitor.visit(this)
    }

}