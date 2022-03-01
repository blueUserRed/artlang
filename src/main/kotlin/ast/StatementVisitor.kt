package ast

interface StatementVisitor<T> {
    fun visit(stmt: Statement.ExpressionStatement): T
    fun visit(stmt: Statement.Function): T
    fun visit(stmt: Statement.Program): T
    fun visit(stmt: Statement.Print): T
    fun visit(stmt: Statement.Block): T
    fun visit(stmt: Statement.VariableDeclaration): T
    fun visit(stmt: Statement.VariableAssignment): T
    fun visit(stmt: Statement.Loop): T
    fun visit(stmt: Statement.If): T
    fun visit(stmt: Statement.While): T
    fun visit(stmt: Statement.Return): T
}