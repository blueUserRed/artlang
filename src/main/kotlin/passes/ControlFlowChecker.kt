package passes

import ast.Expression
import ast.ExpressionVisitor
import ast.Statement
import ast.StatementVisitor
import passes.TypeChecker.Datatype
import java.lang.RuntimeException

//TODO: add more functionality when break and continue is introduced
class ControlFlowChecker : StatementVisitor<Boolean>, ExpressionVisitor<Boolean> {

    override fun visit(exp: Expression.Binary): Boolean {
        return false
    }

    override fun visit(exp: Expression.Literal): Boolean {
        return false
    }

    override fun visit(exp: Expression.Variable): Boolean {
        return false
    }

    override fun visit(exp: Expression.Group): Boolean {
        return false
    }

    override fun visit(exp: Expression.Unary): Boolean {
        return false
    }

    override fun visit(exp: Expression.FunctionCall): Boolean {
        return false
    }

    override fun visit(stmt: Statement.ExpressionStatement): Boolean {
        return check(stmt.exp)
    }

    override fun visit(stmt: Statement.Function): Boolean {
        if (stmt.returnType == Datatype.VOID) return false
        if (!check(stmt.statements)) throw RuntimeException("Function ${stmt.name.lexeme} does not always return")
        return false
    }

    override fun visit(stmt: Statement.Program): Boolean {
        for (func in stmt.funcs) check(func)
        for (c in stmt.classes) check(c)
        return false
    }

    override fun visit(stmt: Statement.Print): Boolean {
        return false
    }

    override fun visit(stmt: Statement.Block): Boolean {
        for (s in stmt.statements) if (check(s)) return true
        return false
    }

    override fun visit(stmt: Statement.VariableDeclaration): Boolean {
        return false
    }

    override fun visit(stmt: Statement.VariableAssignment): Boolean {
        return false
    }

    override fun visit(stmt: Statement.Loop): Boolean {
        return check(stmt.stmt) //TODO: will break when 'break'-keyword is introduced
    }

    override fun visit(stmt: Statement.If): Boolean {
        val ifBranch = check(stmt.ifStmt)
        val elseBranch = stmt.elseStmt?.let { check(it) } ?: false
        return ifBranch && elseBranch
    }

    override fun visit(stmt: Statement.While): Boolean {
        return false
    }

    override fun visit(stmt: Statement.Return): Boolean {
        return true
    }

    override fun visit(stmt: Statement.VarIncrement): Boolean {
        return false
    }

    override fun visit(stmt: Statement.ArtClass): Boolean {
        return false
    }

    private fun check(stmt: Statement): Boolean = stmt.accept(this)
    private fun check(expr: Expression): Boolean = expr.accept(this)
}
