package ast

interface ExpressionVisitor<T> {
    fun visit(exp: Expression.Binary): T
    fun visit(exp: Expression.Literal): T
    fun visit(exp: Expression.Variable): T
}