package passes

import ast.AstNode
import ast.AstNodeVisitor
import tokenizer.Token

//TODO: fix error reporting with strings, because the quotes are not included in the lexeme
class MinMaxPosFinder : AstNodeVisitor<MutableMap<Int, Pair<Int, Int>>> {

    override fun visit(binary: AstNode.Binary): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(binary.left), find(binary.right), getMinMaxFor(binary.relevantTokens))
    }

    override fun visit(literal: AstNode.Literal): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(literal.literal), getMinMaxFor(literal.relevantTokens))
    }

    override fun visit(variable: AstNode.Variable): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(variable.name), getMinMaxFor(variable.relevantTokens))
    }

    override fun visit(group: AstNode.Group): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(group.grouped), getMinMaxFor(group.relevantTokens))
    }

    override fun visit(unary: AstNode.Unary): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(unary.operator), find(unary.on), getMinMaxFor(unary.relevantTokens))
    }

    override fun visit(funcCall: AstNode.FunctionCall): MutableMap<Int, Pair<Int, Int>> {
        return combine(
            getMinMaxFor(funcCall.name),
            *Array(funcCall.arguments.size) { find(funcCall.arguments[it]) },
            getMinMaxFor(funcCall.relevantTokens)
        )
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(exprStmt.exp))
    }

    override fun visit(function: AstNode.Function): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(function.relevantTokens)
    }

    override fun visit(program: AstNode.Program): MutableMap<Int, Pair<Int, Int>> {
        throw RuntimeException("the entire program is wrong")
    }

    override fun visit(clazz: AstNode.ArtClass): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(clazz.relevantTokens)
    }

    override fun visit(print: AstNode.Print): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(print.printToken), find(print.toPrint), getMinMaxFor(print.relevantTokens))
    }

    override fun visit(block: AstNode.Block): MutableMap<Int, Pair<Int, Int>> {
        return combine(
            getMinMaxFor(block.relevantTokens),
            *Array(block.statements.size) { find(block.statements[it]) }
        )
    }

    override fun visit(varDec: AstNode.VariableDeclaration): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(varDec.decToken), find(varDec.initializer), getMinMaxFor(varDec.relevantTokens))
    }

    override fun visit(varAssign: AstNode.Assignment): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(varAssign.name), find(varAssign.toAssign), getMinMaxFor(varAssign.relevantTokens))
    }

    override fun visit(loop: AstNode.Loop): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(loop.relevantTokens), find(loop.body))
    }

    override fun visit(ifStmt: AstNode.If): MutableMap<Int, Pair<Int, Int>> {
        val toRet = combine(
            find(ifStmt.ifStmt),
            find(ifStmt.condition),
            getMinMaxFor(ifStmt.relevantTokens)
        )
        return if (ifStmt.elseStmt == null) toRet else combine(find(ifStmt.elseStmt!!), toRet)
    }

    override fun visit(whileStmt: AstNode.While): MutableMap<Int, Pair<Int, Int>> {
        return combine(
            find(whileStmt.body),
            getMinMaxFor(whileStmt.relevantTokens),
            find(whileStmt.body)
        )
    }

    override fun visit(returnStmt: AstNode.Return): MutableMap<Int, Pair<Int, Int>> {
        val returnMap = combine(getMinMaxFor(returnStmt.returnToken), getMinMaxFor(returnStmt.relevantTokens))
        return if (returnStmt.toReturn == null) returnMap else combine(returnMap, find(returnStmt.toReturn!!))
    }

    override fun visit(varInc: AstNode.VarIncrement): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(varInc.name), getMinMaxFor(varInc.relevantTokens))
    }

    override fun visit(get: AstNode.Get): MutableMap<Int, Pair<Int, Int>> {
        val nameMap = combine(getMinMaxFor(get.name), getMinMaxFor(get.relevantTokens))
        return if (get.from == null) nameMap else combine(nameMap, find(get.from!!))
    }

    override fun visit(cont: AstNode.Continue): MutableMap<Int, Pair<Int, Int>> {
        return combine(getMinMaxFor(cont.continueToken))
    }

    override fun visit(breac: AstNode.Break): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(breac.breakToken)
    }

    override fun visit(arr: AstNode.ArrGet): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(arr.from), find(arr.arrIndex), getMinMaxFor(arr.relevantTokens))
    }

    override fun visit(arr: AstNode.ArrSet): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(arr.from), find(arr.arrIndex), find(arr.to), getMinMaxFor(arr.relevantTokens))
    }

    override fun visit(constructorCall: AstNode.ConstructorCall): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(constructorCall.relevantTokens)
    }

    override fun visit(field: AstNode.Field): MutableMap<Int, Pair<Int, Int>> {
        field as AstNode.FieldDeclaration
        return combine(
            *Array(field.modifiers.size) { getMinMaxFor(field.modifiers[it]) },
            find(field.initializer),
            getMinMaxFor(field.relevantTokens)
        )
    }

    override fun visit(arr: AstNode.ArrayCreate): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(arr.relevantTokens)
    }

    override fun visit(arr: AstNode.ArrayLiteral): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(arr.relevantTokens)
    }

    override fun visit(yieldArrow: AstNode.YieldArrow): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(yieldArrow.expr), getMinMaxFor(yieldArrow.relevantTokens))
    }

    override fun visit(varInc: AstNode.VarAssignShorthand): MutableMap<Int, Pair<Int, Int>> {
        val toRet = combine(
            getMinMaxFor(varInc.name),
            getMinMaxFor(varInc.operator),
            getMinMaxFor(varInc.relevantTokens)
        )
        return if (varInc.from == null) toRet else combine(find(varInc.from!!), toRet)
    }

    override fun visit(nul: AstNode.Null): MutableMap<Int, Pair<Int, Int>> {
        return getMinMaxFor(nul.relevantTokens)
    }

    override fun visit(convert: AstNode.TypeConvert): MutableMap<Int, Pair<Int, Int>> {
        return combine(find(convert.toConvert), getMinMaxFor(convert.to))
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun find(node: AstNode): MutableMap<Int, Pair<Int, Int>> = node.accept(this)

    private fun getMinMaxFor(token: Token): MutableMap<Int, Pair<Int, Int>> {
        if (token.line == -1) return mutableMapOf()
        return mutableMapOf(token.line to (token.pos to token.pos + token.lexeme.length))
    }

    private fun getMinMaxFor(tokens: List<Token>): MutableMap<Int, Pair<Int, Int>> {
        var acc: MutableMap<Int, Pair<Int, Int>> = mutableMapOf()
        for (t in tokens) if (t.line != -1) acc = combine(acc, getMinMaxFor(t))
        return acc
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
