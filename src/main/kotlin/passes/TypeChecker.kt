package passes

import Datakind
import Datatype
import ast.*
import errors.ErrorPool
import errors.Errors
import errors.artError
import tokenizer.Token
import tokenizer.TokenType
import javax.xml.crypto.Data

/**
 * checks type-safety, checks that function/fields that are referenced exist
 */
class TypeChecker : AstNodeVisitor<Datatype> {

    /**
     * the locals, their indices and types
     */
    private var vars: MutableMap<Int, Datatype> = mutableMapOf()

    /**
     * the program currently being compiled
     */
    private lateinit var program: AstNode.Program

    /**
     * the current function; null if not in a function
     */
    private var curFunction: AstNode.Function? = null

    /**
     * the current class; null if not in a class
     */
    private var curClass: AstNode.ArtClass? = null

    /**
     * used for swapping nodes. if a visit function returns and swap != null, the check-function will attempt to swap
     * the previous node with the node in swap
     */
    private var swap: AstNode? = null

    /**
     * the source code of the program
     */
    private lateinit var srcCode: String


    override fun visit(binary: AstNode.Binary): Datatype {
        val left = check(binary.left, binary)
        val right = check(binary.right, binary)

        if (left.matches(Datakind.VOID, Datakind.STAT_CLASS)) {
            artError(Errors.ExpectedAnExpressionError(binary.left, srcCode))
        }
        if (right.matches(Datakind.VOID, Datakind.STAT_CLASS)) {
            artError(Errors.ExpectedAnExpressionError(binary.right, srcCode))
        }

        when (binary.operator.tokenType) {

            TokenType.PLUS -> {
                if (left == Datatype.Str() && right == Datatype.Str()) return Datatype.Str()
                if (
                    left.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE) &&
                    left == right
                ) return left
                artError(Errors.IllegalTypesInBinaryOperationError(
                    binary.operator.lexeme, left, right, binary, srcCode
                ))
                return Datatype.ErrorType()
            }

            TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.MOD -> {
                if (
                    left.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE) &&
                    left == right
                ) return left
                artError(Errors.IllegalTypesInBinaryOperationError(
                    binary.operator.lexeme, left, right, binary, srcCode
                ))
                return Datatype.ErrorType()
            }

            TokenType.D_EQ, TokenType.NOT_EQ -> {
                if (left.matches(Datakind.NULL) && right.matches(Datakind.OBJECT)) return Datatype.Bool()
                if (right.matches(Datakind.NULL) && left.matches(Datakind.OBJECT)) return Datatype.Bool()

                if (left.matches(Datakind.OBJECT) && right.matches(Datakind.OBJECT)) return Datatype.Bool()

                if (
                    left.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE) &&
                    left == right
                ) return Datatype.Bool()

                artError(Errors.IllegalTypesInBinaryOperationError(
                    binary.operator.lexeme, left, right, binary, srcCode
                ))
                return Datatype.Bool()
            }

            TokenType.GT, TokenType.GT_EQ, TokenType.LT, TokenType.LT_EQ -> {
                if (
                    left.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE) &&
                    left == right
                ) return Datatype.Bool()

                artError(Errors.IllegalTypesInBinaryOperationError(
                    binary.operator.lexeme, left, right, binary, srcCode
                ))
                return Datatype.Bool()
            }

            TokenType.D_AND, TokenType.D_OR -> {
                if (left.kind != Datakind.BOOLEAN) artError(Errors.ExpectedConditionError(binary.left, left, srcCode))
                if (right.kind != Datakind.BOOLEAN) artError(Errors.ExpectedConditionError(binary.right, right, srcCode))
                return Datatype.Bool()
            }

            else -> throw RuntimeException("unreachable")

        }

    }

    override fun visit(literal: AstNode.Literal): Datatype {
        val type = getDatatypeFromToken(literal.literal.tokenType)
        literal.type = type
        return type
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): Datatype {
        check(exprStmt.exp, exprStmt)
        return Datatype.Void()
    }

    override fun visit(function: AstNode.Function): Datatype {
        function as AstNode.FunctionDeclaration

        curFunction = function

        val newVars = mutableMapOf<Int, Datatype>()
        if (function.hasThis) newVars[0] = Datatype.Object(curClass!!)
        for (i in function.functionDescriptor.args.indices) newVars[i] = function.functionDescriptor.args[i].second
        vars = newVars
        function.clazz = curClass
        function.statements?.let { check(it, function) }
        curFunction = null
        return Datatype.Void()
    }

    override fun visit(program: AstNode.Program): Datatype {
        this.program = program
        srcCode = program.srcCode

        preCalcFields(program.fields, null)
        preCalcFuncs(program.funcs, null)
        preCalcClasses(program.classes)
        for (field in program.fields) if (field !is SyntheticNode) check(field, program)
        for (func in program.funcs) if (func !is SyntheticNode) check(func, program)
        for (c in program.classes) if (c !is SyntheticNode) check(c, program)
        return Datatype.Void()
    }

    /**
     * pre-calculates classes. This is necessary to e.g. determine the types of functions/fields before the main
     * type-checking phase starts
     */
    private fun preCalcClasses(clazzes: List<AstNode.ArtClass>) {
        val names = mutableListOf<String>()

        for (clazz in clazzes) if (clazz !is SyntheticNode) {
            clazz as AstNode.ClassDefinition
            if (clazz.name in names) {
                artError(Errors.DuplicateDefinitionError(clazz.nameToken, "class", srcCode))
                continue
            }
            names.add(clazz.name)
            preCalcFields(clazz.fields + clazz.staticFields, clazz)
            preCalcFuncs(clazz.funcs + clazz.staticFuncs, clazz)
        }
        for (clazz in clazzes) if (clazz !is SyntheticNode) {
            clazz as AstNode.ClassDefinition
            val superClass = clazz.extendsToken?.let {
                lookupTopLevelClass(it.lexeme) ?: run {
                    artError(Errors.UnknownIdentifierError(it, srcCode))
                    null
                }
            } ?: SyntheticAst.objectClass
            clazz._extends = superClass
        }
    }

    /**
     * pre-calculates functions. This is necessary to e.g. determine the types of functions/fields before the main
     * type-checking phase starts
     */
    private fun preCalcFuncs(funcs: List<AstNode.Function>, clazz: AstNode.ArtClass?) {
        for (func in funcs) if (func !is SyntheticNode) {
            func as AstNode.FunctionDeclaration
            func.clazz = clazz
            val args = mutableListOf<Pair<String, Datatype>>()
            if (func.hasThis) args.add(Pair("this", Datatype.Object(clazz!!)))
            for (arg in func.args) args.add(Pair(arg.first.lexeme, typeNodeToDataType(arg.second)))

            val returnType = func.returnType?.let { typeNodeToDataType(it) } ?: Datatype.Void()
            func._functionDescriptor = FunctionDescriptor(args, returnType)
            if (func.name == "main") {
                if (clazz != null) {
                    artError(Errors.InvalidMainFunctionDeclarationError(
                        "the main function can only be defined in the top level",
                        srcCode,
                        listOf(func.nameToken)
                    ))
                }
                if (func.functionDescriptor.args.isNotEmpty()) {
                    if (
                        func.functionDescriptor.args.size != 1 &&
                        func.functionDescriptor.args[0].second != Datatype.ArrayType(Datatype.Str())
                    ) {
                        artError(Errors.InvalidMainFunctionDeclarationError(
                            "the main function must not take any arguments",
                            srcCode,
                            listOf(func.nameToken)
                        ))
                    }
                }
                if (func.functionDescriptor.returnType != Datatype.Void()) {
                    artError(Errors.InvalidMainFunctionDeclarationError(
                        "the main function must return void",
                        srcCode,
                        listOf(func.nameToken)
                    ))
                }
            }
        }
        for (func1 in funcs) for (func2 in funcs) if (func1 !== func2) {
            if (func1.name == func2.name && func1.functionDescriptor.matches(func2.functionDescriptor)) {
                func1 as AstNode.FunctionDeclaration
                artError(Errors.DuplicateDefinitionError(func1.nameToken, "function", srcCode))
            }
        }
    }

    /**
     * pre-calculates fields. This is necessary to e.g. determine the types of functions/fields before the main
     * type-checking phase starts
     */
    private fun preCalcFields(fields: List<AstNode.Field>, clazz: AstNode.ArtClass?) {
        val names = mutableListOf<String>()
        for (field in fields) if (field !is SyntheticNode) {
            field as AstNode.FieldDeclaration
            if (field.name in names) {
                artError(Errors.DuplicateDefinitionError(field.nameToken, "field", srcCode))
            }
            names.add(field.name)
            val explType = typeNodeToDataType(field.explType)
            field._fieldType = explType
            field.clazz = clazz
        }
    }

    override fun visit(print: AstNode.Print): Datatype {
        check(print.toPrint, print)
        if (print.toPrint.type.matches(Datakind.VOID, Datakind.STAT_CLASS)) {
            artError(Errors.ExpectedAnExpressionError(print.toPrint, srcCode))
        }
        return Datatype.Void()
    }

    override fun visit(block: AstNode.Block): Datatype {
        var type: Datatype = Datatype.Void()
        for (s in block.statements) {
            check(s, block)
            if (s is AstNode.YieldArrow) type = s.yieldType
        }
        return type
    }

    override fun visit(variable: AstNode.Variable): Datatype {
        if (variable.index == -1) throw RuntimeException("unreachable")
        val type = vars[variable.index] ?: throw RuntimeException("unreachable")
        variable.type = type
        return type
    }

    override fun visit(varDec: AstNode.VariableDeclaration): Datatype {

        val type = check(varDec.initializer, varDec)
        if (type == Datatype.Void()) {
            artError(Errors.ExpectedAnExpressionError(varDec.initializer, srcCode))
            return Datatype.Void()
        }

        var type2: Datatype? = null
        if (varDec.explType != null) {
            type2 = typeNodeToDataType(varDec.explType!!)
            if (!type.compatibleWith(type2)) {
                artError(Errors.IncompatibleTypesError(varDec, "variable declaration", type, type2, srcCode))
            }
        }

        if (type == Datatype.NullType() && type2 == null) {
            artError(Errors.CantInferTypeError(varDec, srcCode))
            vars[varDec.index] = Datatype.ErrorType()
            varDec.varType = Datatype.ErrorType()
            return Datatype.Void()
        }

        vars[varDec.index] = type2 ?: type
        varDec.varType = type2 ?: type

        return Datatype.Void()
    }

    override fun visit(varAssign: AstNode.Assignment): Datatype {
        val typeToAssign = check(varAssign.toAssign, varAssign)

        if (varAssign.from == null) {

            if (varAssign.index != -1) {
                val varType = vars[varAssign.index] ?: throw RuntimeException("unreachable")

//                if (!varType.compatibleWith(typeToAssign)) {
                if (!typeToAssign.compatibleWith(varType)) {
                    artError(Errors.IncompatibleTypesError(varAssign, "assignment", varType, typeToAssign, srcCode))
                }
                varAssign.type = if (varAssign.isWalrus) varType else Datatype.Void()
                return varAssign.type
            }

            val field = lookupTopLevelField(varAssign.name.lexeme)
            if (field != null) {
                val varType = field.fieldType
                if (!varType.compatibleWith(typeToAssign)) {
                    artError(Errors.IncompatibleTypesError(varAssign, "assignment", varType, typeToAssign, srcCode))
                }
                varAssign.fieldDef = field
                return if (varAssign.isWalrus) field.fieldType else Datatype.Void()
            }
            artError(Errors.UnknownIdentifierError(varAssign.name, srcCode))
            return Datatype.ErrorType()
        }

        val from = check(varAssign.from!!, varAssign)

        when (from.kind) {

            Datakind.STAT_CLASS -> {
                from as Datatype.StatClass

                val field = from.lookupField(varAssign.name.lexeme)
                if (field != null) {
                    if (!field.fieldType.compatibleWith(typeToAssign)) {
                        artError(Errors.IncompatibleTypesError(varAssign, "assignment", field.fieldType, typeToAssign, srcCode))
                    }
                    if (field.isConst) artError(Errors.AssignToConstError(varAssign, field.name, srcCode))
                    if (field.isPrivate && curClass !== field.clazz) {
                        artError(Errors.PrivateMemberAccessError(varAssign, "field", varAssign.name.lexeme, srcCode))
                    }
                    varAssign.fieldDef = field
                    return if (varAssign.isWalrus) field.fieldType else Datatype.Void()
                }
                artError(Errors.UnknownIdentifierError(varAssign.name, srcCode))
                return if (varAssign.isWalrus) Datatype.ErrorType() else Datatype.Void()
            }

            Datakind.OBJECT -> {
                from as Datatype.Object

                val field = from.lookupField(varAssign.name.lexeme)
                if (field != null) {
                    if (!field.fieldType.compatibleWith(typeToAssign)) {
                        artError(Errors.IncompatibleTypesError(varAssign, "assignment", field.fieldType, typeToAssign, srcCode))
                    }
                    if (field.isConst) artError(Errors.AssignToConstError(varAssign, field.name, srcCode))
                    if (field.isPrivate && curClass !== field.clazz) {
                        artError(Errors.PrivateMemberAccessError(varAssign, "field", varAssign.name.lexeme, srcCode))
                    }
                    varAssign.fieldDef = field
                    return if (varAssign.isWalrus) field.fieldType else Datatype.Void()
                }
                artError(Errors.UnknownIdentifierError(varAssign.name, srcCode))
                return if (varAssign.isWalrus) Datatype.ErrorType() else Datatype.Void()
            }

            else -> {
                artError(Errors.OperationNotApplicableError("field-get", from, varAssign, srcCode))
                return if (varAssign.isWalrus) Datatype.ErrorType() else Datatype.Void()
            }

        }
    }

    override fun visit(loop: AstNode.Loop): Datatype {
        check(loop.body, loop)
        return Datatype.Void()
    }

    override fun visit(ifStmt: AstNode.If): Datatype {
        val type = check(ifStmt.condition, ifStmt)
        if (type != Datatype.Bool()) {
            artError(Errors.ExpectedConditionError(ifStmt.condition, type, srcCode))
        }
        val ifPart = check(ifStmt.ifStmt, ifStmt)
        val elsePart = ifStmt.elseStmt?.let { check(it, ifStmt) }
        if (elsePart != null && ifPart.kind != Datakind.VOID) {
            if (ifPart.compatibleWith(elsePart)) {
                ifStmt.ifStmt.type = elsePart
                return elsePart
            }
            if (elsePart.compatibleWith(ifPart)) {
                ifStmt.elseStmt!!.type = ifPart
                return ifPart
            }
        }
        return Datatype.Void()
    }


    override fun visit(unary: AstNode.Unary): Datatype {
        val type = check(unary.on, unary)
        if (unary.operator.tokenType == TokenType.MINUS) {
            if (!type.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE)) {
                artError(Errors.OperationNotApplicableError("unary minus", type, unary, srcCode))
            }
        } else {
            if (type != Datatype.Bool()) {
                artError(Errors.OperationNotApplicableError("unary negate", type, unary, srcCode))
            }
        }
        unary.type = type
        return type
    }

    override fun visit(group: AstNode.Group): Datatype {
        return check(group.grouped, group)
    }

    override fun visit(whileStmt: AstNode.While): Datatype {
        if (check(whileStmt.condition, whileStmt) != Datatype.Bool()) {
            artError(Errors.ExpectedConditionError(whileStmt.condition, whileStmt.condition.type, srcCode))
        }
        check(whileStmt.body, whileStmt)
        return Datatype.Void()
    }

    override fun visit(funcCall: AstNode.FunctionCall): Datatype {
        val thisSig = mutableListOf<Datatype>()
        for (arg in funcCall.arguments) {
            check(arg, funcCall)
            thisSig.add(arg.type)
        }

        if (funcCall.from == null) {
            if (curClass != null) {
                val func = Datatype.StatClass(curClass!!).lookupFunc(funcCall.name.lexeme, thisSig)
                if (func != null) {
                    funcCall.definition = func
                    return func.functionDescriptor.returnType
                }
            }
            val func = lookupTopLevelFunc(funcCall.name.lexeme, thisSig)
            if (func != null) {
                funcCall.definition = func
                return func.functionDescriptor.returnType
            }
            if (curClass != null && funcCall.name.lexeme == curClass!!.name) {
                if (funcCall.arguments.size != 0) {
                    artError(Errors.OperationNotImplementedError(
                        funcCall,
                        "Constructors with parameters are not implemented",
                        srcCode
                    ))
                }
                val toSwap = AstNode.ConstructorCall(curClass!!, funcCall.arguments, funcCall.relevantTokens)
                toSwap.type = Datatype.Object(curClass!!)
                swap = toSwap
                return toSwap.type
            }
            val clazz = lookupTopLevelClass(funcCall.name.lexeme)
            if (clazz != null) {
                if (funcCall.arguments.size != 0) {
                    artError(Errors.OperationNotImplementedError(
                        funcCall,
                        "Constructors with parameters are not implemented",
                        srcCode
                    ))
                }
                val toSwap = AstNode.ConstructorCall(clazz, funcCall.arguments, funcCall.relevantTokens)
                toSwap.type = Datatype.Object(clazz)
                swap = toSwap
                return toSwap.type
            }

            artError(Errors.UnknownIdentifierError(funcCall.name, srcCode))
            return Datatype.ErrorType()
        }

        val from = check(funcCall.from!!, funcCall)

        when (from.kind) {

            Datakind.STAT_CLASS -> {
                from as Datatype.StatClass

                val func = from.lookupFunc(funcCall.name.lexeme, thisSig) ?: run {
                    artError(Errors.UnknownIdentifierError(funcCall.name, srcCode))
                    return Datatype.ErrorType()
                }

                if (func.isPrivate && curClass !== func.clazz) {
                    artError(Errors.PrivateMemberAccessError(funcCall, "function", funcCall.name.lexeme, srcCode))
                }
                funcCall.definition = func
                funcCall.type = func.functionDescriptor.returnType
                return funcCall.type
            }

            Datakind.OBJECT -> {
                from as Datatype.Object

                val func = from.lookupFunc(funcCall.name.lexeme, thisSig) ?: run {
                    artError(Errors.UnknownIdentifierError(funcCall.name, srcCode))
                    return Datatype.ErrorType()
                }

                if (func.isPrivate && curClass !== func.clazz) {
                    artError(Errors.PrivateMemberAccessError(funcCall, "function", funcCall.name.lexeme, srcCode))
                }
                funcCall.definition = func
                funcCall.type = func.functionDescriptor.returnType
                return funcCall.type
            }

            else -> {
                artError(Errors.UnknownIdentifierError(funcCall.name, srcCode))
                return Datatype.ErrorType()
            }

        }
    }

    override fun visit(returnStmt: AstNode.Return): Datatype {
        val type = returnStmt.toReturn?.let { check(it, returnStmt) } ?: Datatype.Void()
        if (curFunction == null) return Datatype.Void()
        if (!type.compatibleWith(curFunction!!.functionDescriptor.returnType)) {
            artError(Errors.IncompatibleTypesError(
                returnStmt,
                "return",
                type,
                curFunction!!.functionDescriptor.returnType,
                srcCode
            ))
        }
        return Datatype.Void()
    }

    override fun visit(varInc: AstNode.VarIncrement): Datatype {
        if (varInc.name.from == null) {
            if (varInc.index != -1) {
                val varType = vars[varInc.index] ?: throw RuntimeException("unreachable")
                if (varType != Datatype.Integer()) {
                    artError(Errors.OperationNotApplicableError(
                        "increment/decrement",
                        varType,
                        varInc,
                        srcCode
                    ))
                }
                return Datatype.Void()
            }
        }

        val toSwap = AstNode.VarAssignShorthand(
            varInc.name.from,
            varInc.name.name,
            Token(TokenType.PLUS_EQ, "+=", null, "", -1, -1),
            AstNode.Literal(
                Token(TokenType.INT, "", varInc.toAdd.toInt(), "", -1, -1),
                listOf()
            ),
            varInc.relevantTokens
        )
        check(toSwap, null)
        swap = toSwap
        return toSwap.type
    }

    override fun visit(clazz: AstNode.ArtClass): Datatype {
        val tmp = curClass
        curClass = clazz
        for (field in clazz.fields) check(field, clazz)
        for (field in clazz.staticFields) check(field, clazz)
        for (func in clazz.staticFuncs) check(func, clazz)
        for (func in clazz.funcs) check(func, clazz)
        curClass = tmp
        return Datatype.Void()
    }

    override fun visit(varInc: AstNode.VarAssignShorthand): Datatype {
        check(varInc.toAdd, varInc)

        if (varInc.index != -1) {

            val local = vars[varInc.index]!!

            if (
                varInc.toAdd.type == Datatype.Str() &&
                local == Datatype.Str() &&
                varInc.operator.tokenType == TokenType.PLUS_EQ
            ) return Datatype.Void()

            if (
                !varInc.toAdd.type.compatibleWith(local) ||
                !varInc.toAdd.type.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE)
            ) {
                artError(Errors.IllegalTypesInBinaryOperationError(
                    varInc.operator.lexeme,
                    varInc.toAdd.type,
                    local,
                    varInc,
                    srcCode
                ))
            }
            return Datatype.Void()
        }

        //temporarily create Get and use its visit function to avoid duplicate logic
        val tmpGet = AstNode.Get(varInc.name, varInc.from, varInc.relevantTokens) //easier this way
        check(tmpGet, null)

        if (
            !(varInc.toAdd.type == Datatype.Str() &&
            tmpGet.type == Datatype.Str() &&
            varInc.operator.tokenType == TokenType.PLUS_EQ)
        ) {
            if (
                !varInc.toAdd.type.compatibleWith(tmpGet.type) ||
                !varInc.toAdd.type.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE)
            ) {
                artError(Errors.IllegalTypesInBinaryOperationError(
                    varInc.operator.lexeme,
                    varInc.toAdd.type,
                    tmpGet.type,
                    varInc,
                    srcCode
                ))
            }
        }

        varInc.fieldDef = tmpGet.fieldDef

        if (varInc.fieldDef == null) return Datatype.Void()

        if (varInc.fieldDef!!.isConst) {
            artError(Errors.AssignToConstError(
                varInc,
                varInc.fieldDef!!.name,
                srcCode
            ))
        }

        return Datatype.Void()
    }

    override fun visit(get: AstNode.Get): Datatype {

        if (get.from == null) {
            val clazz = lookupTopLevelClass(get.name.lexeme)
            if (clazz != null) return Datatype.StatClass(clazz)

            val topLevelField = lookupTopLevelField(get.name.lexeme)
            if (topLevelField != null) {
                get.fieldDef = topLevelField
                return topLevelField.fieldType
            }

            if (curClass != null) {
                val field = Datatype.StatClass(curClass!!).lookupField(get.name.lexeme)
                if (field != null) {
                    get.fieldDef = field
                    return field.fieldType
                }
            }

            artError(Errors.UnknownIdentifierError(get.name, srcCode))
            return Datatype.ErrorType()
        }

        val from = check(get.from!!, get)

        // special case for array.size
        if (from.matches(Datakind.ARRAY) && get.name.lexeme == "size") {
            get.type = Datatype.Integer()
            return Datatype.Integer()
        }

        when (from.kind) {

            Datakind.STAT_CLASS -> {
                from as Datatype.StatClass

                val field = from.lookupField(get.name.lexeme)
                if (field != null) {
                    if (field.isPrivate && curClass != field.clazz) {
                        artError(Errors.PrivateMemberAccessError(get, "field", get.name.lexeme, srcCode))
                    }
                    get.fieldDef = field
                    return field.fieldType
                }
                artError(Errors.UnknownIdentifierError(get.name, srcCode))
                return Datatype.ErrorType()
            }

            Datakind.OBJECT -> {
                from as Datatype.Object

                val field = from.lookupField(get.name.lexeme)
                if (field != null) {
                    if (field.isPrivate && curClass != field.clazz) {
                        artError(Errors.PrivateMemberAccessError(get, "field", get.name.lexeme, srcCode))
                    }
                    get.fieldDef = field
                    return field.fieldType
                }

                artError(Errors.UnknownIdentifierError(get.name, srcCode))
                return Datatype.ErrorType()
            }

            else -> {
                artError(Errors.UnknownIdentifierError(get.name, srcCode))
                return Datatype.ErrorType()
            }
        }
    }

    override fun visit(arr: AstNode.ArrGet): Datatype {
        val from = check(arr.from, arr)

        if (from.matches(Datakind.STAT_CLASS)) {
            val toSwap = AstNode.ArrayCreate(from as Datatype.StatClass, arrayOf(arr.arrIndex), arr.relevantTokens)
            check(toSwap, null)
            swap = toSwap
            return toSwap.type
        }

        if (arr.from is AstNode.ArrayCreate) {
            val toSwap = AstNode.ArrayCreate(
                (arr.from as AstNode.ArrayCreate).of,
                arrayOf(arr.arrIndex, *(arr.from as AstNode.ArrayCreate).amounts),
                arr.relevantTokens
            )
            check(toSwap, null)
            swap = toSwap
            return toSwap.type
        }

        if (!from.matches(Datakind.ARRAY)) {
            println(from)
            artError(Errors.InvalidGetSetReceiverError(arr.from, "get", srcCode))
            return Datatype.ErrorType()
        }
        if (!check(arr.arrIndex, arr).matches(Datakind.INT)) {
            artError(Errors.ArrayIndexTypeError(arr.arrIndex, arr.arrIndex.type, srcCode))
        }
        return (from as Datatype.ArrayType).type
    }

    override fun visit(arr: AstNode.ArrSet): Datatype {
        val from = check(arr.from, arr)

        if (!from.matches(Datakind.ARRAY)) {
            artError(Errors.InvalidGetSetReceiverError(arr, "set", srcCode))
            return Datatype.ErrorType()
        }

        from as Datatype.ArrayType

        if (!check(arr.arrIndex, arr).matches(Datakind.INT)) {
            artError(Errors.ArrayIndexTypeError(arr.arrIndex, arr.arrIndex.type, srcCode))
        }

        val toAssign = check(arr.to, arr)

        if (!toAssign.compatibleWith(from.type)) {
            artError(Errors.IncompatibleTypesError(arr, "set", toAssign, from.type, srcCode))
        }
        return if (arr.isWalrus) toAssign else Datatype.Void()
    }

    override fun visit(cont: AstNode.Continue): Datatype {
        return Datatype.Void()
    }

    override fun visit(breac: AstNode.Break): Datatype {
        return Datatype.Void()
    }

    override fun visit(constructorCall: AstNode.ConstructorCall): Datatype {
        throw RuntimeException("unreachable")
    }

    override fun visit(field: AstNode.Field): Datatype {
        field as AstNode.FieldDeclaration

        vars.clear()
        if (!field.isStatic && !field.isTopLevel) vars[0] = Datatype.Object(curClass!!)

        check(field.initializer, field)
        if (!field.fieldType.compatibleWith(field.initializer.type)) {
            artError(Errors.IncompatibleTypesError(
                field,
                "field declaration",
                field.fieldType,
                field.initializer.type,
                srcCode
            ))
        }
        field.clazz = curClass
        return Datatype.Void()
    }

    override fun visit(arr: AstNode.ArrayCreate): Datatype {
        for (amount in arr.amounts) {
            val amType = check(amount, arr)
            if (amType != Datatype.Integer()) {
                artError(Errors.InvalidTypeInArrayCreateError(arr, amType, srcCode))
            }
        }

        var type: Datatype = if (arr.of is Datatype.StatClass) Datatype.Object(arr.of.clazz) else arr.of
        repeat(arr.amounts.size) { type = Datatype.ArrayType(type) }
        return type
    }

    override fun visit(arr: AstNode.ArrayLiteral): Datatype {
        if (arr.elements.isEmpty()) {
            artError(Errors.EmptyArrayLiteralError(arr, srcCode))
            return Datatype.ArrayType(Datatype.ErrorType())
        }

        var lowestType: Datatype = check(arr.elements[0], arr)

        for (i in 1 until arr.elements.size) {
            val el = arr.elements[i]
            val type = check(el, arr)
            if (type.compatibleWith(lowestType)) continue
            if (lowestType.compatibleWith(type)) {
                lowestType = type
                continue
            }
            artError(Errors.IncompatibleTypesError(el, "array literal", type, lowestType, srcCode))
            lowestType = Datatype.ErrorType()
        }

        //if array literal only contains nulls, set type to Object[]
        if (lowestType.kind == Datakind.NULL) lowestType = Datatype.Object(SyntheticAst.objectClass)

        return Datatype.ArrayType(lowestType)
    }

    override fun visit(yieldArrow: AstNode.YieldArrow): Datatype {
        val type = check(yieldArrow.expr, yieldArrow)
        if (type.kind in arrayOf(Datakind.VOID, Datakind.STAT_CLASS)) {
            artError(Errors.ExpectedAnExpressionError(yieldArrow.expr, srcCode))
            yieldArrow.yieldType = Datatype.ErrorType()
            return Datatype.Void()
        }
        yieldArrow.yieldType = type
        return Datatype.Void()
    }

    override fun visit(nul: AstNode.Null): Datatype {
        return Datatype.NullType()
    }

    override fun visit(convert: AstNode.TypeConvert): Datatype {
        val toConvertType = check(convert.toConvert, convert)

        if (!toConvertType.matches(
                Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG, Datakind.FLOAT, Datakind.DOUBLE
        )) {
            artError(Errors.ExpectedPrimitiveInTypeConversionError(
                convert.toConvert,
                toConvertType,
                srcCode
            ))
        }

        return getDatatypeFromTypeToken(convert.to.tokenType)

    }

    override fun visit(supCall: AstNode.SuperCall): Datatype {
        val thisSig = mutableListOf<Datatype>()
        for (arg in supCall.arguments) {
            check(arg, supCall)
            thisSig.add(arg.type)
        }

        if (curClass == null) {
            artError(Errors.CanOnlyBeUsedInError(
                "super",
                "class",
                supCall,
                srcCode
            ))
            return Datatype.ErrorType()
        }

        val func = Datatype.Object(curClass!!.extends!!).lookupFunc(supCall.name.lexeme, thisSig)
        if (func != null) {
            supCall.definition = func
            return func.functionDescriptor.returnType
        }

        artError(Errors.UnknownIdentifierError(supCall.name, srcCode))
        return Datatype.ErrorType()
    }

    override fun visit(cast: AstNode.Cast): Datatype {
        val castType = typeNodeToDataType(cast.to)
        if (!castType.matches(Datakind.OBJECT)) artError(Errors.CannotCastPrimitiveError(cast, srcCode))
        val leftType = check(cast.toCast, cast)
        if (!leftType.matches(Datakind.OBJECT)) artError(Errors.CannotCastPrimitiveError(cast, srcCode))
        return castType
    }

    /**
     * looks up a function in the top level with the name [name] and that can be called using the arguments in [sig]
     */
    private fun lookupTopLevelFunc(name: String, sig: List<Datatype>): AstNode.Function? {
        for (func in program.funcs) if (func.name == name && func.functionDescriptor.isCompatibleWith(sig)) return func
        return null
    }

    /**
     * looks up a class in the top level with the name [name]
     */
    private fun lookupTopLevelClass(name: String): AstNode.ArtClass? {
        for (clazz in program.classes) if (clazz.name == name) return clazz
        return null
    }

    /**
     * looks up a field in the top level with the name [name]
     */
    private fun lookupTopLevelField(name: String): AstNode.Field? {
        for (field in program.fields) if (field.name == name) return field
        return null
    }

    /**
     * checks the type of [node]. Also handles node-swapping and sets the type property of [node] to the return-type.
     * @param parent the parent of [node]. If [parent] is null and a swap is attempted, a [AstNode.CantSwapException] is
     * thrown
     */
    private fun check(node: AstNode, parent: AstNode?): Datatype {
        val res = node.accept(this)
        node.type = res
        if (swap == null) return res
        if (parent == null) throw AstNode.CantSwapException()
        parent.swap(node, swap!!)
        swap = null
        return res
    }

    /**
     * gets the corresponding Datatype from a type-token (e.g. T_INT, T_BYTE)
     */
    private fun getDatatypeFromTypeToken(type: TokenType): Datatype = when (type) {
        TokenType.T_BYTE -> Datatype.Byte()
        TokenType.T_SHORT -> Datatype.Short()
        TokenType.T_INT -> Datatype.Integer()
        TokenType.T_LONG -> Datatype.Long()
        TokenType.T_FLOAT -> Datatype.Float()
        TokenType.T_DOUBLE -> Datatype.Double()
        else -> throw RuntimeException("unreachable")
    }

    /**
     * converts a type-node to its datatype representation
     */
    private fun typeNodeToDataType(node: AstNode.DatatypeNode): Datatype {
        var type = when (node.kind) {
            Datakind.BOOLEAN -> Datatype.Bool()
            Datakind.BYTE -> Datatype.Byte()
            Datakind.SHORT -> Datatype.Short()
            Datakind.INT -> Datatype.Integer()
            Datakind.LONG -> Datatype.Long()
            Datakind.FLOAT -> Datatype.Float()
            Datakind.DOUBLE -> Datatype.Double()
            Datakind.OBJECT -> {
                node as AstNode.ObjectTypeNode
                var toRet: Datatype? = null

                for (c in program.classes) if (c.name == node.identifier.lexeme) {
                    toRet = Datatype.Object(c)
                }
                toRet ?: throw RuntimeException("unknown Type: ${node.identifier.lexeme}")
            }
            else -> throw RuntimeException("invalid type")
        }
        repeat(node.arrayDims) { type = Datatype.ArrayType(type) }
        return type
    }

    /**
     * returns the primitive type for a token
     */
    private fun getDatatypeFromToken(token: TokenType): Datatype = when (token) {
        TokenType.BYTE -> Datatype.Byte()
        TokenType.SHORT -> Datatype.Short()
        TokenType.INT -> Datatype.Integer()
        TokenType.LONG -> Datatype.Long()
        TokenType.FLOAT -> Datatype.Float()
        TokenType.DOUBLE -> Datatype.Double()
        TokenType.STRING -> Datatype.Str()
        TokenType.BOOLEAN -> Datatype.Bool()
        else -> throw RuntimeException("unreachable")
    }

    /**
     * checks which type is the super-type of the other. null the types are not compatible
     */
    private fun getHigherType(type1: Datatype, type2: Datatype): Datatype? {
        if (type1.compatibleWith(type2)) return type2
        if (type2.compatibleWith(type1)) return type1
        return null
    }
}
