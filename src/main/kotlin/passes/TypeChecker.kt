package passes

import ast.AstPrinter
import ast.AstNode
import ast.AstNodeVisitor
import ast.FunctionDescriptor
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

    override fun visit(binary: AstNode.Binary): Datatype {
        val type1 = check(binary.left, binary)
        val type2 = check(binary.right, binary)
        val resultType: Datatype

        when (binary.operator.tokenType) {
            TokenType.PLUS -> {
                if (type1 != type2 || !type1.matches(Datakind.INT, Datakind.STRING)) {
                    throw RuntimeException("Illegal types in addition: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.MINUS -> {
                if (type1 != type2 || !type1.matches(Datakind.INT)) {
                    throw RuntimeException("Illegal types in subtraction: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.STAR -> {
                if (type1 != type2 || !type1.matches(Datakind.INT)) {
                    throw RuntimeException("Illegal types in multiplication: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.SLASH -> {
                if (type1 != type2 || !type1.matches(Datakind.INT)) {
                    throw RuntimeException("Illegal types in division: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.MOD -> {
                if (type1 != type2 || !type1.matches(Datakind.INT)) {
                    throw RuntimeException("Illegal types in modulo: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.D_EQ, TokenType.NOT_EQ -> {
                if (type1 != type2) throw RuntimeException("Illegal types in equals: $type1 and $type2")
                resultType = Datatype.Bool()
            }
            TokenType.D_AND, TokenType.D_OR -> {
                if (type1 != type2 || !type1.matches(Datakind.BOOLEAN)) {
                    throw RuntimeException("Illegal types in boolean comparison: $type1 and $type2")
                }
                resultType = Datatype.Bool()
            }
            TokenType.GT, TokenType.GT_EQ, TokenType.LT, TokenType.LT_EQ -> {
                if (type1 != type2 || !type1.matches(Datakind.INT)) {
                    throw RuntimeException("Illegal types in comparison: $type1 and $type2")
                }
                resultType = Datatype.Bool()
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
        for (i in function.functionDescriptor.args.indices) newVars[i] = function.functionDescriptor.args[i].second
        vars = newVars
        function.clazz = curClass
        function.statements.accept(this)
        return Datatype.Void()
    }

    override fun visit(program: AstNode.Program): Datatype {
        curProgram = program
        for (func in program.funcs) preCalcFuncSigs(func, null)
        for (c in program.classes) preCalcClass(c)
        for (field in program.fields) check(field, program)
        for (func in program.funcs) check(func, program)
        for (c in program.classes) check(c, program)
        return Datatype.Void()
    }

    private fun preCalcClass(clazz: AstNode.ArtClass) {
        for (func in clazz.staticFuncs) preCalcFuncSigs(func, clazz)
        for (func in clazz.funcs) preCalcFuncSigs(func, clazz)
    }

    private fun preCalcFuncSigs(func: AstNode.Function, clazz: AstNode.ArtClass?) {
        curFunction = func

        val args = mutableListOf<Pair<String, Datatype>>()
        if (func.hasThis) args.add(Pair("this", Datatype.Object(clazz!!.name.lexeme, clazz)))
        for (arg in func.args) args.add(Pair(arg.first.lexeme, typeNodeToDataType(arg.second)))

        val returnType = func.returnType?.let { typeNodeToDataType(it) } ?: Datatype.Void()
        func.functionDescriptor = FunctionDescriptor(args, returnType)
    }

    override fun visit(print: AstNode.Print): Datatype {
        check(print.toPrint, print)
        return Datatype.Void()
    }

    override fun visit(block: AstNode.Block): Datatype {
        for (s in block.statements) check(s, block)
        return Datatype.Void()
    }

    override fun visit(variable: AstNode.Variable): Datatype {
        if (variable.index != -1) {
            val datatype = vars[variable.index] ?: throw RuntimeException("unreachable")
            variable.type = datatype
            return datatype
        }
        for (c in curProgram.classes) if (c.name.lexeme == variable.name.lexeme) {
            variable.type = Datatype.StatClass(c)
            return variable.type
        }
        for (field in curProgram.fields) if (field.name.lexeme == variable.name.lexeme) {
            val toSwap = AstNode.FieldReference(field.name)
            toSwap.type = field.fieldType
            swap = toSwap
            return field.fieldType
        }
        throw RuntimeException("Unknown Variable ${variable.name.lexeme}")
    }

    override fun visit(varDec: AstNode.VariableDeclaration): Datatype {
        val type = check(varDec.initializer, varDec)
        if (type == Datatype.Void()) throw RuntimeException("Expected Expression in var initializer")
        if (varDec.explType != null) {
            val type2 = typeNodeToDataType(varDec.explType!!)
            if (type2 != type) throw RuntimeException("Incompatible types in declaration: $type2 and $type")
        }
        vars[varDec.index] = type

        return Datatype.Void()
    }

    override fun visit(varAssign: AstNode.VariableAssignment): Datatype {
        val typeToAssign = check(varAssign.toAssign, varAssign)
        if (varAssign.index != -1) {
            val varType = vars[varAssign.index] ?: throw RuntimeException("unreachable")
            if (typeToAssign != varType) throw RuntimeException("tried to assign $typeToAssign to $varType")
            return Datatype.Void()
        }

        for (field in curProgram.fields) if (field.name.lexeme == varAssign.name.lexeme) {
            val toSwap = AstNode.FieldSet(varAssign.name, varAssign.toAssign, field)
            if (typeToAssign != toSwap.definition.fieldType) {
                throw RuntimeException("tried to assign $typeToAssign to ${toSwap.type}")
            }
            toSwap.type = Datatype.Void()
            swap = toSwap
            return Datatype.Void()
        }
        throw RuntimeException("cant find variable ${varAssign.name.lexeme}")
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

        if (funcCall.func is Either.Left) return doFuncCallFromNode(funcCall, thisSig)

        var funcDefinition: AstNode.Function? = null
        if (funcDefinition == null) funcDefinition = findStaticFunc(funcCall, thisSig)

        val result = doConstructorCall(funcDefinition, funcCall, thisSig)
        if (result != null) return result

        if (funcDefinition == null) throw RuntimeException("Function ${funcCall.getFullName()} does not exist")

        funcCall.definition = funcDefinition
        val type = funcDefinition.functionDescriptor.returnType
        funcCall.type = type
        return type
    }

    private fun doConstructorCall(
        funcDefinition: AstNode.Function?,
        funcCall: AstNode.FunctionCall,
        thisSig: MutableList<Datatype>
    ): Datatype.Object? {
        if (
            funcDefinition == null &&
            curClass != null &&
            curClass!!.name.lexeme == (funcCall.func as Either.Right<Token>).value.lexeme &&
            thisSig.size == 0
        ) {
            swap = AstNode.ConstructorCall(curClass!!, mutableListOf())
            swap!!.type = Datatype.Object(curClass!!.name.lexeme, curClass!!)
            return Datatype.Object(curClass!!.name.lexeme, curClass!!)
        }

        if (funcDefinition == null && thisSig.size == 0) for (c in curProgram.classes) {
            if (c.name.lexeme == (funcCall.func as Either.Right<Token>).value.lexeme) {
                swap = AstNode.ConstructorCall(c, mutableListOf())
                swap!!.type = Datatype.Object(c.name.lexeme, c)
                return Datatype.Object(c.name.lexeme, c)
            }
        }
        return null
    }

    private fun findStaticFunc(funcCall: AstNode.FunctionCall, thisSig: MutableList<Datatype>): AstNode.Function? {
        if (curClass != null) for (func in curClass!!.staticFuncs) {
            if (func.name.lexeme != (funcCall.func as Either.Right<Token>).value.lexeme) continue
            if (!doFuncSigsMatch(thisSig, func.functionDescriptor.args)) continue
            return func
        }
        for (func in curProgram.funcs) {
            if (func.name.lexeme != (funcCall.func as Either.Right<Token>).value.lexeme) continue
            if (!doFuncSigsMatch(thisSig, func.functionDescriptor.args)) continue
            return func
        }
        return null
    }

    private fun doFuncCallFromNode(funcCall: AstNode.FunctionCall, thisSig: MutableList<Datatype>): Datatype {
        val ref = check((funcCall.func as Either.Left<AstNode>).value, funcCall)

        if (ref.matches(Datakind.STAT_FUNC_REF)) {

            ref as Datatype.StatFuncRef
            if (!doFuncSigsMatch(thisSig, ref.func.functionDescriptor.args)) {
                throw RuntimeException("incorrect Arguments supplied for static function ${funcCall.getFullName()}")
            }
            funcCall.definition = ref.func
            funcCall.type = ref.func.functionDescriptor.returnType
            return ref.func.functionDescriptor.returnType

        } else if (ref.matches(Datakind.AMBIG_STAT_FUNC_REF)) {

            ref as Datatype.AmbigStatFuncRef
            var definition: AstNode.Function? = null
            for (func in ref.possibilities) if (doFuncSigsMatch(thisSig, func.func.functionDescriptor.args)) {
                definition = func.func
                break
            }
            if (definition == null) {
                throw RuntimeException("Cant call any variant of static function " +
                        "${funcCall.getFullName()} with arguments supplied")
            }
            funcCall.definition = definition
            funcCall.type = definition.functionDescriptor.returnType
            return definition.functionDescriptor.returnType

        } else if (ref.matches(Datakind.FUNC_REF)) {

            ref as Datatype.FuncRef
            if (!doFuncSigsMatch(thisSig, ref.func.functionDescriptor.args)) {
                throw RuntimeException("incorrect Arguments supplied for function ${funcCall.getFullName()}")
            }
            funcCall.definition = ref.func
            funcCall.type = ref.func.functionDescriptor.returnType
            return ref.func.functionDescriptor.returnType

        } else if (ref.matches(Datakind.AMBIG_FUNC_REF)) {

            ref as Datatype.AmbigFuncRef
            var definition: AstNode.Function? = null
            for (func in ref.possibilities) if (doFuncSigsMatch(thisSig, func.func.functionDescriptor.args)) {
                definition = func.func
                break
            }
            if (definition == null) {
                throw RuntimeException("Cant call any variant of function " +
                        "${funcCall.getFullName()} with arguments supplied")
            }
            funcCall.definition = definition
            funcCall.type = definition.functionDescriptor.returnType
            return definition.functionDescriptor.returnType

        } else throw RuntimeException("cant call any function on ${funcCall.getFullName()}")
    }

    override fun visit(returnStmt: AstNode.Return): Datatype {
        val type = returnStmt.toReturn?.let { check(it, returnStmt) } ?: Datatype.Void()
        if (curFunction.functionDescriptor.returnType != type) {
            throw RuntimeException("incompatible return types: $type and ${curFunction.functionDescriptor.returnType}")
        }
        return type
    }

    override fun visit(varInc: AstNode.VarIncrement): Datatype {
        if (varInc.index != -1) {
            val varType = vars[varInc.index] ?: throw RuntimeException("unreachable")
            if (varType != Datatype.Integer()) TODO("not yet implemented")
            return Datatype.Void()
        }
        for (field in curProgram.fields) if (field.name.lexeme == varInc.name.lexeme) {
            val toSwap = AstNode.FieldSet(
                varInc.name,
                AstNode.Binary(
                    AstNode.FieldReference(varInc.name),
                    Token(TokenType.PLUS, "+=", null, varInc.name.file,  varInc.name.pos),
                    AstNode.Literal(
                        Token(TokenType.INT, "+=", varInc.toAdd.toInt(), varInc.name.file, varInc.name.pos)
                    )
                ),
                field
            )
            toSwap.to.type = Datatype.Integer()
            (toSwap.to as AstNode.Binary).left.type = Datatype.Integer()
            (toSwap.to as AstNode.Binary).right.type = Datatype.Integer()
            swap = toSwap
            return Datatype.Void()
        }
        throw RuntimeException("unknown Variable ${varInc.name.lexeme}")
    }

    override fun visit(clazz: AstNode.ArtClass): Datatype {
        val tmp = curClass
        curClass = clazz
        for (func in clazz.staticFuncs) func.accept(this)
        for (func in clazz.funcs) func.accept(this)
        curClass = tmp
        return Datatype.Void()
    }

    override fun visit(walrus: AstNode.WalrusAssign): Datatype {
        val type = check(walrus.toAssign, walrus)
        walrus.type = type
        return type
    }

    override fun visit(get: AstNode.Get): Datatype {
        val from = check(get.from, get)
        if (from.matches(Datakind.STAT_CLASS)) return doGetFromStatClass(from as Datatype.StatClass, get)
        else if (from.matches(Datakind.OBJECT)) {

            from as Datatype.Object

            val possibilities: MutableList<Datatype.FuncRef> = mutableListOf()
            for (func in from.clazz.funcs) if (func.name.lexeme == get.name.lexeme) {
                if (func.isPrivate && curClass !== from.clazz) continue
                possibilities.add(Datatype.FuncRef(func))
            }
            if (possibilities.size == 0) {
                throw RuntimeException("couldn't access ${get.name.lexeme} from ${get.from.accept(AstPrinter())}")
            }
            val type = if (possibilities.size == 1) possibilities[0]
            else Datatype.AmbigFuncRef(possibilities)
            get.type = type
            return type
        } else TODO("not yet implemented")
    }

    private fun doGetFromStatClass(from: Datatype.StatClass, get: AstNode.Get): Datatype {
        val possibilities = mutableListOf<Datatype.StatFuncRef>()
        for (func in from.artClass.staticFuncs) if (func.name.lexeme == get.name.lexeme) {
            if (func.isPrivate && curClass !== from.artClass) continue
            possibilities.add(Datatype.StatFuncRef(func))
        }
        if (possibilities.size == 0) {
            throw RuntimeException("couldn't access static function ${get.name.lexeme} from ${get.from.accept(AstPrinter())}")
        }
        val type = if (possibilities.size == 1) possibilities[0]
        else Datatype.AmbigStatFuncRef(possibilities)
        get.type = type
        return type
    }

    override fun visit(set: AstNode.Set): Datatype {
        TODO("Not yet implemented")
    }

    override fun visit(walrus: AstNode.WalrusSet): Datatype {
        TODO("Not yet implemented")
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
        val explType = typeNodeToDataType(field.explType)
        if (explType != field.initializer.type) {
            throw RuntimeException("incompatible types in field declaration $explType and ${field.initializer.type}")
        }
        field.fieldType = explType
        return Datatype.Void()
    }

    override fun visit(fieldGet: AstNode.FieldReference): Datatype {
        throw RuntimeException("unreachable")
    }

    override fun visit(fieldSet: AstNode.FieldSet): Datatype {
        throw RuntimeException("unreachable")
    }

    private fun doFuncSigsMatch(types1: List<Datatype>, types2: List<Pair<String, Datatype>>): Boolean {
        val types2NoThis = types2.toMutableList()
        if (types2.isNotEmpty() && types2[0].first == "this") types2NoThis.removeAt(0)
        if (types1.size != types2NoThis.size) return false
        for (i in types1.indices) if (types1[i] != types2NoThis[i].second) return false
        return true
    }

    private fun check(node: AstNode, parent: AstNode): Datatype {
        val res = node.accept(this)
        if (swap == null) return res
        parent.swap(node, swap!!)
        swap = null
        return res
    }

    private fun typeNodeToDataType(node: AstNode.DatatypeNode): Datatype = when (node.kind) {
        Datakind.BOOLEAN -> Datatype.Bool()
        Datakind.INT -> Datatype.Integer()
        Datakind.STRING -> Datatype.Str()
        Datakind.OBJECT -> {
            node as AstNode.ObjectTypeNode
            var toRet: Datatype? = null
            for (c in curProgram.classes) if (c.name.lexeme == node.identifier.lexeme) {
                toRet = Datatype.Object(c.name.lexeme, c)
            }
            toRet ?: throw RuntimeException("unknown Type: ${node.identifier.lexeme}")
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

    abstract class Datatype(val kind: Datakind) {

        abstract val descriptorType: String

        abstract override fun equals(other: Any?): Boolean
        abstract override fun toString(): String


        class Integer : Datatype(Datakind.INT) {
            override val descriptorType: String = "I"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Integer::class
            override fun toString(): String = "int"
        }

        class Float : Datatype(Datakind.FLOAT) {
            override val descriptorType: String = "F"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Float::class
            override fun toString(): String = "float"
        }

        class Bool : Datatype(Datakind.BOOLEAN) {
            override val descriptorType: String = "Z"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Bool::class
            override fun toString(): String = "bool"
        }

        class Str: Datatype(Datakind.STRING) {
            override val descriptorType: String = "Ljava/lang/String;"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Str::class
            override fun toString(): String = "str"
        }

        class Void : Datatype(Datakind.VOID) {
            override val descriptorType: String = "V"
            override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Void::class
            override fun toString(): String = "void"
        }

        class Object(val name: String, val clazz: AstNode.ArtClass) : Datatype(Datakind.OBJECT) {
            override val descriptorType: String = "L$name;"
            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == Object::class && name == (other as Object).name
            }
            override fun toString(): String = name
        }

        fun matches(vararg kinds: Datakind): Boolean {
            for (kind in kinds) if (kind == this.kind) return true
            return false
        }

        class StatClass(val artClass: AstNode.ArtClass) : Datatype(Datakind.STAT_CLASS) {

            override val descriptorType: String = "Ljava/lang/Class;"

            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == StatClass::class && artClass === (other as StatClass).artClass
            }

            override fun toString(): String = "Class<${artClass.name.lexeme}>"
        }

        class StatFuncRef(val func: AstNode.Function) : Datatype(Datakind.STAT_FUNC_REF) {
            override val descriptorType: String = "---StatFuncRef has no descriptor---" //TODO: lol

            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == StatFuncRef::class && func === (other as StatFuncRef).func
            }

            override fun toString(): String = "StaticFuncRef<${func.name.lexeme}>"
        }

        class AmbigStatFuncRef(val possibilities: List<StatFuncRef>) : Datatype(Datakind.AMBIG_STAT_FUNC_REF) {

            override val descriptorType: String = "---AmbigStatFuncRef has no descriptor---" //TODO: lol

            override fun equals(other: Any?): Boolean {
                if (other == null) return false
                if (other::class != StatFuncRef::class) return false
                other as AmbigStatFuncRef
                for (i in possibilities.indices) if (possibilities[i] != other.possibilities[i]) return false
                return true
            }

            override fun toString(): String = "AmbigStatFuncRef"
        }

        class FuncRef(val func: AstNode.Function) : Datatype(Datakind.FUNC_REF) {
            override val descriptorType: String = "---FuncRef has no descriptor---" //TODO: lol

            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == FuncRef::class && func === (other as FuncRef).func
            }

            override fun toString(): String = "FuncRef<${func.name.lexeme}>"
        }

        class AmbigFuncRef(val possibilities: List<FuncRef>) : Datatype(Datakind.AMBIG_FUNC_REF) {

            override val descriptorType: String = "---AmbigFuncRef has no descriptor---" //TODO: lol

            override fun equals(other: Any?): Boolean {
                if (other == null) return false
                if (other::class != FuncRef::class) return false
                other as AmbigFuncRef
                for (i in possibilities.indices) if (possibilities[i] != other.possibilities[i]) return false
                return true
            }

            override fun toString(): String = "AmbigFuncRef"
        }
    }

    enum class Datakind {
        INT, FLOAT, STRING, VOID, BOOLEAN, OBJECT, STAT_CLASS,
        STAT_FUNC_REF, AMBIG_STAT_FUNC_REF, FUNC_REF, AMBIG_FUNC_REF
    }
}
