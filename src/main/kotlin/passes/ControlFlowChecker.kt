package passes

import ast.AstNode
import ast.AstNodeVisitor
import Datatype
import ast.SyntheticAst
import ast.SyntheticNode
import errors.Errors
import errors.artError
import passes.ControlFlowChecker.ControlFlowState

/**
 * ensures that break/continue is only used in loops, return is only used in function, that functions
 * with return-type != Void always return
 */
class ControlFlowChecker : AstNodeVisitor<ControlFlowState> {
    
    private lateinit var srcCode: String

    /**
     * the currently (lowest) surrounding loop; null if there is none
     */
    private var surroundingLoop: AstNode? = null

    /**
     * the current function; null if not in a function
     */
    private var curFunction: AstNode.Function? = null

    /**
     * stores the states of all non-static fields in the current class
     */
    private var fields: MutableMap<String, FieldInitState> = mutableMapOf()

    private var inConstructor: Boolean = false

    private var curClass: AstNode.ArtClass? = null

    override fun visit(binary: AstNode.Binary): ControlFlowState {
        val s1 = check(binary.left)
        val s2 = check(binary.right)
        return ControlFlowState(
            s1.alwaysReturns || s2.alwaysReturns,
            s1.alwaysBreaks || s2.alwaysBreaks,
            s1.sometimesReturns || s2.sometimesReturns,
            s1.sometimesBreaks || s2.sometimesBreaks
        )
    }

    override fun visit(literal: AstNode.Literal): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(variable: AstNode.Variable): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(group: AstNode.Group): ControlFlowState {
        return check(group.grouped)
    }

    override fun visit(unary: AstNode.Unary): ControlFlowState {
        return check(unary.on)
    }

    override fun visit(funcCall: AstNode.FunctionCall): ControlFlowState {
        for (arg in funcCall.arguments) check(arg)
        return ControlFlowState()
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): ControlFlowState {
        return check(exprStmt.exp)
    }

    override fun visit(function: AstNode.Function): ControlFlowState {
        function as AstNode.FunctionDeclaration
        curFunction = function
        if (function.statements == null) return ControlFlowState()
        val controlFlowState = check(function.statements!!)
        curFunction = null
        if (function.functionDescriptor.returnType == Datatype.Void()) return ControlFlowState()
        if (!controlFlowState.alwaysReturns) {
           artError(Errors.FunctionDoesNotAlwaysReturnError(function.statements!!.relevantTokens[1], srcCode))
        }
        return ControlFlowState()
    }

    override fun visit(program: AstNode.Program): ControlFlowState {
        srcCode = program.srcCode
        for (field in program.fields) if (field !is SyntheticNode) check(field)
        for (func in program.funcs) if (func !is SyntheticNode) check(func)
        for (c in program.classes) if (c !is SyntheticNode) check(c)
        return ControlFlowState()
    }

    override fun visit(print: AstNode.Print): ControlFlowState {
        return check(print.toPrint)
    }

    override fun visit(block: AstNode.Block): ControlFlowState {
        var alwaysRet = false
        var alwaysBreak = false
        var sometimesBreak = false
        var sometimesRet = false

        for (s in block.statements) {
            val result = check(s)

            alwaysRet = (alwaysRet || result.alwaysReturns) && !sometimesBreak
            alwaysBreak = alwaysBreak|| result.alwaysBreaks
            sometimesRet = sometimesRet || result.sometimesReturns
            sometimesBreak = sometimesBreak || result.sometimesBreaks
        }
        return ControlFlowState(alwaysRet, alwaysBreak, sometimesRet, sometimesBreak)
    }

    override fun visit(varDec: AstNode.VariableDeclaration): ControlFlowState {
        return check(varDec.initializer)
    }

    override fun visit(varAssign: AstNode.Assignment): ControlFlowState {
        if (inConstructor && varAssign.fieldDef != null && !varAssign.fieldDef!!.isStatic) {
            if (varAssign.fieldDef!!.isConst && fields[varAssign.fieldDef!!.name] != FieldInitState.NOT_INITIALISED) {
                artError(Errors.AssignToConstError(
                    varAssign,
                    varAssign.fieldDef!!.name,
                    srcCode
                ))
            }
            fields[varAssign.fieldDef!!.name] = FieldInitState.INITIALISED
        }
        return check(varAssign.toAssign)
    }

    override fun visit(loop: AstNode.Loop): ControlFlowState {
        val tmp = surroundingLoop
        surroundingLoop = loop
        val fieldsBefore = fields.toMutableMap()
        val result = check(loop.body)
        var alwaysReturns = result.alwaysReturns
        if (!result.sometimesBreaks && result.sometimesReturns) alwaysReturns = true
        surroundingLoop = tmp

        val newFields = mutableMapOf<String, FieldInitState>()
        for ((key, value) in fieldsBefore) {
            if (value == FieldInitState.NOT_INITIALISED && fields[key] == FieldInitState.INITIALISED) {
                newFields[key] = FieldInitState.MAYBE_INITIALISED
            } else newFields[key] = value
        }
        fields = newFields

        return ControlFlowState(
            alwaysReturns = alwaysReturns,
            alwaysBreaks = false,
            sometimesReturns = result.sometimesReturns,
            sometimesBreaks = false
        )
    }

    override fun visit(ifStmt: AstNode.If): ControlFlowState {
        val fieldsBefore = fields.toMutableMap()

        val ifBranch = check(ifStmt.ifStmt)
        val fieldsAfterIf = fields.toMutableMap()

        fields = fieldsBefore
        val elseBranch = ifStmt.elseStmt?.let { check(it) } ?: ControlFlowState()

        val newFields = mutableMapOf<String, FieldInitState>()
        if (ifStmt.elseStmt == null) {
            for ((key, value) in fieldsBefore) {
                if (
                    value != FieldInitState.INITIALISED &&
                    fieldsAfterIf[key] == FieldInitState.INITIALISED
                ) newFields[key] = FieldInitState.MAYBE_INITIALISED
                else newFields[key] = value
            }
        } else {
            for ((key, value) in fieldsBefore) {
                if (value == FieldInitState.INITIALISED) {
                    newFields[key] = value
                    continue
                }
                if (fieldsAfterIf[key] == FieldInitState.INITIALISED && fields[key] == FieldInitState.INITIALISED) {
                    newFields[key] = FieldInitState.INITIALISED
                    continue
                }
                if (fieldsAfterIf[key] == FieldInitState.INITIALISED || fields[key] == FieldInitState.INITIALISED) {
                    newFields[key] = FieldInitState.MAYBE_INITIALISED
                    continue
                }
                newFields[key] = value
            }
        }

        fields = newFields

        return ControlFlowState(
            ifBranch.alwaysReturns && elseBranch.alwaysReturns,
            ifBranch.alwaysBreaks && elseBranch.alwaysBreaks,
            ifBranch.sometimesReturns || elseBranch.sometimesReturns,
            ifBranch.sometimesBreaks || elseBranch.sometimesBreaks
        )
    }

    override fun visit(whileStmt: AstNode.While): ControlFlowState {
        val tmp = surroundingLoop
        surroundingLoop = whileStmt
        val fieldsBefore = fields.toMutableMap()
        val result = check(whileStmt.body)
        surroundingLoop = tmp

        val newFields = mutableMapOf<String, FieldInitState>()
        for ((key, value) in fieldsBefore) {
            if (value == FieldInitState.NOT_INITIALISED && fields[key] == FieldInitState.INITIALISED) {
                newFields[key] = FieldInitState.MAYBE_INITIALISED
            } else newFields[key] = value
        }
        fields = newFields

        return ControlFlowState(
            alwaysReturns = false,
            alwaysBreaks = false,
            sometimesReturns = result.sometimesReturns,
            sometimesBreaks = false
        )
    }

    override fun visit(returnStmt: AstNode.Return): ControlFlowState {
//        if (curFunction == null && !inConstructor) { //TODO: returning in constructors (issues with field initializations)
        if (curFunction == null) {
            artError(Errors.CanOnlyBeUsedInError(
                "return",
                "function",
                returnStmt,
                srcCode
            ))
        }
        return ControlFlowState(alwaysReturns = true, sometimesReturns = true)
    }

    override fun visit(varInc: AstNode.VarIncrement): ControlFlowState {
        return check(varInc.name)
    }

    override fun visit(clazz: AstNode.ArtClass): ControlFlowState {

        curClass = clazz

        for (field in clazz.fields)         if (field !is SyntheticNode) check(field)
        for (field in clazz.staticFields)   if (field !is SyntheticNode) check(field)
        for (func in clazz.funcs)           if (func !is SyntheticNode)  check(func)
        for (func in clazz.staticFuncs)     if (func !is SyntheticNode)  check(func)
        for (con in clazz.constructors)     if (con !is SyntheticNode)   check(con)


        if (clazz.constructors.size == 1 && clazz.constructors[0] is SyntheticAst.DefaultConstructor) {
            for (field in clazz.fields) if (field is AstNode.FieldDeclaration && field.initializer == null) {
                artError(Errors.FieldIsNotInitialisedError(
                    Either.Right(field),
                    field.name,
                    srcCode
                ))
            }
        }

        curClass = null

        return ControlFlowState()
    }

    override fun visit(get: AstNode.Get): ControlFlowState {

        if (inConstructor && get.fieldDef != null && curClass != null && get.fieldDef in curClass!!.fields) {
            if (fields[get.fieldDef!!.name] != FieldInitState.INITIALISED) {
                artError(Errors.FieldAccessBeforeInitialization(
                    get,
                    get.fieldDef!!.name,
                    srcCode
                ))
            }
        }

        return get.from?.let { check(it) } ?: ControlFlowState()
    }

    override fun visit(cont: AstNode.Continue): ControlFlowState {
        if (surroundingLoop == null) {
            artError(Errors.CanOnlyBeUsedInError(
                "continue",
                "loop",
                cont,
                srcCode
            ))
        }
        return ControlFlowState()
    }

    override fun visit(breac: AstNode.Break): ControlFlowState {
        if (surroundingLoop == null) {
            artError(Errors.CanOnlyBeUsedInError(
                "break",
                "loop",
                breac,
                srcCode
            ))
        }
        return ControlFlowState(
            alwaysReturns = false,
            alwaysBreaks = true,
            sometimesReturns = false,
            sometimesBreaks = true
        )
    }

    override fun visit(constructorCall: AstNode.ConstructorCall): ControlFlowState {
        for (arg in constructorCall.arguments) check(arg) // TODO: may not work in the future: expressions that return, etc.
        return ControlFlowState()
    }

    override fun visit(field: AstNode.Field): ControlFlowState {
        field as AstNode.FieldDeclaration
        if (field.initializer != null) {
            if (!field.isStatic) fields[field.name] = FieldInitState.INITIALISED
            return check(field.initializer!!)
        } else {
            if (field.isStatic) {
                artError(Errors.FieldIsNotInitialisedError(
                    Either.Right(field),
                    field.name,
                    srcCode
                ))
            }
            return ControlFlowState()
        }
    }

    override fun visit(arr: AstNode.ArrGet): ControlFlowState {
        val s1 = check(arr.from)
        val s2 = check(arr.arrIndex)
        return ControlFlowState(
            s1.alwaysReturns || s2.alwaysReturns,
            s1.alwaysBreaks || s2.alwaysBreaks,
            s1.sometimesReturns || s2.sometimesReturns,
            s1.sometimesBreaks || s2.sometimesBreaks
        )
    }

    override fun visit(arr: AstNode.ArrSet): ControlFlowState {
        val s1 = check(arr.from)
        val s2 = check(arr.arrIndex)
        val s3 = check(arr.to)
        return ControlFlowState(
            s1.alwaysReturns || s2.alwaysReturns || s3.alwaysReturns,
            s1.alwaysBreaks || s2.alwaysBreaks || s3.alwaysBreaks,
            s1.sometimesReturns || s2.sometimesReturns || s3.sometimesReturns,
            s1.sometimesBreaks || s2.sometimesBreaks || s3.sometimesBreaks
        )
    }

    override fun visit(arr: AstNode.ArrayCreate): ControlFlowState {
        var combined = ControlFlowState()
        for (el in arr.amounts) {
            val s = check(el)
            combined = ControlFlowState(
                combined.alwaysReturns || s.alwaysReturns,
                combined.alwaysBreaks || s.alwaysBreaks,
                combined.sometimesReturns || s.sometimesReturns,
                combined.sometimesBreaks || s.sometimesBreaks
            )
        }
        return combined
    }

    override fun visit(arr: AstNode.ArrayLiteral): ControlFlowState {
        var alwaysRet = false
        var alwaysBreak = false
        var sometimesBreak = false
        var sometimesRet = false
        for (el in arr.elements) {
            val result = check(el)
            if (result.alwaysReturns) alwaysRet = true
            if (result.alwaysBreaks) alwaysBreak = true
            if (result.sometimesReturns) sometimesRet = true
            if (result.sometimesBreaks) sometimesBreak = true
        }
        return ControlFlowState(alwaysRet, alwaysBreak, sometimesRet, sometimesBreak)
    }

    override fun visit(varInc: AstNode.VarAssignShorthand): ControlFlowState {
        return check(varInc.toAdd)
    }

    override fun visit(yieldArrow: AstNode.YieldArrow): ControlFlowState {
        return check(yieldArrow.expr)
    }

    override fun visit(nul: AstNode.Null): ControlFlowState {
        return ControlFlowState()
    }

    override fun visit(convert: AstNode.TypeConvert): ControlFlowState {
        return check(convert.toConvert)
    }

    override fun visit(supCall: AstNode.SuperCall): ControlFlowState {
        for (arg in supCall.arguments) check(arg)
        return ControlFlowState()
    }

    override fun visit(cast: AstNode.Cast): ControlFlowState {
        return check(cast.toCast)
    }

    override fun visit(instanceOf: AstNode.InstanceOf): ControlFlowState {
        return check(instanceOf.toCheck)
    }

    override fun visit(constructor: AstNode.Constructor): ControlFlowState {
        constructor as AstNode.ConstructorDeclaration

        fields.clear()

        val fieldAssignArgs = constructor.fieldAssignArgFieldDefs
        for (field in curClass!!.fields) if (field !is SyntheticNode) {
            field as AstNode.FieldDeclaration

            var initialisedByArg = false
            for ((name, _) in fieldAssignArgs) if (name == field.name) initialisedByArg = true
            val initialisedInDef = field.initializer != null

            if (initialisedByArg && initialisedInDef) artError(Errors.DuplicateFieldInitialisationError(
                constructor,
                field.name,
                srcCode
            ))

            fields[field.name] =    if (initialisedInDef || initialisedByArg) FieldInitState.INITIALISED
                                    else FieldInitState.NOT_INITIALISED
        }

        inConstructor = true

        constructor.superCallArgs?.forEach { check(it) }
        constructor.body?.let { check(it) }

        inConstructor = false

        for (fieldEntry in fields) if (fieldEntry.value != FieldInitState.INITIALISED) {
            artError(Errors.FieldIsNotInitialisedError(
                Either.Left(constructor.relevantTokens[0]),
                fieldEntry.key,
                srcCode
            ))
        }
        return ControlFlowState()
    }

    /**
     * checks the [ControlFlowState] of [node]
     */
    private fun check(node: AstNode): ControlFlowState = node.accept(this)

    /**
     * contains details about whether a specific node returns/breaks
     * @param alwaysReturns true if the node guaranties that it will always return from the enclosing function
     * @param alwaysBreaks true if the node guaranties that it will always break from the enclosing loop
     * @param sometimesReturns true if the node will sometimes return from the enclosing function
     * @param sometimesBreaks true if the node will sometimes break from the enclosing loop
     */
    data class ControlFlowState(
        val alwaysReturns: Boolean = false,
        val alwaysBreaks: Boolean = false,
        val sometimesReturns: Boolean = false,
        val sometimesBreaks: Boolean = false
    )

    private enum class FieldInitState {
        NOT_INITIALISED, INITIALISED, MAYBE_INITIALISED
    }

}
