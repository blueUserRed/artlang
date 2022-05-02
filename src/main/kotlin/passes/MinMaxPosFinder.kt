package passes

import ast.AstNode
import ast.AstNodeVisitor
import tokenizer.Token

//TODO: rewrite properly
class MinMaxPosFinder : AstNodeVisitor<MutableMap<Int, Pair<Int, Int>>> {

    override fun visit(binary: AstNode.Binary): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(binary.left), find(binary.right))
    }

    override fun visit(literal: AstNode.Literal): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(literal.literal)
    }

    override fun visit(variable: AstNode.Variable): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(variable.name)
    }

    override fun visit(group: AstNode.Group): MutableMap<Int, Pair<Int, Int>> {
        return find(group)
    }

    override fun visit(unary: AstNode.Unary): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(unary.operator), find(unary.on))
    }

    override fun visit(funcCall: AstNode.FunctionCall): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(funcCall.name), *Array(funcCall.arguments.size) { find(funcCall.arguments[it]) })
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): MutableMap<Int, Pair<Int, Int>> {
        return find(exprStmt.exp)
    }

    override fun visit(function: AstNode.Function): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(program: AstNode.Program): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(clazz: AstNode.ArtClass): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(print: AstNode.Print): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(print.printToken), find(print.toPrint))
    }

    override fun visit(block: AstNode.Block): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(varDec: AstNode.VariableDeclaration): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(varDec.decToken), find(varDec.initializer))
    }

    override fun visit(varAssign: AstNode.Assignment): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(varAssign.name), find(varAssign.toAssign))
    }

    override fun visit(loop: AstNode.Loop): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(ifStmt: AstNode.If): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(whileStmt: AstNode.While): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(returnStmt: AstNode.Return): MutableMap<Int, Pair<Int, Int>> {
        val returnMap = getMinMaxFor(returnStmt.returnToken)
        return if (returnStmt.toReturn == null) returnMap else combine(returnMap, find(returnStmt.toReturn!!))
    }

    override fun visit(varInc: AstNode.VarIncrement): MutableMap<Int, Pair<Int, Int>> {
        return find(varInc.name)
    }

    override fun visit(get: AstNode.Get): MutableMap<Int, Pair<Int, Int>> {
        val nameMap = getMinMaxFor(get.name)
        return if (get.from == null) nameMap else combine(nameMap, find(get.from!!))
    }

    override fun visit(cont: AstNode.Continue): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(cont.continueToken)
    }

    override fun visit(breac: AstNode.Break): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(breac.breakToken)
    }

    override fun visit(arr: AstNode.ArrGet): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(arr: AstNode.ArrSet): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(constructorCall: AstNode.ConstructorCall): MutableMap<Int, Pair<Int, Int>> {
        TODO("figure this out")
    }

    override fun visit(field: AstNode.Field): MutableMap<Int, Pair<Int, Int>> {
        field as AstNode.FieldDeclaration
        return combine(*Array(field.modifiers.size) { getMinMaxFor(field.modifiers[it]) }, find(field.initializer))
    }

    override fun visit(arr: AstNode.ArrayCreate): MutableMap<Int, Pair<Int, Int>> {
        TODO("figure this out")
    }

    override fun visit(arr: AstNode.ArrayLiteral): MutableMap<Int, Pair<Int, Int>> {
        TODO("figure this out")
    }

    override fun visit(yieldArrow: AstNode.YieldArrow): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(varInc: AstNode.VarAssignShorthand): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun find(node: AstNode): MutableMap<Int, Pair<Int, Int>> = node.accept(this)

    private fun getMinMaxFor(token: Token): MutableMap<Int, Pair<Int, Int>> {
        return mutableMapOf(token.line to (token.pos to token.pos + token.lexeme.length))
    }

    private fun combine(vararg lists: MutableMap<Int, Pair<Int, Int>>): MutableMap<Int, Pair<Int, Int>> {
        val combined = mutableMapOf<Int, Pair<Int, Int>>()
        for (list in lists) for (entry in list) {
            if (combined[entry.key] == null) {
                combined[entry.key] = entry.value
                continue
            }
            combined[entry.key] = Math.min(combined[entry.key]!!.first, entry.value.first) to
                                  Math.max(combined[entry.key]!!.second, entry.value.second)
        }
        return combined
    }
}
