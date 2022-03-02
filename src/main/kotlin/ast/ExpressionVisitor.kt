package ast

interface ExpressionVisitor<T> {
    fun visit(exp: Expression.Binary): T
    fun visit(exp: Expression.Literal): T
    fun visit(exp: Expression.Variable): T
    fun visit(exp: Expression.Group): T
    fun visit(exp: Expression.Unary): T
    fun visit(exp: Expression.FunctionCall): T
}