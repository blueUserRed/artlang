package passes

import ast.AstNode
import ast.AstNodeVisitor
import ast.SyntheticNode
import errors.Errors
import errors.artError

class InheritanceChecker : AstNodeVisitor<Unit> {

    lateinit var srcCode: String

    override fun visit(binary: AstNode.Binary) = throw RuntimeException("unreachable")

    override fun visit(literal: AstNode.Literal) = throw RuntimeException("unreachable")

    override fun visit(variable: AstNode.Variable) = throw RuntimeException("unreachable")

    override fun visit(group: AstNode.Group) = throw RuntimeException("unreachable")

    override fun visit(unary: AstNode.Unary) = throw RuntimeException("unreachable")

    override fun visit(funcCall: AstNode.FunctionCall) = throw RuntimeException("unreachable")

    override fun visit(exprStmt: AstNode.ExpressionStatement) = throw RuntimeException("unreachable")

    override fun visit(function: AstNode.Function) {
        function as AstNode.FunctionDeclaration
        if (function.clazz == null) return
        if (function.isStatic) {
            if (function.hasModifier("override")) {
                artError(Errors.FunctionDoesNotOverrideAnythingError(
                    function.modifiers.filter { it.lexeme == "override" }[0],
                    function.name,
                    srcCode
                ))
            }
            return
        }
        val overriddenFunc =
            Datatype.Object(function.clazz!!.extends!!).lookupFuncExact(function.name, function.functionDescriptor)
        if (overriddenFunc != null && !overriddenFunc.isPrivate) {
            if (function.isPrivate) {
                artError(Errors.CantWeakenAccessModifiersError(
                    function.nameToken,
                    srcCode
                ))
            }
            if (!function.hasModifier("override")) {
                artError(Errors.FunctionRequiresOverrideModifierError(
                    function.nameToken,
                    srcCode
                ))
            }
            return
        }
        if (function.hasModifier("override")) {
            artError(Errors.FunctionDoesNotOverrideAnythingError(
                function.modifiers.filter { it.lexeme == "override" }[0],
                function.name,
                srcCode
            ))
        }
    }

    override fun visit(program: AstNode.Program) {
        program.classes.filter { it !is SyntheticNode }.forEach { it.accept(this) }
        program.funcs.filter { it !is SyntheticNode }.forEach { it.accept(this) }
    }

    override fun visit(clazz: AstNode.ArtClass) {
        clazz.funcs.filter { it !is SyntheticNode }.forEach { it.accept(this) }
        clazz.staticFuncs.filter { it !is SyntheticNode }.forEach { it.accept(this) }
    }

    override fun visit(print: AstNode.Print) = throw RuntimeException("unreachable")

    override fun visit(block: AstNode.Block) = throw RuntimeException("unreachable")

    override fun visit(varDec: AstNode.VariableDeclaration) = throw RuntimeException("unreachable")

    override fun visit(varAssign: AstNode.Assignment) = throw RuntimeException("unreachable")

    override fun visit(loop: AstNode.Loop) = throw RuntimeException("unreachable")

    override fun visit(ifStmt: AstNode.If) = throw RuntimeException("unreachable")

    override fun visit(whileStmt: AstNode.While) = throw RuntimeException("unreachable")

    override fun visit(returnStmt: AstNode.Return) = throw RuntimeException("unreachable")

    override fun visit(varInc: AstNode.VarIncrement) = throw RuntimeException("unreachable")

    override fun visit(get: AstNode.Get) = throw RuntimeException("unreachable")

    override fun visit(cont: AstNode.Continue) = throw RuntimeException("unreachable")

    override fun visit(breac: AstNode.Break) = throw RuntimeException("unreachable")

    override fun visit(constructorCall: AstNode.ConstructorCall) = throw RuntimeException("unreachable")

    override fun visit(field: AstNode.Field) = throw RuntimeException("unreachable")

    override fun visit(arr: AstNode.ArrayCreate) = throw RuntimeException("unreachable")

    override fun visit(arr: AstNode.ArrayLiteral) = throw RuntimeException("unreachable")

    override fun visit(arr: AstNode.ArrGet) = throw RuntimeException("unreachable")

    override fun visit(arr: AstNode.ArrSet) = throw RuntimeException("unreachable")

    override fun visit(varInc: AstNode.VarAssignShorthand) = throw RuntimeException("unreachable")

    override fun visit(yieldArrow: AstNode.YieldArrow) = throw RuntimeException("unreachable")
}