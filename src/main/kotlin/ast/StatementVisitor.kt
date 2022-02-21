package ast

interface StatementVisitor<T> {
    fun visit(stmt: Statement.ExpressionStatement): T
    fun visit(stmt: Statement.Function): T
    fun visit(stmt: Statement.Program): T
    fun visit(stmt: Statement.Print): T
}