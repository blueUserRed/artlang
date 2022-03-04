package passes

import ast.AstNode
import ast.AstNodeVisitor
import java.lang.RuntimeException

class VariableResolver : AstNodeVisitor<Unit> {

    private var curVars: MutableList<String> = mutableListOf()
    private var varDeclarations: MutableList<AstNode.VariableDeclaration> = mutableListOf()
    private var maxLocals: Int = 0

    private lateinit var curProgram: AstNode.Program

    override fun visit(exp: AstNode.Binary) {
        resolve(exp.left)
        resolve(exp.right)
    }

    override fun visit(exp: AstNode.Literal) {
    }

    override fun visit(exp: AstNode.Variable) {
        val index = curVars.indexOf(exp.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown Variable: ${exp.name.lexeme}")
        exp.index = index
    }

    override fun visit(stmt: AstNode.ExpressionStatement) {
        resolve(stmt.exp)
    }

    override fun visit(stmt: AstNode.Function) {
        val vars = mutableListOf<String>()
        for (entry in stmt.argTokens) vars.add(entry.first.lexeme)
        curVars = vars
        resolve(stmt.statements)
        stmt.amountLocals = maxLocals
    }

    override fun visit(stmt: AstNode.Program) {
        curProgram = stmt
        for (func in stmt.funcs) resolve(func)
        for (c in stmt.classes) resolve(c)
    }

    override fun visit(stmt: AstNode.Print) {
        resolve(stmt.toPrint)
    }

    override fun visit(stmt: AstNode.Block) {
        val before = curVars.toMutableList()
        val beforeDecs = varDeclarations.toMutableList()
        for (s in stmt.statements) resolve(s)
        if (curVars.size > maxLocals) maxLocals = curVars.size
        curVars = before
        varDeclarations = beforeDecs
    }

    override fun visit(stmt: AstNode.VariableDeclaration) {
        if (stmt.name.lexeme in curVars) throw RuntimeException("Redeclaration of variable ${stmt.name.lexeme}")
        resolve(stmt.initializer)
        curVars.add(stmt.name.lexeme)
        varDeclarations.add(stmt)
        stmt.index = curVars.size - 1
    }

    override fun visit(stmt: AstNode.VariableAssignment) {
        resolve(stmt.toAssign)
        val index = curVars.indexOf(stmt.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown variable ${stmt.name.lexeme}")
        if (varDeclarations[index].isConst) throw RuntimeException("Tried to assign to const ${stmt.name.lexeme}")
        stmt.index = index
    }

    override fun visit(stmt: AstNode.Loop) {
        resolve(stmt.body)
    }

    override fun visit(stmt: AstNode.If) {
        resolve(stmt.condition)
        resolve(stmt.ifStmt)
        stmt.elseStmt?.let { resolve(it) }
    }

    override fun visit(exp: AstNode.Group) {
        resolve(exp.grouped)
    }

    override fun visit(exp: AstNode.Unary) {
        resolve(exp.on)
    }

    override fun visit(stmt: AstNode.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    override fun visit(exp: AstNode.FunctionCall) {
        for (arg in exp.arguments) resolve(arg)
    }

    override fun visit(stmt: AstNode.Return) {
        stmt.toReturn?.let { resolve(it) }
    }

    override fun visit(stmt: AstNode.VarIncrement) {
        val index = curVars.indexOf(stmt.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown variable ${stmt.name.lexeme}")
        stmt.index = index
    }

    override fun visit(stmt: AstNode.ArtClass) {
        for (func in stmt.funcs) resolve(func)
    }

    override fun visit(exp: AstNode.WalrusAssign) {
        resolve(exp.toAssign)
        val index = curVars.indexOf(exp.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown variable ${exp.name.lexeme}")
        exp.index = index
    }

    private fun resolve(node: AstNode) = node.accept(this)

}
