package passes

import ast.AstNode
import ast.AstNodeVisitor
import ast.SyntheticNode
import errors.Errors
import errors.artError
import kotlin.RuntimeException

/**
 * maps all variables to an index and set that index on references to that local
 */
class VariableResolver : AstNodeVisitor<Unit> {

    /**
     * variables in the current scope
     *
     * does *not* correspond to the locals array on the jvm, because two word values only take up one index
     */
    private var curVars: MutableList<String> = mutableListOf()

    /**
     * the declarations of the variable in [curVars], null if the variable has no declaration (this, function-arg)
     */
    private var varDeclarations: MutableList<AstNode.VariableDeclaration?> = mutableListOf()

    /**
     * if swap is set to value != null, the current node will be swapped with swap
     */
    private var swap: AstNode? = null

    private lateinit var program: AstNode.Program
    private lateinit var srcCode: String

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
        function as AstNode.FunctionDeclaration
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
        function.statements?.let { resolve(it, function) }
    }

    override fun visit(program: AstNode.Program) {
        this.program = program
        srcCode = program.srcCode

        for (field in program.fields) if (field !is SyntheticNode) resolve(field, program)
        for (func in program.funcs) if (func !is SyntheticNode) resolve(func, program)
        for (c in program.classes) if (c !is SyntheticNode) resolve(c, program)
    }

    override fun visit(print: AstNode.Print) {
        resolve(print.toPrint, print)
    }

    override fun visit(block: AstNode.Block) {
        val before = curVars.toMutableList()
        val beforeDecs = varDeclarations.toMutableList()
        for (s in block.statements) resolve(s, block)
        curVars = before
        varDeclarations = beforeDecs
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        if (varDec.name.lexeme in curVars) {
            artError(Errors.DuplicateDefinitionError(varDec.name, "variable", srcCode))
        }
        resolve(varDec.initializer, varDec)
        curVars.add(varDec.name.lexeme)
        varDeclarations.add(varDec)
        varDec.index = curVars.size - 1
    }

    override fun visit(varAssign: AstNode.Assignment) {
//        varAssign.arrIndex?.let { resolve(it, varAssign) }
        resolve(varAssign.toAssign, varAssign)
        if (varAssign.from != null) {
            resolve(varAssign.from!!, varAssign)
            return
        }
        val index = curVars.indexOf(varAssign.name.lexeme)
        if (index == -1) {
            varAssign.index = -1
            return
        }
        // if varDeclarations[index] == null the variable is a function arg or 'this', which is const
        if (varDeclarations[index]?.isConst ?: true) {
            artError(Errors.AssignToConstError(varAssign, varAssign.name.lexeme, srcCode))
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
        funcCall.from?.let { resolve(it, funcCall) }
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
        if (index != -1) {
            if (varDeclarations[index]!!.isConst) {
                artError(Errors.AssignToConstError(
                    varInc,
                    varDeclarations[index]!!.name.lexeme,
                    srcCode
                ))
            }
        }
        varInc.index = index
    }

    override fun visit(clazz: AstNode.ArtClass) {
        for (field in clazz.fields) resolve(field, clazz)
        for (field in clazz.staticFields) resolve(field, clazz)
        for (func in clazz.staticFuncs) resolve(func, clazz)
        for (func in clazz.funcs) resolve(func, clazz)
        for (con in clazz.constructors) resolve(con, clazz)
    }

    override fun visit(get: AstNode.Get) {
        if (get.from != null) {
            resolve(get.from!!, get)
            return
        }
        val index = curVars.indexOf(get.name.lexeme)
        if (index == -1) return
        val toSwap = AstNode.Variable(get.name, get.relevantTokens)
        toSwap.index = index
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

    override fun visit(field: AstNode.Field) {
        field as AstNode.FieldDeclaration
        curVars.clear()
        varDeclarations.clear()
        if (!field.isTopLevel && !field.isStatic) {
            curVars.add("this")
            varDeclarations.add(null)
        }
        field.initializer?.let { resolve(it, field) }
    }

    override fun visit(arr: AstNode.ArrayCreate) {
        for (amount in arr.amounts) resolve(amount, arr)
    }

    override fun visit(arr: AstNode.ArrayLiteral) {
        for (el in arr.elements) resolve(el, arr)
    }

    override fun visit(arr: AstNode.ArrGet) {
        resolve(arr.from, arr)
        resolve(arr.arrIndex, arr)
    }

    override fun visit(arr: AstNode.ArrSet) {
        resolve(arr.from, arr)
        resolve(arr.arrIndex, arr)
        resolve(arr.to, arr)
    }

    override fun visit(yieldArrow: AstNode.YieldArrow) {
        resolve(yieldArrow.expr, yieldArrow)
    }

    override fun visit(varInc: AstNode.VarAssignShorthand) {
        if (varInc.from != null) {
            resolve(varInc.from!!, varInc)
            return
        }
        val index = curVars.indexOf(varInc.name.lexeme)
        varInc.index = index
    }

    override fun visit(nul: AstNode.Null) {
    }

    override fun visit(convert: AstNode.TypeConvert) {
        resolve(convert.toConvert, convert)
    }

    override fun visit(supCall: AstNode.SuperCall) {
    }

    override fun visit(cast: AstNode.Cast) {
        resolve(cast.toCast, cast)
    }

    override fun visit(instanceOf: AstNode.InstanceOf) {
        resolve(instanceOf.toCheck, instanceOf)
    }

    override fun visit(constructor: AstNode.Constructor) {
        constructor as AstNode.ConstructorDeclaration

        val vars = mutableListOf<String>()
        val varDecs = mutableListOf<AstNode.VariableDeclaration?>()
        vars.add("this")
        varDecs.add(null)

        val fieldAssignArgsIndices = constructor.fieldAssignArgsIndices

        for (i in constructor.args.indices) if (i !in fieldAssignArgsIndices) {
            val arg = constructor.args[i]
            vars.add(arg.first.lexeme)
            varDecs.add(null)
        }

        curVars = vars
        varDeclarations = varDecs

        constructor.superCallArgs?.forEach { resolve(it, constructor) }

        constructor.body?.let { resolve(it, constructor) }
    }

    /**
     * resolves all variables in a node, also handles swapping
     * @param parent the parent of [node], necessary for swapping. If a swap is attempted and [parent] is null a
     * [AstNode.CantSwapException] is thrown
     */
    private fun resolve(node: AstNode, parent: AstNode?) {
        val res = node.accept(this)
        if (swap == null) return res
        if (parent == null) throw AstNode.CantSwapException()
        parent.swap(node, swap!!)
        swap = null
        return res
    }
}
