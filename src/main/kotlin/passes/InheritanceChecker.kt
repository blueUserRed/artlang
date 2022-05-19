package passes

import ast.AstNode
import ast.AstNodeVisitor
import ast.SyntheticNode
import errors.Errors
import errors.artError

/**
 * ensures that all inheritance rules are met. for example checks that all functions that override something
 */
class InheritanceChecker : AstNodeVisitor<Unit> {

    private lateinit var srcCode: String

    override fun visit(binary: AstNode.Binary) {
        check(binary.left)
        check(binary.right)
    }

    override fun visit(literal: AstNode.Literal) {
    }

    override fun visit(variable: AstNode.Variable) {
    }

    override fun visit(group: AstNode.Group) {
        check(group.grouped)
    }

    override fun visit(unary: AstNode.Unary) {
        check(unary.on)
    }

    override fun visit(funcCall: AstNode.FunctionCall) {
        funcCall.from?.let { check(it) }
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement) {
        check(exprStmt.exp)
    }

    override fun visit(function: AstNode.Function) {
        function as AstNode.FunctionDeclaration

        function.statements?.let { check(it) }

        if (function.clazz == null) return

        if (function.isAbstract && !function.clazz!!.isAbstract) {
            artError(Errors.AbstractFunctionOutsideAbstractClassError(
                function.modifiers.filter { it.lexeme == "abstract" }[0],
                srcCode
            ))
        }

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
            val retType = function.functionDescriptor.returnType
            val retTypeOverridden = overriddenFunc.functionDescriptor.returnType
            if (retType != retTypeOverridden && !retTypeOverridden.compatibleWith(retType)) {
                artError(Errors.NonMatchingReturnTypesInOverriddenFunctionError(
                    retType,
                    retTypeOverridden,
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
        srcCode = program.srcCode
        program.classes.filter { it !is SyntheticNode }.forEach { check(it) }
        program.funcs.filter { it !is SyntheticNode }.forEach { check(it) }
    }

    override fun visit(clazz: AstNode.ArtClass) {
        clazz as AstNode.ClassDefinition

        clazz.funcs.filter { it !is SyntheticNode }.forEach { it.accept(this) }
        clazz.staticFuncs.filter { it !is SyntheticNode }.forEach { it.accept(this) }

        var next: AstNode.ArtClass? = clazz.extends
        while (next != null) {
            if (next === clazz) {
                artError(Errors.InheritanceLoopError(
                    "Inheritance loop found: class ${clazz.name} extends itself",
                    clazz.nameToken,
                    srcCode
                ))
                break
            }
            next = next.extends
        }

        if (clazz.isAbstract) return

        val funcsToImplement = Datatype.Object(clazz.extends).getAllAbstractFuncs()

        for (toImpl in funcsToImplement) {
            val thisFunc = Datatype.Object(clazz).lookupFuncExact(toImpl.name, toImpl.functionDescriptor, true)
            if (thisFunc == null) {
                artError(Errors.ClassDoesNotImplementAbstractFunctionError(
                    clazz.nameToken,
                    toImpl.name,
                    srcCode
                ))
            }
        }
    }

    override fun visit(print: AstNode.Print) {
        check(print.toPrint)
    }

    override fun visit(block: AstNode.Block) {
        for (s in block.statements) check(s)
    }

    override fun visit(varDec: AstNode.VariableDeclaration) {
        check(varDec.initializer)
    }

    override fun visit(varAssign: AstNode.Assignment) {
        check(varAssign.toAssign)
    }

    override fun visit(loop: AstNode.Loop) {
        check(loop.body)
    }

    override fun visit(ifStmt: AstNode.If) {
        check(ifStmt.ifStmt)
        ifStmt.elseStmt?.let { check(it) }
    }

    override fun visit(whileStmt: AstNode.While) {
        check(whileStmt.body)
    }

    override fun visit(returnStmt: AstNode.Return) {
        returnStmt.toReturn?.let { check(it) }
    }

    override fun visit(varInc: AstNode.VarIncrement) {
        check(varInc.name)
    }

    override fun visit(get: AstNode.Get) {
        get.from?.let { check(it) }
    }

    override fun visit(cont: AstNode.Continue) {
    }

    override fun visit(breac: AstNode.Break) {
    }

    override fun visit(constructorCall: AstNode.ConstructorCall) {
        if (constructorCall.clazz.isAbstract) {
            artError(Errors.CannotInstantiateAbstractClassError(
                constructorCall,
                srcCode
            ))
        }
        for (arg in constructorCall.arguments) check(arg)
    }

    override fun visit(field: AstNode.Field) {
        field as AstNode.FieldDeclaration
        check(field.initializer)
    }

    override fun visit(arr: AstNode.ArrayCreate) {
        for (am in arr.amounts) check(am)
    }

    override fun visit(arr: AstNode.ArrayLiteral) {
        for (el in arr.elements) check(el)
    }

    override fun visit(arr: AstNode.ArrGet) {
        check(arr.from)
        check(arr.arrIndex)
    }

    override fun visit(arr: AstNode.ArrSet) {
        check(arr.from)
        check(arr.arrIndex)
        check(arr.to)
    }

    override fun visit(varInc: AstNode.VarAssignShorthand) {
        varInc.from?.let { check(it) }
        check(varInc.toAdd)
    }

    override fun visit(yieldArrow: AstNode.YieldArrow) {
        check(yieldArrow.expr)
    }

    override fun visit(nul: AstNode.Null) {
    }

    override fun visit(convert: AstNode.TypeConvert) {
        check(convert.toConvert)
    }

    override fun visit(supCall: AstNode.SuperCall) {
        for (arg in supCall.arguments) check(arg)
        if (supCall.definition.isAbstract) {
            artError(Errors.CannotCallAbstractFunctionViaSuperError(
                supCall,
                supCall.definition.name,
                srcCode
            ))
        }
    }

    override fun visit(cast: AstNode.Cast) {
        check(cast.toCast)
    }

    fun check(node: AstNode) = node.accept(this)
}
