package passes

import ast.AstNode
import ast.AstNodeVisitor
import java.lang.RuntimeException

class VariableResolver : AstNodeVisitor<Unit> {

    private var curVars: MutableList<String> = mutableListOf()
    private var varDeclarations: MutableList<AstNode.VariableDeclaration> = mutableListOf()
    private var maxLocals: Int = 0

    private lateinit var curProgram: AstNode.Program

    override fun visit(binary: AstNode.Binary) {
        resolve(binary.left)
        resolve(binary.right)
    }

    override fun visit(literal: AstNode.Literal) {
    }

    override fun visit(variable: AstNode.Variable) {
        val index = curVars.indexOf(variable.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown Variable: ${variable.name.lexeme}")
        variable.index = index
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement) {
        resolve(exprStmt.exp)
    }

    override fun visit(function: AstNode.Function) {
        val vars = mutableListOf<String>()
        for (entry in function.argTokens) vars.add(entry.first.lexeme)
        curVars = vars
        resolve(function.statements)
        function.amountLocals = maxLocals
    }

    override fun visit(program: AstNode.Program) {
        curProgram = program
        for (func in program.funcs) resolve(func)
        for (c in program.classes) resolve(c)
    }

    override fun visit(print: AstNode.Print) {
        resolve(print.toPrint)
    }

    override fun visit(stmt: AstNode.Block) {
        val before = curVars.toMutableList()
        val beforeDecs = varDeclarations.toMutableList()
        for (s in stmt.statements) resolve(s)
        if (curVars.size > maxLocals) maxLocals = curVars.size
        curVars = before
        varDeclarations = beforeDecs
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        if (varDec.name.lexeme in curVars) throw RuntimeException("Redeclaration of variable ${varDec.name.lexeme}")
        resolve(varDec.initializer)
        curVars.add(varDec.name.lexeme)
        varDeclarations.add(varDec)
        varDec.index = curVars.size - 1
    }

    override fun visit(varAssign: AstNode.VariableAssignment) {
        resolve(varAssign.toAssign)
        val index = curVars.indexOf(varAssign.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown variable ${varAssign.name.lexeme}")
        if (varDeclarations[index].isConst) throw RuntimeException("Tried to assign to const ${varAssign.name.lexeme}")
        varAssign.index = index
    }

    override fun visit(loop: AstNode.Loop) {
        resolve(loop.body)
    }

    override fun visit(ifStmt: AstNode.If) {
        resolve(ifStmt.condition)
        resolve(ifStmt.ifStmt)
        ifStmt.elseStmt?.let { resolve(it) }
    }

    override fun visit(group: AstNode.Group) {
        resolve(group.grouped)
    }

    override fun visit(unary: AstNode.Unary) {
        resolve(unary.on)
    }

    override fun visit(whileStmt: AstNode.While) {
        resolve(whileStmt.condition)
        resolve(whileStmt.body)
    }

    override fun visit(funcCall: AstNode.FunctionCall) {
        if (funcCall.func is Either.Left) resolve(funcCall.func.value)
        for (arg in funcCall.arguments) resolve(arg)
    }

    override fun visit(returnStmt: AstNode.Return) {
        returnStmt.toReturn?.let { resolve(it) }
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        val index = curVars.indexOf(varInc.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown variable ${varInc.name.lexeme}")
        varInc.index = index
    }

    override fun visit(clazz: AstNode.ArtClass) {
        for (func in clazz.funcs) resolve(func)
    }

    override fun visit(walrus: AstNode.WalrusAssign) {
        resolve(walrus.toAssign)
        val index = curVars.indexOf(walrus.name.lexeme)
        if (index == -1) throw RuntimeException("Unknown variable ${walrus.name.lexeme}")
        walrus.index = index
    }

    override fun visit(get: AstNode.Get) {
//        resolve(get.from)
    }

    override fun visit(set: AstNode.Set) {
//        resolve(set.from)
        resolve(set.to)
    }

    override fun visit(walrus: AstNode.WalrusSet) {
//        resolve(walrus.from)
        resolve(walrus.to)
    }

    private fun resolve(node: AstNode) = node.accept(this)

}
