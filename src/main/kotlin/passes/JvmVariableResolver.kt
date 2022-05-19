package passes

import Datatype
import ast.AstNode
import ast.AstNodeVisitor
import ast.SyntheticNode

class JvmVariableResolver : AstNodeVisitor<Unit> {

    private var jvmVars: MutableList<String?> = mutableListOf()
    private var maxLocals: Int = 0
    private var curClass: AstNode.ArtClass? = null

    override fun visit(binary: AstNode.Binary) {
        resolve(binary.left)
        resolve(binary.right)
    }

    override fun visit(literal: AstNode.Literal) {
    }

    override fun visit(variable: AstNode.Variable) {
        variable.jvmIndex = jvmVars.indexOf(variable.name.lexeme)
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement) {
        resolve(exprStmt.exp)
    }

    override fun visit(function: AstNode.Function) {
        function as AstNode.FunctionDeclaration
        maxLocals = 0
        jvmVars.clear()
        for (arg in function.functionDescriptor.args) addVar(arg.first, arg.second)
        resolve(function.statements)
        function.amountLocals = maxLocals
    }

    override fun visit(program: AstNode.Program) {
        for (field in program.fields) if (field !is SyntheticNode) resolve(field)
        for (func in program.funcs) if (func !is SyntheticNode) resolve(func)
        for (c in program.classes) if (c !is SyntheticNode) resolve(c)
    }

    override fun visit(print: AstNode.Print) {
        resolve(print.toPrint)
    }

    override fun visit(block: AstNode.Block) {
        val before = jvmVars.toMutableList()
        for (s in block.statements) resolve(s)
        if (jvmVars.size > maxLocals) maxLocals = jvmVars.size
        jvmVars = before
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        resolve(varDec.initializer)
        varDec.jvmIndex = addVar(varDec.name.lexeme, varDec.varType)
    }

    override fun visit(varAssign: AstNode.Assignment) {
        resolve(varAssign.toAssign)
        if (varAssign.from != null) {
            resolve(varAssign.from!!)
            return
        }
        varAssign.jvmIndex = jvmVars.indexOf(varAssign.name.lexeme)
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
        funcCall.from?.let { resolve(it) }
        for (arg in funcCall.arguments) resolve(arg)
    }

    override fun visit(returnStmt: AstNode.Return) {
        returnStmt.toReturn?.let { resolve(it) }
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        varInc.name.from?.let { resolve(it) }
        val index = jvmVars.indexOf(varInc.name.name.lexeme)
        varInc.index = index
    }

    override fun visit(clazz: AstNode.ArtClass) {
        curClass = clazz
        for (field in clazz.fields) resolve(field)
        for (field in clazz.staticFields) resolve(field)
        for (func in clazz.staticFuncs) resolve(func)
        for (func in clazz.funcs) resolve(func)
        curClass = null
    }

    override fun visit(get: AstNode.Get) {
        get.from?.let { resolve(it) }
    }

    override fun visit(cont: AstNode.Continue) {
    }

    override fun visit(breac: AstNode.Break) {
    }

    override fun visit(constructorCall: AstNode.ConstructorCall) {
        for (arg in constructorCall.arguments) resolve(arg)
    }

    override fun visit(field: AstNode.Field) {
        field as AstNode.FieldDeclaration
        maxLocals = 0
        jvmVars.clear()

        if (!field.isStatic && !field.isTopLevel) addVar("this", Datatype.Object(curClass!!))

        resolve(field.initializer)
        field.amountLocals = maxLocals
    }

    override fun visit(arr: AstNode.ArrayCreate) {
        for (amount in arr.amounts) resolve(amount)
    }

    override fun visit(arr: AstNode.ArrayLiteral) {
        for (el in arr.elements) resolve(el)
    }

    override fun visit(arr: AstNode.ArrGet) {
        resolve(arr.from)
        resolve(arr.arrIndex)
    }

    override fun visit(arr: AstNode.ArrSet) {
        resolve(arr.from)
        resolve(arr.arrIndex)
        resolve(arr.to)
    }

    override fun visit(yieldArrow: AstNode.YieldArrow) {
        resolve(yieldArrow.expr)
    }

    override fun visit(varInc: AstNode.VarAssignShorthand) {
        if (varInc.from != null) {
            resolve(varInc.from!!)
            return
        }
        varInc.jvmIndex = jvmVars.indexOf(varInc.name.lexeme)
    }

    override fun visit(nul: AstNode.Null) {
    }

    override fun visit(convert: AstNode.TypeConvert) {
        resolve(convert.toConvert)
    }

    private fun resolve(node: AstNode) {
        node.accept(this)
    }

    private fun addVar(name: String, type: Datatype): Int {
        if (!type.matches(Datakind.LONG, Datakind.DOUBLE)) {
            jvmVars.add(name)
            return jvmVars.size - 1
        } else {
            jvmVars.addAll(arrayOf(name, null))
            return jvmVars.size - 2
        }
    }

}