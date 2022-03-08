package passes

import ast.AstNode
import ast.AstNodeVisitor
import passes.TypeChecker.Datatype
import java.lang.RuntimeException

//TODO: add more functionality when break and continue is introduced
class ControlFlowChecker : AstNodeVisitor<Boolean> {

    override fun visit(binary: AstNode.Binary): Boolean {
        return false
    }

    override fun visit(literal: AstNode.Literal): Boolean {
        return false
    }

    override fun visit(variable: AstNode.Variable): Boolean {
        return false
    }

    override fun visit(group: AstNode.Group): Boolean {
        return false
    }

    override fun visit(unary: AstNode.Unary): Boolean {
        return false
    }

    override fun visit(funcCall: AstNode.FunctionCall): Boolean {
        return false
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): Boolean {
        return check(exprStmt.exp)
    }

    override fun visit(function: AstNode.Function): Boolean {
        if (function.functionDescriptor.returnType == Datatype.Void()) return false
        if (!check(function.statements)) throw RuntimeException("Function ${function.name.lexeme} does not always return")
        return false
    }

    override fun visit(program: AstNode.Program): Boolean {
        for (func in program.funcs) check(func)
        for (c in program.classes) check(c)
        return false
    }

    override fun visit(print: AstNode.Print): Boolean {
        return false
    }

    override fun visit(stmt: AstNode.Block): Boolean {
        for (s in stmt.statements) if (check(s)) return true
        return false
    }

    override fun visit(varDec: AstNode.VariableDeclaration): Boolean {
        return false
    }

    override fun visit(varAssign: AstNode.VariableAssignment): Boolean {
        return false
    }

    override fun visit(loop:AstNode.Loop): Boolean {
        return check(loop.body) //TODO: will break when 'break'-keyword is introduced
    }

    override fun visit(ifStmt: AstNode.If): Boolean {
        val ifBranch = check(ifStmt.ifStmt)
        val elseBranch = ifStmt.elseStmt?.let { check(it) } ?: false
        return ifBranch && elseBranch
    }

    override fun visit(whileStmt: AstNode.While): Boolean {
        return false
    }

    override fun visit(returnStmt: AstNode.Return): Boolean {
        return true
    }

    override fun visit(varInc: AstNode.VarIncrement): Boolean {
        return false
    }

    override fun visit(clazz: AstNode.ArtClass): Boolean {
        return false
    }

    override fun visit(walrus: AstNode.WalrusAssign): Boolean {
        return false
    }

    override fun visit(get: AstNode.Get): Boolean {
        return false
    }

    override fun visit(set: AstNode.Set): Boolean {
        return false
    }

    override fun visit(walrus: AstNode.WalrusSet): Boolean {
        return false
    }

    private fun check(node: AstNode): Boolean = node.accept(this)

}
