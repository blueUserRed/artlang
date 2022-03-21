package passes

import ast.AstNode
import ast.AstNodeVisitor
import kotlin.RuntimeException

class VariableResolver : AstNodeVisitor<Unit> {

    private var curVars: MutableList<String> = mutableListOf()
    private var varDeclarations: MutableList<AstNode.VariableDeclaration?> = mutableListOf()
    private var maxLocals: Int = 0

    private var swap: AstNode? = null

    private lateinit var curProgram: AstNode.Program

    override fun visit(binary: AstNode.Binary) {
        resolve(binary.left, binary)
        resolve(binary.right, binary)
    }

    override fun visit(literal: AstNode.Literal) {
    }

    override fun visit(variable: AstNode.Variable) {
        throw RuntimeException("unreachable")
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement) {
        resolve(exprStmt.exp, exprStmt)
    }

    override fun visit(function: AstNode.Function) {
        val vars = mutableListOf<String>()
        val varDecs = mutableListOf<AstNode.VariableDeclaration?>()
        if (function.hasThis) {
            vars.add("this")
            varDecs.add(null)
        }
        for (arg in function.args) vars.add(arg.first.lexeme)
        for (arg in function.args) varDecs.add(null)
        curVars = vars
        varDeclarations = varDecs
        maxLocals = vars.size
        resolve(function.statements, function)
        function.amountLocals = maxLocals
    }

    override fun visit(program: AstNode.Program) {
        curProgram = program

        for (field in program.fields) resolve(field, program)
        for (func in program.funcs) resolve(func, program)
        for (c in program.classes) resolve(c, program)
    }

    override fun visit(print: AstNode.Print) {
        resolve(print.toPrint, print)
    }

    override fun visit(block: AstNode.Block) {
        val before = curVars.toMutableList()
        val beforeDecs = varDeclarations.toMutableList()
        for (s in block.statements) resolve(s, block)
        if (curVars.size > maxLocals) maxLocals = curVars.size
        curVars = before
        varDeclarations = beforeDecs
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        if (varDec.name.lexeme in curVars) throw RuntimeException("Redeclaration of variable ${varDec.name.lexeme}")
        resolve(varDec.initializer, varDec)
        curVars.add(varDec.name.lexeme)
        varDeclarations.add(varDec)
        varDec.index = curVars.size - 1
    }

    override fun visit(varAssign: AstNode.Assignment) {
        varAssign.arrIndex?.let { resolve(it, varAssign) }
        resolve(varAssign.toAssign, varAssign)
        if (varAssign.name.from != null) {
            resolve(varAssign.name.from!!, varAssign.name)
            return
        }
        val index = curVars.indexOf(varAssign.name.name.lexeme)
        if (index == -1) {
            varAssign.index = -1
            return
        }
        // if varDeclarations[index] == null the variable is a function arg or 'this', which is const
        if (varDeclarations[index]?.isConst ?: true && varAssign.arrIndex == null) {
            throw RuntimeException("Tried to assign to const ${varAssign.name.name.lexeme}")
        }
        varAssign.index = index
    }

    override fun visit(loop: AstNode.Loop) {
        resolve(loop.body, loop)
    }

    override fun visit(ifStmt: AstNode.If) {
        resolve(ifStmt.condition, ifStmt)
        resolve(ifStmt.ifStmt, ifStmt)
        ifStmt.elseStmt?.let { resolve(it, ifStmt) }
    }

    override fun visit(group: AstNode.Group) {
        resolve(group.grouped, group)
    }

    override fun visit(unary: AstNode.Unary) {
        resolve(unary.on, unary)
    }

    override fun visit(whileStmt: AstNode.While) {
        resolve(whileStmt.condition, whileStmt)
        resolve(whileStmt.body, whileStmt)
    }

    override fun visit(funcCall: AstNode.FunctionCall) {
        resolve(funcCall.func, funcCall)
        for (arg in funcCall.arguments) resolve(arg, funcCall)
    }

    override fun visit(returnStmt: AstNode.Return) {
        returnStmt.toReturn?.let { resolve(it, returnStmt) }
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        if (varInc.name.from != null) {
            resolve(varInc.name.from!!, varInc.name)
            return
        }
        val index = curVars.indexOf(varInc.name.name.lexeme)
        varInc.index = index
    }

    override fun visit(clazz: AstNode.ArtClass) {
        for (field in clazz.fields) resolve(field, clazz)
        for (field in clazz.staticFields) resolve(field, clazz)
        for (func in clazz.staticFuncs) resolve(func, clazz)
        for (func in clazz.funcs) resolve(func, clazz)
    }

    override fun visit(get: AstNode.Get) {
        if (get.arrIndex != null) resolve(get.arrIndex!!, get)
        if (get.from != null) {
            resolve(get.from!!, get)
            return
        }
        val index = curVars.indexOf(get.name.lexeme)
        if (index == -1) return
        val toSwap = AstNode.Variable(get.name)
        toSwap.index = index
        toSwap.arrIndex = get.arrIndex
        swap = toSwap
        return
    }

    override fun visit(cont: AstNode.Continue) {
    }

    override fun visit(breac: AstNode.Break) {
    }

    override fun visit(constructorCall: AstNode.ConstructorCall) {
        for (arg in constructorCall.arguments) resolve(arg, constructorCall)
    }

    override fun visit(field: AstNode.FieldDeclaration) {
        resolve(field.initializer, field)
    }

    private fun resolve(node: AstNode, parent: AstNode?) {
        val res = node.accept(this)
        if (swap == null) return res
        if (parent == null) throw AstNode.CantSwapException()
        parent.swap(node, swap!!)
        swap = null
        return res
    }

    override fun visit(arr: AstNode.ArrayCreate) {
        resolve(arr.amount, arr)
    }

    override fun visit(arr: AstNode.ArrayLiteral) {
        for (el in arr.elements) resolve(el, arr)
    }
}
