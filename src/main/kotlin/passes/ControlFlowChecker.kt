package passes

import ast.AstNode
import ast.AstNodeVisitor
import passes.TypeChecker.Datatype
import java.lang.RuntimeException

//TODO: add more functionality when break and continue is introduced
class ControlFlowChecker : AstNodeVisitor<Boolean> {

    override fun visit(exp: AstNode.Binary): Boolean {
        return false
    }

    override fun visit(exp: AstNode.Literal): Boolean {
        return false
    }

    override fun visit(exp: AstNode.Variable): Boolean {
        return false
    }

    override fun visit(exp: AstNode.Group): Boolean {
        return false
    }

    override fun visit(exp: AstNode.Unary): Boolean {
        return false
    }

    override fun visit(exp: AstNode.FunctionCall): Boolean {
        return false
    }

    override fun visit(stmt: AstNode.ExpressionStatement): Boolean {
        return check(stmt.exp)
    }

    override fun visit(stmt: AstNode.Function): Boolean {
        if (stmt.returnType == Datatype.VOID) return false
        if (!check(stmt.statements)) throw RuntimeException("Function ${stmt.name.lexeme} does not always return")
        return false
    }

    override fun visit(stmt: AstNode.Program): Boolean {
        for (func in stmt.funcs) check(func)
        for (c in stmt.classes) check(c)
        return false
    }

    override fun visit(stmt: AstNode.Print): Boolean {
        return false
    }

    override fun visit(stmt: AstNode.Block): Boolean {
        for (s in stmt.statements) if (check(s)) return true
        return false
    }

    override fun visit(stmt: AstNode.VariableDeclaration): Boolean {
        return false
    }

    override fun visit(stmt: AstNode.VariableAssignment): Boolean {
        return false
    }

    override fun visit(stmt:AstNode.Loop): Boolean {
        return check(stmt.body) //TODO: will break when 'break'-keyword is introduced
    }

    override fun visit(stmt: AstNode.If): Boolean {
        val ifBranch = check(stmt.ifStmt)
        val elseBranch = stmt.elseStmt?.let { check(it) } ?: false
        return ifBranch && elseBranch
    }

    override fun visit(stmt: AstNode.While): Boolean {
        return false
    }

    override fun visit(stmt: AstNode.Return): Boolean {
        return true
    }

    override fun visit(stmt: AstNode.VarIncrement): Boolean {
        return false
    }

    override fun visit(stmt: AstNode.ArtClass): Boolean {
        return false
    }

    override fun visit(exp: AstNode.WalrusAssign): Boolean {
        return false
    }

    private fun check(node: AstNode): Boolean = node.accept(this)
}
