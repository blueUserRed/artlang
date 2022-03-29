package passes

import ast.AstNode
import ast.AstNodeVisitor
import ast.AstPrinter
import ast.FunctionDescriptor
import errors.Errors
import errors.artError
import tokenizer.TokenType
import passes.TypeChecker.Datatype
import tokenizer.Token
import kotlin.RuntimeException

class TypeChecker : AstNodeVisitor<Datatype> {

    private var vars: MutableMap<Int, Datatype> = mutableMapOf()
    private lateinit var curProgram: AstNode.Program
    private lateinit var curFunction: AstNode.Function
    private var curClass: AstNode.ArtClass? = null

    private var swap: AstNode? = null

    var srcCode: String = ""

    override fun visit(binary: AstNode.Binary): Datatype {
        val type1 = check(binary.left, binary)
        val type2 = check(binary.right, binary)
        val resultType: Datatype

        when (binary.operator.tokenType) {
            TokenType.PLUS -> {
                if (type1 == type2 && type1 == Datatype.Str()) {
                    resultType = type1
                } else {
                    if (!type1.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG,
                            Datakind.FLOAT, Datakind.DOUBLE) ||
                            !(type1.compatibleWith(type2) || type2.compatibleWith(type1))) {
                        artError(Errors.IllegalTypesInBinaryOperationError(
                            binary.operator.lexeme, type1, type2, binary, srcCode
                        ))
                        resultType = Datatype.ErrorType()
                    } else {
                        resultType = getHigherType(type1, type2) ?: throw RuntimeException("unreachable")
                    }
                }
            }
            TokenType.MINUS -> {
                if (!type1.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG,
                    Datakind.FLOAT, Datakind.DOUBLE) || !(type1.compatibleWith(type2) || type2.compatibleWith(type1))) {
                    artError(Errors.IllegalTypesInBinaryOperationError(
                        binary.operator.lexeme, type1, type2, binary, srcCode
                    ))
                    resultType = Datatype.ErrorType()
                } else {
                    resultType = getHigherType(type1, type2) ?: throw RuntimeException("unreachable")
                }
            }
            TokenType.STAR, TokenType.SLASH -> {
                if (!type1.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG,
                    Datakind.FLOAT, Datakind.DOUBLE) || !(type1.compatibleWith(type2) || type2.compatibleWith(type1))) {
                    artError(Errors.IllegalTypesInBinaryOperationError(
                        binary.operator.lexeme, type1, type2, binary, srcCode
                    ))
                    resultType = Datatype.ErrorType()
                } else {
                    resultType = getHigherType(type1, type2) ?: throw RuntimeException("unreachable")
                }
            }
            TokenType.MOD -> {
                if (!type1.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG,
                        Datakind.FLOAT, Datakind.DOUBLE) || !(type1.compatibleWith(type2) || type2.compatibleWith(type1))) {
                    artError(Errors.IllegalTypesInBinaryOperationError(
                        binary.operator.lexeme, type1, type2, binary, srcCode
                    ))
                    resultType = Datatype.ErrorType()
                } else {
                    resultType = getHigherType(type1, type2) ?: throw RuntimeException("unreachable")
                }
            }
            TokenType.D_EQ, TokenType.NOT_EQ -> {
                if (!type1.matches(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG,
                        Datakind.FLOAT, Datakind.DOUBLE) || type1 != type2) {
                    artError(Errors.IllegalTypesInBinaryOperationError(
                        binary.operator.lexeme, type1, type2, binary, srcCode
                    ))
                    resultType = Datatype.ErrorType()
                } else {
                    resultType = Datatype.Bool()
                }
            }
            TokenType.D_AND, TokenType.D_OR -> {
                if (type1 != type2 || !type1.matches(Datakind.BOOLEAN)) {
                    artError(Errors.IllegalTypesInBinaryOperationError(
                        binary.operator.lexeme, type1, type2, binary, srcCode
                    ))
                    resultType = Datatype.ErrorType()
                } else resultType = Datatype.Bool()
            }
            TokenType.GT, TokenType.GT_EQ, TokenType.LT, TokenType.LT_EQ -> {
                if (!type1.matches(
                        Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.LONG,
                        Datakind.FLOAT, Datakind.DOUBLE
                    ) || !type1.compatibleWith(type2)
                ) {
                    artError(
                        Errors.IllegalTypesInBinaryOperationError(
                            binary.operator.lexeme, type1, type2, binary, srcCode
                        )
                    )
                    resultType = Datatype.ErrorType()
                } else {
                    resultType = Datatype.Bool()
                }
            }
            else -> throw RuntimeException("unreachable")
        }

        binary.type = resultType
        return resultType
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
        curFunction = function

        val newVars = mutableMapOf<Int, Datatype>()
        if (function.hasThis) newVars[0] = Datatype.Object(curClass!!.name.lexeme, curClass!!)
        for (i in function.functionDescriptor.args.indices) newVars[i] = function.functionDescriptor.args[i].second
        vars = newVars
        function.clazz = curClass
        function.statements.accept(this)
        return Datatype.Void()
    }

    override fun visit(program: AstNode.Program): Datatype {
        curProgram = program
        preCalcFields(program.fields, null)
        preCalcFuncs(program.funcs, null)
        preCalcClasses(program.classes)
        for (field in program.fields) check(field, program)
        for (func in program.funcs) check(func, program)
        for (c in program.classes) check(c, program)
        return Datatype.Void()
    }

    private fun preCalcClasses(clazzes: List<AstNode.ArtClass>) {
        val names = mutableListOf<String>()
        for (clazz in clazzes) {
            if (clazz.name.lexeme in names) throw RuntimeException("duplicate definition of class ${clazz.name.lexeme}")
            names.add(clazz.name.lexeme)
            preCalcFields(clazz.fields, clazz)
            preCalcFields(clazz.staticFields, clazz)
            preCalcFuncs(clazz.staticFuncs, clazz)
            preCalcFuncs(clazz.funcs, clazz)
        }
    }

    private fun preCalcFuncs(funcs: List<AstNode.Function>, clazz: AstNode.ArtClass?) {
        for (func in funcs) {
            curFunction = func
            func.clazz = clazz
            val args = mutableListOf<Pair<String, Datatype>>()
            if (func.hasThis) args.add(Pair("this", Datatype.Object(clazz!!.name.lexeme, clazz)))
            for (arg in func.args) args.add(Pair(arg.first.lexeme, typeNodeToDataType(arg.second)))

            val returnType = func.returnType?.let { typeNodeToDataType(it) } ?: Datatype.Void()
            func.functionDescriptor = FunctionDescriptor(args, returnType)
            if (func.name.lexeme == "main") {
                if (clazz != null) throw RuntimeException("main function can only be in top level")
                if (func.functionDescriptor.args.isNotEmpty()) {
                    throw RuntimeException("main function cant take any arguments")
                }
                if (func.functionDescriptor.returnType != Datatype.Void()) {
                    throw RuntimeException("main function can only return void")
                }
            }
        }
        for (func1 in funcs) for (func2 in funcs) if (func1 !== func2) {
            if (func1.name.lexeme == func2.name.lexeme && func1.functionDescriptor.matches(func2.functionDescriptor)) {
                throw RuntimeException("Duplicate definiation of function ${func1.name.lexeme}" +
                        func1.functionDescriptor.getDescriptorString()
                )
            }
        }
    }

    private fun preCalcFields(fields: List<AstNode.FieldDeclaration>, clazz: AstNode.ArtClass?) {
        val names = mutableListOf<String>()
        for (field in fields) {
            if (field.name.lexeme in names) throw RuntimeException("duplicate definition of field ${field.name.lexeme}")
            names.add(field.name.lexeme)
            val explType = typeNodeToDataType(field.explType)
            field.fieldType = explType
            field.clazz = clazz
        }
    }

    override fun visit(print: AstNode.Print): Datatype {
        check(print.toPrint, print)
        if (print.toPrint.type.matches(Datakind.VOID, Datakind.STAT_CLASS)) {
            throw RuntimeException("invalid type in print: ${print.toPrint.type}")
        }
        return Datatype.Void()
    }

    override fun visit(block: AstNode.Block): Datatype {
        for (s in block.statements) check(s, block)
        return Datatype.Void()
    }

    override fun visit(variable: AstNode.Variable): Datatype {
        if (variable.index == -1) throw RuntimeException("Unknown Variable ${variable.name.lexeme}")
        val type = vars[variable.index] ?: throw RuntimeException("unreachable")

        if (variable.arrIndex != null) {
            if (check(variable.arrIndex!!, variable) != Datatype.Integer()) {
                throw RuntimeException("expected integer in get")
            }
            if (!type.matches(Datakind.ARRAY)) {
                throw RuntimeException("can only get from arrays")
            }
            variable.type = (type as Datatype.ArrayType).type
            return variable.type
        }

        variable.type = type
        return type
    }

    override fun visit(varDec: AstNode.VariableDeclaration): Datatype {
        val type = check(varDec.initializer, varDec)
        if (type == Datatype.Void()) throw RuntimeException("Expected Expression in var initializer")
        if (varDec.explType != null) {
            val type2 = typeNodeToDataType(varDec.explType!!)
            if (type2 != type) throw RuntimeException("Incompatible types in declaration: $type2 and $type")
        }
        vars[varDec.index] = type
        varDec.varType = type

        return Datatype.Void()
    }

    override fun visit(varAssign: AstNode.Assignment): Datatype {
        varAssign.arrIndex?.let { check(it, varAssign) }
        val typeToAssign = check(varAssign.toAssign, varAssign)
        if (varAssign.name.from == null) {
            if (varAssign.index != -1) {
                val varType = vars[varAssign.index] ?: throw RuntimeException("unreachable")
                varAssign.name.type = varType

                if (varAssign.arrIndex != null) {
                    if (varAssign.arrIndex!!.type != Datatype.Integer()) {
                        throw RuntimeException("array can only be indexed by integer")
                    }
                    if (!varType.matches(Datakind.ARRAY)) {
                        throw RuntimeException("can only get from array")
                    }
                    varType as Datatype.ArrayType
                    if (varType.type != typeToAssign) {
                        throw RuntimeException("incompatible types in array set: ${varType.type} and $typeToAssign")
                    }
                    varAssign.type = if (varAssign.isWalrus) varType.type else Datatype.Void()
                    return varAssign.type
                }

                if (typeToAssign != varType) throw RuntimeException("tried to assign $typeToAssign to $varType")
                varAssign.type = if (varAssign.isWalrus) varType else Datatype.Void()
                return varAssign.type
            }
            for (field in curProgram.fields) if (field.name.lexeme == varAssign.name.name.lexeme) {
                varAssign.name.fieldDef = field
                varAssign.name.type = field.fieldType

                if (varAssign.arrIndex != null) {
                    if (varAssign.arrIndex!!.type != Datatype.Integer()) {
                        throw RuntimeException("array can only be indexed by integer")
                    }
                    if (!field.fieldType.matches(Datakind.ARRAY)) {
                        throw RuntimeException("can only get from array")
                    }
                    if ((field.fieldType as Datatype.ArrayType).type != typeToAssign) {
                        throw RuntimeException("incompatible types in array set:" +
                                "${(field.fieldType as Datatype.ArrayType).type} and $typeToAssign")
                    }
                    varAssign.type = if (varAssign.isWalrus) (field.fieldType as Datatype.ArrayType).type
                        else Datatype.Void()
                    return varAssign.type
                }

                varAssign.type =  if (varAssign.isWalrus) field.fieldType else Datatype.Void()
                return varAssign.type
            }
            throw RuntimeException("cant find variable ${varAssign.name.name.lexeme}")
        }
        val from = check(varAssign.name.from!!, varAssign.name)

        when (from.kind) {

            Datakind.STAT_CLASS -> {
                from as Datatype.StatClass
                for (field in from.clazz.staticFields) if (field.name.lexeme == varAssign.name.name.lexeme) {
                    if (field.isConst) throw RuntimeException("tried to assign to constant field ${field.name.lexeme}")
                    if (field.isPrivate && curClass !== field.clazz) {
                        throw RuntimeException("field ${field.name.lexeme} is private")
                    }
                    varAssign.name.fieldDef = field
                    varAssign.name.type = field.fieldType

                    if (varAssign.arrIndex != null) {
                        if (varAssign.arrIndex!!.type != Datatype.Integer()) {
                            throw RuntimeException("array can only be indexed by integer")
                        }
                        if (!field.fieldType.matches(Datakind.ARRAY)) {
                            throw RuntimeException("can only get from array")
                        }
                        if ((field.fieldType as Datatype.ArrayType).type != typeToAssign) {
                            throw RuntimeException("incompatible types in array set:" +
                                    "${(field.fieldType as Datatype.ArrayType).type} and $typeToAssign")
                        }
                        varAssign.type = if (varAssign.isWalrus) (field.fieldType as Datatype.ArrayType).type
                        else Datatype.Void()
                        return varAssign.type
                    }

                    varAssign.type = if (varAssign.isWalrus) field.fieldType else Datatype.Void()
                    return varAssign.type
                }
            }

            Datakind.OBJECT -> {
                from as Datatype.Object
                for (field in from.clazz.fields) if (field.name.lexeme == varAssign.name.name.lexeme) {
                    if (field.isConst) throw RuntimeException("tried to assign to constant field ${field.name.lexeme}")
                    if (field.isPrivate && curClass !== field.clazz) {
                        throw RuntimeException("field ${field.name.lexeme} is private")
                    }
                    varAssign.name.fieldDef = field
                    varAssign.name.type = field.fieldType

                    if (varAssign.arrIndex != null) {
                        if (varAssign.arrIndex!!.type != Datatype.Integer()) {
                            throw RuntimeException("array can only be indexed by integer")
                        }
                        if (!field.fieldType.matches(Datakind.ARRAY)) {
                            throw RuntimeException("can only get from array")
                        }
                        if ((field.fieldType as Datatype.ArrayType).type != typeToAssign) {
                            throw RuntimeException("incompatible types in array set:" +
                                    "${(field.fieldType as Datatype.ArrayType).type} and $typeToAssign")
                        }
                        varAssign.type = if (varAssign.isWalrus) (field.fieldType as Datatype.ArrayType).type
                        else Datatype.Void()
                        return varAssign.type
                    }

                    varAssign.type = if (varAssign.isWalrus) field.fieldType else Datatype.Void()
                    return varAssign.type
                }
            }
            else -> TODO("getting is only implemented for classes and objects")
        }
        throw RuntimeException("cant get ${varAssign.name.name.lexeme} from ${varAssign.name.from!!.accept(AstPrinter())}")
    }

    override fun visit(loop: AstNode.Loop): Datatype {
        check(loop.body, loop)
        return Datatype.Void()
    }

    override fun visit(ifStmt: AstNode.If): Datatype {
        val type = check(ifStmt.condition, ifStmt)
        if (type != Datatype.Bool()) throw RuntimeException("Expected Boolean value")
        ifStmt.ifStmt.accept(this)
        ifStmt.elseStmt?.accept(this)
        return Datatype.Void()
    }

    override fun visit(unary: AstNode.Unary): Datatype {
        val type = check(unary.on, unary)
        if (unary.operator.tokenType == TokenType.MINUS) {
            if (type != Datatype.Integer()) throw RuntimeException("cant negate $type")
        } else {
            if (type != Datatype.Bool()) throw RuntimeException("cant invert $type")
        }
        unary.type = type
        return type
    }

    override fun visit(group: AstNode.Group): Datatype {
        return check(group.grouped, group)
    }

    override fun visit(whileStmt: AstNode.While): Datatype {
        if (check(whileStmt.condition, whileStmt) != Datatype.Bool()) throw RuntimeException("Expected Boolean value")
        whileStmt.body.accept(this)
        return Datatype.Void()
    }

    override fun visit(funcCall: AstNode.FunctionCall): Datatype {
        val thisSig = mutableListOf<Datatype>()
        for (arg in funcCall.arguments) {
            check(arg, funcCall)
            thisSig.add(arg.type)
        }

        if (funcCall.func.from == null) {
            if (curClass != null) for (func in curClass!!.staticFuncs) {
                if (func.name.lexeme != funcCall.func.name.lexeme) continue
                if (!doFuncSigsMatch(thisSig, func.functionDescriptor.args)) continue
                funcCall.definition = func
                funcCall.type = func.functionDescriptor.returnType
                return funcCall.type
            }
            for (func in curProgram.funcs) if (func.name.lexeme == funcCall.func.name.lexeme) {
                if (!doFuncSigsMatch(thisSig, func.functionDescriptor.args)) continue
                funcCall.definition = func
                funcCall.type = func.functionDescriptor.returnType
                return funcCall.type
            }
            if (curClass != null && funcCall.func.name.lexeme == curClass!!.name.lexeme) {
                if (funcCall.arguments.size != 0) TODO("constructor calls with arguments are not yet implemented")
                val toSwap = AstNode.ConstructorCall(curClass!!, funcCall.arguments, funcCall.func)
                toSwap.type = Datatype.Object(curClass!!.name.lexeme, curClass!!)
                swap = toSwap
                return toSwap.type
            }
            for (clazz in curProgram.classes) if (clazz.name.lexeme == funcCall.func.name.lexeme) {
                if (funcCall.arguments.size != 0) TODO("constructor calls with arguments are not yet implemented")
                val toSwap = AstNode.ConstructorCall(clazz, funcCall.arguments, funcCall.func)
                toSwap.type = Datatype.Object(clazz.name.lexeme, clazz)
                swap = toSwap
                return toSwap.type
            }
            throw RuntimeException("couldn't find function ${funcCall.getFullName()}")
        }

        val from = check(funcCall.func.from!!, funcCall.func)

        when (from.kind) {

            Datakind.STAT_CLASS -> {
                from as Datatype.StatClass
                for (func in from.clazz.staticFuncs) if (func.name.lexeme == funcCall.func.name.lexeme) {
                    if (!doFuncSigsMatch(thisSig, func.functionDescriptor.args)) continue
                    if (func.isPrivate && curClass !== func.clazz) {
                        throw RuntimeException("cant call private function ${func.name.lexeme}")
                    }
                    funcCall.definition = func
                    funcCall.type = func.functionDescriptor.returnType
                    return funcCall.type
                }
                throw RuntimeException("couldn't find function ${funcCall.getFullName()}")
            }

            Datakind.OBJECT -> {
                from as Datatype.Object
                for (func in from.clazz.funcs) if (func.name.lexeme == funcCall.func.name.lexeme) {
                    if (!doFuncSigsMatch(thisSig, func.functionDescriptor.args)) continue
                    if (func.isPrivate && curClass !== func.clazz) {
                        throw RuntimeException("cant call private function ${func.name.lexeme}")
                    }
                    funcCall.definition = func
                    funcCall.type = func.functionDescriptor.returnType
                    return funcCall.type
                }
                throw RuntimeException("couldn't find function ${funcCall.getFullName()}")
            }

            else -> throw RuntimeException("cant lookup function on ${funcCall.getFullName()}")

        }
    }

    override fun visit(returnStmt: AstNode.Return): Datatype {
        val type = returnStmt.toReturn?.let { check(it, returnStmt) } ?: Datatype.Void()
        if (curFunction.functionDescriptor.returnType != type) {
            throw RuntimeException("incompatible return types: $type and ${curFunction.functionDescriptor.returnType}")
        }
        return type
    }

    override fun visit(varInc: AstNode.VarIncrement): Datatype {
        if (varInc.name.from == null) {
            if (varInc.index != -1) {
                val varType = vars[varInc.index] ?: throw RuntimeException("unreachable")
                if (varType != Datatype.Integer()) TODO("incrementing types other than int is not implemented")
                return Datatype.Void()
            }
        }

        val toSwap = AstNode.Assignment(
            varInc.name,
            AstNode.Binary(
                varInc.name,
                Token(TokenType.PLUS, "+=", null, varInc.name.name.file,
                    varInc.name.name.pos, varInc.name.name.line),
                AstNode.Literal(
                    Token(TokenType.INT, "+=", varInc.toAdd.toInt(), varInc.name.name.file,
                        varInc.name.name.pos, varInc.name.name.line)
                )
            ),
            false
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

    override fun visit(get: AstNode.Get): Datatype {

        val arrIndexType = get.arrIndex?.let { check(it, get) }
        if (arrIndexType != null && arrIndexType != Datatype.Integer()) {
            throw RuntimeException("expected Integer")
        }

        if (get.from == null) {
            for (c in curProgram.classes) if (c.name.lexeme == get.name.lexeme) {

                if (arrIndexType != null) {
                    val toSwap = AstNode.ArrayCreate(AstNode.ObjectTypeNode(get.name), get.arrIndex!!)
                    toSwap.type = Datatype.ArrayType(typeNodeToDataType(toSwap.typeNode))
                    swap = toSwap
                    return toSwap.type
                }

                get.type = Datatype.StatClass(c)
                return get.type
            }
            for (field in curProgram.fields) if (field.name.lexeme == get.name.lexeme) {
                get.fieldDef = field

                if (arrIndexType != null) {
                    if (!field.fieldType.matches(Datakind.ARRAY)) {
                        throw RuntimeException("can only use get-expression on array")
                    }
                    get.type = (field.fieldType as Datatype.ArrayType).type
                    return get.type
                }

                get.type = field.fieldType
                return get.type
            }
            if (curClass != null) for (field in curClass!!.staticFields) {
                if (field.name.lexeme != get.name.lexeme) continue
                get.fieldDef = field

                if (arrIndexType != null) {
                    if (!field.fieldType.matches(Datakind.ARRAY)) {
                        throw RuntimeException("can only use get-expression on array")
                    }
                    get.type = (field.fieldType as Datatype.ArrayType).type
                    return get.type
                }

                get.type = field.fieldType
                return get.type
            }
            throw IllegalArgumentException("couldn't find variable ${get.name.lexeme}")
        }

        val from = check(get.from!!, get)

        if (from.matches(Datakind.ARRAY) && get.name.lexeme == "size") {
            get.type = Datatype.Integer()
            return Datatype.Integer()
        }

        when (from.kind) {

            Datakind.STAT_CLASS -> {
                from as Datatype.StatClass
                for (field in from.clazz.staticFields) if (field.name.lexeme == get.name.lexeme) {
                    if (field.isPrivate && curClass !== field.clazz) {
                        throw RuntimeException("static field ${field.name.lexeme} is private")
                    }
                    get.fieldDef = field

                    if (arrIndexType != null) {
                        if (!field.fieldType.matches(Datakind.ARRAY)) {
                            throw RuntimeException("can only use get-expression on array")
                        }
                        get.type = (field.fieldType as Datatype.ArrayType).type
                        return get.type
                    }

                    get.type = field.fieldType
                    return get.type
                }
                throw RuntimeException("cant get ${get.name.lexeme} from class ${from.clazz.name.lexeme}")
            }

            Datakind.OBJECT -> {
                from as Datatype.Object
                for (field in from.clazz.fields) if (field.name.lexeme == get.name.lexeme) {
                    if (field.isPrivate && curClass !== field.clazz) {
                        throw RuntimeException("field ${field.name.lexeme} is private")
                    }
                    get.fieldDef = field

                    if (arrIndexType != null) {
                        if (!field.fieldType.matches(Datakind.ARRAY)) {
                            throw RuntimeException("can only use get-expression on array")
                        }
                        get.type = (field.fieldType as Datatype.ArrayType).type
                        return get.type
                    }

                    get.type = field.fieldType
                    return get.type
                }
                throw RuntimeException("cant get ${get.name.lexeme} from ${from.clazz.name.lexeme}")
            }

            else -> throw RuntimeException("cant get any field from $from")
        }
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

    override fun visit(field: AstNode.FieldDeclaration): Datatype {
        check(field.initializer, field)
        if (field.fieldType != field.initializer.type) {
            throw RuntimeException("incompatible types in field declaration ${field.fieldType} and ${field.initializer.type}")
        }
        field.clazz = curClass
        return Datatype.Void()
    }

    override fun visit(arr: AstNode.ArrayCreate): Datatype {
        val amType = check(arr.amount, arr)
        if (amType != Datatype.Integer()) throw RuntimeException("array size has to be an integer")
        arr.type = Datatype.ArrayType(typeNodeToDataType(arr.typeNode))
        return arr.type
    }

    override fun visit(arr: AstNode.ArrayLiteral): Datatype {
        if (arr.elements.isEmpty()) throw RuntimeException("array literal is empty")
        val type = check(arr.elements[0], arr)
        if (type.matches(Datakind.STAT_CLASS)) throw RuntimeException("expected value in array literal")
        for (i in 1 until arr.elements.size) {
            val res = check(arr.elements[i], arr)
            if (type != res) throw RuntimeException("incompatible types in array initializer: $type and $res")
        }
        arr.type = Datatype.ArrayType(type)
        return arr.type
    }

    private fun doFuncSigsMatch(types1: List<Datatype>, types2: List<Pair<String, Datatype>>): Boolean {
        val types2NoThis = types2.toMutableList()
        if (types2.isNotEmpty() && types2[0].first == "this") types2NoThis.removeAt(0)
        if (types1.size != types2NoThis.size) return false
        for (i in types1.indices) if (types1[i] != types2NoThis[i].second) return false
        return true
    }

    private fun check(node: AstNode, parent: AstNode?): Datatype {
        val res = node.accept(this)
        if (swap == null) return res
        if (parent == null) throw AstNode.CantSwapException()
        parent.swap(node, swap!!)
        swap = null
        return res
    }

    private fun typeNodeToDataType(node: AstNode.DatatypeNode): Datatype = when (node.kind) {
        Datakind.BOOLEAN -> if (node.isArray) Datatype.ArrayType(Datatype.Bool()) else Datatype.Bool()
        Datakind.BYTE -> if (node.isArray) Datatype.ArrayType(Datatype.Byte()) else Datatype.Byte()
        Datakind.SHORT -> if (node.isArray) Datatype.ArrayType(Datatype.Short()) else Datatype.Short()
        Datakind.INT -> if (node.isArray) Datatype.ArrayType(Datatype.Integer()) else Datatype.Integer()
        Datakind.LONG -> if (node.isArray) Datatype.ArrayType(Datatype.Long()) else Datatype.Long()
        Datakind.FLOAT -> if (node.isArray) Datatype.ArrayType(Datatype.Float()) else Datatype.Float()
        Datakind.DOUBLE -> if (node.isArray) Datatype.ArrayType(Datatype.Double()) else Datatype.Double()
        Datakind.STRING -> if (node.isArray) Datatype.ArrayType(Datatype.Str()) else Datatype.Str()
        Datakind.OBJECT -> {
            node as AstNode.ObjectTypeNode
            var toRet: Datatype? = null
            for (c in curProgram.classes) if (c.name.lexeme == node.identifier.lexeme) {
                toRet = Datatype.Object(c.name.lexeme, c)
            }
            toRet ?: throw RuntimeException("unknown Type: ${node.identifier.lexeme}")
            if (node.isArray) Datatype.ArrayType(toRet) else toRet
        }
        else -> throw RuntimeException("invalid type")
    }

    private fun getDatatypeFromToken(token: TokenType) = when (token) {
        TokenType.INT -> Datatype.Integer()
        TokenType.STRING -> Datatype.Str()
        TokenType.FLOAT -> Datatype.Float()
        TokenType.BOOLEAN -> Datatype.Bool()
        else -> throw RuntimeException("unreachable")
    }

    private fun getHigherType(type1: Datatype, type2: Datatype): Datatype? {
        if (type1.compatibleWith(type2)) return type2
        if (type2.compatibleWith(type1)) return type1
        return null
    }

    abstract class Datatype(val kind: Datakind) {

        abstract val descriptorType: String

        abstract fun compatibleWith(other: Datatype): Boolean

        abstract override fun equals(other: Any?): Boolean
        abstract override fun toString(): String

        fun matches(vararg kinds: Datakind): Boolean {
            for (kind in kinds) if (kind == this.kind) return true
            return false
        }

        class Integer : Datatype(Datakind.INT) {
            override val descriptorType: String = "I"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Integer::class
            override fun toString(): String = "int"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.INT, Datakind.ERROR)
            }
        }
        class Byte : Datatype(Datakind.BYTE) {
            override val descriptorType: String = "B"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Byte::class
            override fun toString(): String = "byte"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.ERROR)
            }
        }
        class Short : Datatype(Datakind.SHORT) {
            override val descriptorType: String = "S"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Short::class
            override fun toString(): String = "short"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.SHORT, Datakind.INT, Datakind.ERROR)
            }
        }
        class Long : Datatype(Datakind.LONG) {
            override val descriptorType: String = "J"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Long::class
            override fun toString(): String = "long"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.LONG, Datakind.ERROR)
            }
        }

        class Float : Datatype(Datakind.FLOAT) {
            override val descriptorType: String = "F"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Float::class
            override fun toString(): String = "float"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.FLOAT, Datakind.ERROR)
            }
        }
        class Double : Datatype(Datakind.DOUBLE) {
            override val descriptorType: String = "D"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Double::class
            override fun toString(): String = "double"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.DOUBLE, Datakind.ERROR)
            }
        }


        class Bool : Datatype(Datakind.BOOLEAN) {
            override val descriptorType: String = "Z"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Bool::class
            override fun toString(): String = "bool"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.BOOLEAN, Datakind.ERROR)
            }
        }

        class Str: Datatype(Datakind.STRING) {
            override val descriptorType: String = "Ljava/lang/String;"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Str::class
            override fun toString(): String = "str"
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.STRING, Datakind.ERROR)
            }
        }

        class Void : Datatype(Datakind.VOID) {
            override val descriptorType: String = "V"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Void::class
            override fun toString(): String = "void"
            override fun compatibleWith(other: Datatype): Boolean = false
        }

        class Object(val name: String, val clazz: AstNode.ArtClass) : Datatype(Datakind.OBJECT) {
            override val descriptorType: String = "L$name;"
            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == Object::class && name == (other as Object).name
            }
            override fun toString(): String = name
            override fun compatibleWith(other: Datatype): Boolean {
                return other.kind in arrayOf(Datakind.OBJECT, Datakind.ERROR)
            }
        }

        class StatClass(val clazz: AstNode.ArtClass) : Datatype(Datakind.STAT_CLASS) {

            override val descriptorType: String = "Ljava/lang/Class;"

            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == StatClass::class && clazz === (other as StatClass).clazz
            }

            override fun toString(): String = "Class<${clazz.name.lexeme}>"
            override fun compatibleWith(other: Datatype): Boolean = false
        }

        class ArrayType(val type: Datatype) : Datatype(Datakind.ARRAY) {
            override val descriptorType: String = "[${type.descriptorType}"

            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == ArrayType::class && (other as ArrayType).type == type
            }

            override fun toString(): String {
                return "Array<$type>"
            }
            override fun compatibleWith(other: Datatype): Boolean {
                if (other.matches(Datakind.ARRAY)) return type.compatibleWith((other as ArrayType).type)
                return false
            }
        }

        class ErrorType : Datatype(Datakind.ERROR) {
            override val descriptorType: String = "--ERROR--"
            override fun compatibleWith(other: Datatype): Boolean = true
            override fun toString(): String = "--ERROR--"
            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == ErrorType::class
            }
        }

    }

    enum class Datakind {
        INT, LONG, BYTE, SHORT, FLOAT, DOUBLE, STRING, VOID, BOOLEAN, OBJECT,
        ARRAY,
        ERROR,
        STAT_CLASS
    }
}
