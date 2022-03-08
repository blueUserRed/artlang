package passes

import ast.ASTPrinter
import ast.AstNode
import ast.AstNodeVisitor
import ast.FunctionDescriptor
import tokenizer.TokenType
import java.lang.RuntimeException
import passes.TypeChecker.Datatype

class TypeChecker : AstNodeVisitor<Datatype> {

    private var vars: MutableMap<Int, Datatype> = mutableMapOf()
    private lateinit var curProgram: AstNode.Program
    private lateinit var curFunction: AstNode.Function

    override fun visit(binary: AstNode.Binary): Datatype {
        val type1 = check(binary.left)
        val type2 = check(binary.right)
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

    private fun getDatatypeFromToken(token: TokenType) = when (token) {
        TokenType.INT -> Datatype.Integer()
        TokenType.STRING -> Datatype.Str()
        TokenType.FLOAT -> Datatype.Float()
        TokenType.BOOLEAN -> Datatype.Bool()
        else -> throw RuntimeException("unreachable")
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): Datatype {
        check(exprStmt.exp)
        return Datatype.Void()
    }

    override fun visit(function: AstNode.Function): Datatype {
        curFunction = function

        val newVars = mutableMapOf<Int, Datatype>()
        for (i in function.functionDescriptor.args.indices) newVars[i] = function.functionDescriptor.args[i].second
        vars = newVars
        function.statements.accept(this)
        return Datatype.Void()
    }

    override fun visit(program: AstNode.Program): Datatype {
        curProgram = program
        for (func in program.funcs) precCalcFuncSigs(func)
        for (c in program.classes) for (func in c.funcs) precCalcFuncSigs(func)
        for (func in program.funcs) func.accept(this)
        for (c in program.classes) c.accept(this)
        return Datatype.Void()
    }

    private fun precCalcFuncSigs(func: AstNode.Function) { //TODO: fix for class funcs
        curFunction = func

        val args = mutableListOf<Pair<String, Datatype>>()
        for (arg in func.argTokens) args.add(Pair(arg.first.lexeme, tokenToDataType(arg.second.tokenType)))

        val returnType = func.returnTypeToken?.let { tokenToDataType(it.tokenType) } ?: Datatype.Void()
        func.functionDescriptor = FunctionDescriptor(args, returnType)
    }

    override fun visit(print: AstNode.Print): Datatype {
        check(print.toPrint)
        return Datatype.Void()
    }

    override fun visit(block: AstNode.Block): Datatype {
        for (s in block.statements) check(s)
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
        throw RuntimeException("Unknown Variable ${variable.name.lexeme}")
    }

    override fun visit(varDec: AstNode.VariableDeclaration): Datatype {
        val type = check(varDec.initializer)
        if (type == Datatype.Void()) throw RuntimeException("Expected Expression in var initializer")
        if (varDec.typeToken != null) {
            val type2 = tokenToDataType(varDec.typeToken!!.tokenType)
            if (type2 != type) throw RuntimeException("Incompatible types in declaration: $type2 and $type")
        }
        vars[varDec.index] = type

        return Datatype.Void()
    }

    override fun visit(varAssign: AstNode.VariableAssignment): Datatype {
        val type = check(varAssign.toAssign)
        val varType = vars[varAssign.index] ?: throw RuntimeException("unreachable")
        if (type != varType) throw RuntimeException("tried to assign $type to $varType")
        return Datatype.Void()
    }

    override fun visit(loop: AstNode.Loop): Datatype {
        check(loop.body)
        return Datatype.Void()
    }

    override fun visit(ifStmt: AstNode.If): Datatype {
        val type = check(ifStmt.condition)
        if (type != Datatype.Bool()) throw RuntimeException("Expected Boolean value")
        ifStmt.ifStmt.accept(this)
        ifStmt.elseStmt?.accept(this)
        return Datatype.Void()
    }

    override fun visit(unary: AstNode.Unary): Datatype {
        val type = check(unary.on)
        if (unary.operator.tokenType == TokenType.MINUS) {
            if (type != Datatype.Integer()) throw RuntimeException("cant negate $type")
        } else {
            if (type != Datatype.Bool()) throw RuntimeException("cant invert $type")
        }
        unary.type = type
        return type
    }

    override fun visit(group: AstNode.Group): Datatype {
        return check(group.grouped)
    }

    override fun visit(whileStmt: AstNode.While): Datatype {
        if (check(whileStmt.condition) != Datatype.Bool()) throw RuntimeException("Expected Boolean value")
        whileStmt.body.accept(this)
        return Datatype.Void()
    }

    override fun visit(funcCall: AstNode.FunctionCall): Datatype {
        val thisSig = mutableListOf<Datatype>()
        for (arg in funcCall.arguments) {
            check(arg)
            thisSig.add(arg.type)
        }

        if (funcCall.func is Either.Left) {
            val ref = check(funcCall.func.value)

            if (ref.matches(Datakind.STAT_FUNC_REF)) {
                ref as Datatype.StatFuncRef
                if (!doFuncSigsMatch(thisSig, ref.func.functionDescriptor.args)) {
                    throw RuntimeException("incorrect Arguments supplied for function ${funcCall.getFullName()}")
                }
                funcCall.definition = ref.func
                funcCall.type = ref.func.functionDescriptor.returnType
                return ref.func.functionDescriptor.returnType
            }

            if (ref.matches(Datakind.AMBIG_STAT_FUNC_REF)) {
                ref as Datatype.AmbigStatFuncRef
                var definition: AstNode.Function? = null
                for (func in ref.possibilities) if (doFuncSigsMatch(thisSig, func.func.functionDescriptor.args)) {
                    definition = func.func
                    break
                }
                if (definition == null) {
                    throw RuntimeException("Cant call any variant of ${funcCall.getFullName()} with arguments supplied")
                }
                funcCall.definition = definition
                funcCall.type = definition.functionDescriptor.returnType
                return definition.functionDescriptor.returnType
            }
        }

        var funcIndex: Int? = null
        for (i in curProgram.funcs.indices) {
            if (curProgram.funcs[i].name.lexeme != (funcCall.func as Either.Right).value.lexeme) continue
            if (!doFuncSigsMatch(thisSig, curProgram.funcs[i].functionDescriptor.args)) continue
            funcIndex = i
        }
        if (funcIndex == null) throw RuntimeException("Function ${funcCall.getFullName()} does not exist")
        funcCall.definition = curProgram.funcs[funcIndex]
        val type = curProgram.funcs[funcIndex].functionDescriptor.returnType
        funcCall.type = type
        return type
    }

    override fun visit(returnStmt: AstNode.Return): Datatype {
        val type = returnStmt.toReturn?.let { check(it) } ?: Datatype.Void()
        if (curFunction.functionDescriptor.returnType != type) {
            throw RuntimeException("incompatible return types: $type and ${curFunction.functionDescriptor.returnType}")
        }
        return type
    }

    override fun visit(varInc: AstNode.VarIncrement): Datatype {
        val varType = vars[varInc.index] ?: throw RuntimeException("unreachable")
        if (varType != Datatype.Integer()) TODO("not yet implemented")
        return Datatype.Void()
    }

    override fun visit(clazz: AstNode.ArtClass): Datatype {
        for (func in clazz.funcs) func.accept(this)
        return Datatype.Void()
    }

    override fun visit(walrus: AstNode.WalrusAssign): Datatype {
        val type = check(walrus.toAssign)
        walrus.type = type
        return type
    }

    override fun visit(get: AstNode.Get): Datatype {
        val from = check(get.from)
        if (!from.matches(Datakind.STAT_CLASS)) TODO("not yet implemented")
        from as Datatype.StatClass
        val possibilities = mutableListOf<Datatype.StatFuncRef>()
        for (func in from.artClass.funcs) if (func.name.lexeme == get.name.lexeme) possibilities.add(Datatype.StatFuncRef(func))
        if (possibilities.size == 0) {
            throw RuntimeException("couldn't access ${get.name.lexeme} from ${get.from.accept(ASTPrinter())}")
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

    private fun doFuncSigsMatch(types1: List<Datatype>, types2: List<Pair<String, Datatype>>): Boolean {
        if (types1.size != types2.size) return false
        for (i in types1.indices) if (types1[i] != types2[i].second) return false
        return true
    }

    private fun check(node: AstNode): Datatype = node.accept(this)

    private fun tokenToDataType(token: TokenType): Datatype = when (token) {
        TokenType.T_BOOLEAN -> Datatype.Bool()
        TokenType.T_INT -> Datatype.Integer()
        TokenType.T_STRING -> Datatype.Str()
        else -> throw RuntimeException("invalid type")
    }

    abstract class Datatype(val kind: Datakind) {

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

        class Object(val name: String) : Datatype(Datakind.OBJECT) {
            override val descriptorType: String = "L$name;"
            override fun equals(other: Any?): Boolean {
                return if (other == null) false else other::class == Object::class && name == (other as Object).name
            }
            override fun toString(): String = name
        }

        abstract val descriptorType: String

        abstract override fun equals(other: Any?): Boolean
        abstract override fun toString(): String

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
    }

    enum class Datakind {
        INT, FLOAT, STRING, VOID, BOOLEAN, OBJECT, STAT_CLASS, STAT_FUNC_REF, AMBIG_STAT_FUNC_REF
    }
}
