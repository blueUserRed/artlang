package passes

import ast.AstNode
import ast.AstNodeVisitor

class MinMaxPosFinder : AstNodeVisitor<MutableMap<Int, Pair<Int, Int>>> {

    override fun visit(binary: AstNode.Binary): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(binary.left), find(binary.right))
    }

    override fun visit(literal: AstNode.Literal): MutableMap<Int, Pair<Int, Int>> {
        return mutableMapOf(literal.literal.line to
                (literal.literal.pos to literal.literal.pos + literal.literal.lexeme.length))
    }

    override fun visit(variable: AstNode.Variable): MutableMap<Int, Pair<Int, Int>> {
        val nameMap = mutableMapOf(variable.name.line to
                (variable.name.pos to variable.name.pos + variable.name.lexeme.length))
        if (variable.arrIndex != null) {
            return combine(nameMap, find(variable.arrIndex!!))
        }
        return nameMap
    }

    override fun visit(group: AstNode.Group): MutableMap<Int, Pair<Int, Int>> {
        return find(group)
    }

    override fun visit(unary: AstNode.Unary): MutableMap<Int, Pair<Int, Int>> {
        return combine(mutableMapOf(unary.operator.line to
                (unary.operator.pos to unary.operator.pos + unary.operator.lexeme.length)))
    }

    override fun visit(funcCall: AstNode.FunctionCall): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(funcCall.func), *Array(funcCall.arguments.size) { find(funcCall.arguments[it]) })
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
        val combined = mutableMapOf(print.printToken.line to
                (print.printToken.pos to print.printToken.pos + print.printToken.lexeme.length))
        return combine(combined, find(print.toPrint))
    }

    override fun visit(block: AstNode.Block): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(varDec: AstNode.VariableDeclaration): MutableMap<Int, Pair<Int, Int>> {
        val combined = mutableMapOf(varDec.decToken.line to
                (varDec.decToken.pos to varDec.decToken.pos + varDec.decToken.lexeme.length))
        return combine(combined, find(varDec.initializer))
    }

    override fun visit(varAssign: AstNode.Assignment): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(varAssign.name), find(varAssign.toAssign))
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
        val combined = mutableMapOf(returnStmt.returnToken.line to
                (returnStmt.returnToken.pos to returnStmt.returnToken.pos + returnStmt.returnToken.lexeme.length))
        return if (returnStmt.toReturn == null) combined else combine(combined, find(returnStmt.toReturn!!))
    }

    override fun visit(varInc: AstNode.VarIncrement): MutableMap<Int, Pair<Int, Int>> {
        return find(varInc.name)
    }

    override fun visit(get: AstNode.Get): MutableMap<Int, Pair<Int, Int>> {
        val combined = mutableMapOf(get.name.line to
                (get.name.pos to get.name.pos + get.name.lexeme.length))
        return if (get.from == null) combined else combine(combined, find(get.from!!))
    }

    override fun visit(cont: AstNode.Continue): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(breac: AstNode.Break): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(constructorCall: AstNode.ConstructorCall): MutableMap<Int, Pair<Int, Int>> {
        TODO("not yet implemented")
    }

    override fun visit(field: AstNode.FieldDeclaration): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(arr: AstNode.ArrayCreate): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    override fun visit(arr: AstNode.ArrayLiteral): MutableMap<Int, Pair<Int, Int>> {
        TODO("Not yet implemented")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun find(node: AstNode): MutableMap<Int, Pair<Int, Int>> = node.accept(this)

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
