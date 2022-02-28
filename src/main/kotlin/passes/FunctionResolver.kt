package passes

import ast.Expression
import ast.ExpressionVisitor
import ast.Statement
import ast.StatementVisitor
import passes.TypeChecker.Datatype
import java.lang.RuntimeException

class FunctionResolver : StatementVisitor<Unit>, ExpressionVisitor<Unit> {

    private lateinit var curProgram: Statement.Program

    override fun visit(exp: Expression.Binary) {
        resolve(exp.left)
        resolve(exp.right)
    }

    override fun visit(exp: Expression.Literal) {
    }

    override fun visit(exp: Expression.Variable) {
    }

    override fun visit(exp: Expression.Group) {
        resolve(exp.grouped)
    }

    override fun visit(exp: Expression.Unary) {
        resolve(exp.exp)
    }

    override fun visit(exp: Expression.FunctionCall) {
        val thisSig = mutableListOf<Datatype>()
        for (arg in exp.arguments) {
            resolve(arg)
            thisSig.add(arg.type)
        }
        var funcIndex: Int? = null
        for (i in curProgram.funcs.indices) {
            if (!doFuncSigsMatch(thisSig, curProgram.funcs[i].args)) continue
            funcIndex = i
        }
        if (funcIndex == null) throw RuntimeException("Function ${exp.name.lexeme} does not exist")
        exp.funcIndex = funcIndex
    }

    override fun visit(stmt: Statement.ExpressionStatement) {
        resolve(stmt.exp)
    }

    override fun visit(stmt: Statement.Function) {
        resolve(stmt.statements)
    }

    override fun visit(stmt: Statement.Program) {
        curProgram = stmt
        for (func in stmt.funcs) resolve(func)
    }

    override fun visit(stmt: Statement.Print) {
        resolve(stmt.toPrint)
    }

    override fun visit(stmt: Statement.Block) {
        for (s in stmt.statements) resolve(s)
    }

    override fun visit(stmt: Statement.VariableDeclaration) {
        resolve(stmt.initializer)
    }

    override fun visit(stmt: Statement.VariableAssignment) {
        resolve(stmt.expr)
    }

    override fun visit(stmt: Statement.Loop) {
        resolve(stmt.stmt)
    }

    override fun visit(stmt: Statement.If) {
        resolve(stmt.condition)
        resolve(stmt.ifStmt)
        stmt.elseStmt?.let { resolve(it) }
    }

    override fun visit(stmt: Statement.While) {
        resolve(stmt.body)
    }

    private fun doFuncSigsMatch(types1: List<Datatype>, types2: List<Pair<String, Datatype>>): Boolean {
        if (types1.size != types2.size) return false
        for (i in types1.indices) if (types1[i] != types2[i].second) return false
        return true
    }

    private fun resolve(stmt: Statement) = stmt.accept(this)
    private fun resolve(expr: Expression) = expr.accept(this)
}