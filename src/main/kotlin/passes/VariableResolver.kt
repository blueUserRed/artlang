package passes

import ast.Expression
import ast.ExpressionVisitor
import ast.Statement
import ast.StatementVisitor
import java.lang.RuntimeException
import passes.TypeChecker.Datatype

class VariableResolver : StatementVisitor<Unit>, ExpressionVisitor<Unit> {

    private var curVars: MutableList<String> = mutableListOf()
    private var maxLocals: Int = 0

    private lateinit var curProgram: Statement.Program

    override fun visit(exp: Expression.Binary) {
        resolve(exp.left)
        resolve(exp.right)
    }

    override fun visit(exp: Expression.Literal) {
    }

    override fun visit(exp: Expression.Variable) {
        val index = curVars.indexOf(exp.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown Variable: ${exp.name.lexeme}")
        exp.index = index
    }

    override fun visit(stmt: Statement.ExpressionStatement) {
        resolve(stmt.exp)
    }

    override fun visit(stmt: Statement.Function) {
        val vars = mutableListOf<String>()
        for (entry in stmt.argTokens) vars.add(entry.first.lexeme)
        curVars = vars
        resolve(stmt.statements)
        stmt.amountLocals = maxLocals
    }

    override fun visit(stmt: Statement.Program) {
        curProgram = stmt
        for (func in stmt.funcs) resolve(func)
    }

    override fun visit(stmt: Statement.Print) {
        resolve(stmt.toPrint)
    }

    override fun visit(stmt: Statement.Block) {
        val before = curVars.toMutableList()
        for (s in stmt.statements) resolve(s)
        if (curVars.size > maxLocals) maxLocals = curVars.size
        curVars = before
    }

    override fun visit(stmt: Statement.VariableDeclaration) {
        if (stmt.name.lexeme in curVars) throw RuntimeException("Redeclaration of variable ${stmt.name.lexeme}")
        resolve(stmt.initializer)
        curVars.add(stmt.name.lexeme)
        stmt.index = curVars.size - 1
    }

    override fun visit(stmt: Statement.VariableAssignment) {
        resolve(stmt.expr)
        val index = curVars.indexOf(stmt.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown variable ${stmt.name.lexeme}")
        stmt.index = index
    }

    override fun visit(stmt: Statement.Loop) {
        resolve(stmt.stmt)
    }

    override fun visit(stmt: Statement.If) {
        resolve(stmt.condition)
        resolve(stmt.ifStmt)
        stmt.elseStmt?.let { resolve(it) }
    }

    override fun visit(exp: Expression.Group) {
        resolve(exp.grouped)
    }

    override fun visit(exp: Expression.Unary) {
        resolve(exp.exp)
    }

    override fun visit(stmt: Statement.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    override fun visit(exp: Expression.FunctionCall) {
        for (arg in exp.arguments) resolve(arg)
    }

    override fun visit(stmt: Statement.Return) {
        stmt.returnExpr?.let { resolve(it) }
    }

    private fun resolve(expr: Expression) = expr.accept(this)
    private fun resolve(stmt: Statement) = stmt.accept(this)

}