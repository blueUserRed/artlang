package passes

import ast.AstNode
import ast.AstNodeVisitor
import passes.TypeChecker.Datatype
import java.lang.RuntimeException
import passes.ControlFlowChecker.ControlFlowState

class ControlFlowChecker : AstNodeVisitor<ControlFlowState> {

    private var surroundingLoop: AstNode? = null

    override fun visit(binary: AstNode.Binary): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(literal: AstNode.Literal): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(variable: AstNode.Variable): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(group: AstNode.Group): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(unary: AstNode.Unary): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(funcCall: AstNode.FunctionCall): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): ControlFlowState {
        return check(exprStmt.exp)
    }

    override fun visit(function: AstNode.Function): ControlFlowState {
        if (function.functionDescriptor.returnType == Datatype.Void()) return ControlFlowState()
        if (!check(function.statements).alwaysReturns) {
            throw RuntimeException("Function ${function.name.lexeme} does not always return")
        }
        return ControlFlowState()
    }

    override fun visit(program: AstNode.Program): ControlFlowState {
        for (field in program.fields) check(field)
        for (func in program.funcs) check(func)
        for (c in program.classes) check(c)
        return ControlFlowState()
    }

    override fun visit(print: AstNode.Print): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(block: AstNode.Block): ControlFlowState {
        var alwaysRet = false
        var alwaysBreak = false
        var sometimesBreak = false
        var sometimesRet = false
        for (s in block.statements) {
            val result = check(s)
            if (result.alwaysReturns) alwaysRet = true
            if (result.alwaysBreaks) alwaysBreak = true
            if (result.sometimesReturns) sometimesRet = true
            if (result.sometimesBreaks) sometimesBreak = true
        }
        return ControlFlowState(alwaysRet, alwaysBreak, sometimesRet, sometimesBreak)
    }

    override fun visit(varDec: AstNode.VariableDeclaration): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(varAssign: AstNode.VariableAssignment): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(loop: AstNode.Loop): ControlFlowState {
        val tmp = surroundingLoop
        surroundingLoop = loop
        val result = check(loop.body)
        var alwaysReturns = result.alwaysReturns
        if (!result.sometimesBreaks && result.sometimesReturns) alwaysReturns = true
        surroundingLoop = tmp
        return ControlFlowState(
            alwaysReturns = alwaysReturns,
            alwaysBreaks = false,
            sometimesReturns = result.sometimesReturns,
            sometimesBreaks = false
        )
    }

    override fun visit(ifStmt: AstNode.If): ControlFlowState {
        val ifBranch = check(ifStmt.ifStmt)
        val elseBranch = ifStmt.elseStmt?.let { check(it) } ?: ControlFlowState()
        return ControlFlowState(
            ifBranch.alwaysReturns && elseBranch.alwaysReturns,
            ifBranch.alwaysBreaks && elseBranch.alwaysBreaks,
            ifBranch.sometimesReturns || elseBranch.sometimesReturns,
            ifBranch.sometimesBreaks || elseBranch.sometimesBreaks
        )
    }

    override fun visit(whileStmt: AstNode.While): ControlFlowState {
        val result = check(whileStmt.body)
        return ControlFlowState(
            alwaysReturns = false,
            alwaysBreaks = false,
            sometimesReturns = result.sometimesReturns,
            sometimesBreaks = false
        )
    }

    override fun visit(returnStmt: AstNode.Return): ControlFlowState {
        return ControlFlowState(alwaysReturns = true, sometimesReturns = true)
    }

    override fun visit(varInc: AstNode.VarIncrement): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(clazz: AstNode.ArtClass): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(walrus: AstNode.WalrusAssign): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(get: AstNode.Get): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(set: AstNode.Set): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(walrus: AstNode.WalrusSet): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(cont: AstNode.Continue): ControlFlowState {
        if (surroundingLoop == null) throw RuntimeException("Used continue outside of a loop")
        return ControlFlowState()
    }

    override fun visit(breac: AstNode.Break): ControlFlowState {
        if (surroundingLoop == null) throw RuntimeException("Used break outside of a loop")
        return ControlFlowState(
            alwaysReturns = false,
            alwaysBreaks = true,
            sometimesReturns = false,
            sometimesBreaks = true
        )
    }

    override fun visit(constructorCall: AstNode.ConstructorCall): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(field: AstNode.FieldDeclaration): ControlFlowState {
        check(field.initializer)
        return ControlFlowState()
    }

    override fun visit(fieldGet: AstNode.FieldReference): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(fieldSet: AstNode.FieldSet): ControlFlowState {
        return ControlFlowState()
    }

    private fun check(node: AstNode): ControlFlowState = node.accept(this)

    data class ControlFlowState(
        val alwaysReturns: Boolean = false,
        val alwaysBreaks: Boolean = false,
        val sometimesReturns: Boolean = false,
        val sometimesBreaks: Boolean = false
    )

}
