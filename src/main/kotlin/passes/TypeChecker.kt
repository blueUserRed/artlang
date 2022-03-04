package passes

import ast.Expression
import ast.ExpressionVisitor
import ast.Statement
import ast.StatementVisitor
import tokenizer.TokenType
import java.lang.RuntimeException

class TypeChecker : ExpressionVisitor<TypeChecker.Datatype>, StatementVisitor<TypeChecker.Datatype> {

    private var vars: MutableMap<Int, Datatype> = mutableMapOf()
    private lateinit var curProgram: Statement.Program
    private lateinit var curFunction: Statement.Function

    override fun visit(exp: Expression.Binary): Datatype {
        val type1 = check(exp.left)
        val type2 = check(exp.right)
        val resultType: Datatype

        when (exp.operator.tokenType) {
            TokenType.PLUS -> {
                if (type1 != type2 || type1 !in arrayOf(Datatype.INT, Datatype.STRING)) {
                    throw RuntimeException("Illegal types in addition: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.MINUS -> {
                if (type1 != type2 || type1 != Datatype.INT) {
                    throw RuntimeException("Illegal types in subtraction: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.STAR -> {
                if (type1 != type2 || type1 != Datatype.INT) {
                    throw RuntimeException("Illegal types in multiplication: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.SLASH -> {
                if (type1 != type2 || type1 != Datatype.INT) {
                    throw RuntimeException("Illegal types in division: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.MOD -> {
                if (type1 != type2 || type1 != Datatype.INT) {
                    throw RuntimeException("Illegal types in modulo: $type1 and $type2")
                }
                resultType = type1
            }
            TokenType.D_EQ, TokenType.NOT_EQ -> {
                if (type1 != type2) throw RuntimeException("Illegal types in equals: $type1 and $type2")
                resultType = Datatype.BOOLEAN
            }
            TokenType.D_AND, TokenType.D_OR -> {
                if (type1 != type2 || type1 != Datatype.BOOLEAN) {
                    throw RuntimeException("Illegal types in boolean comparison: $type1 and $type2")
                }
                resultType = Datatype.BOOLEAN
            }
            TokenType.GT, TokenType.GT_EQ, TokenType.LT, TokenType.LT_EQ -> {
                if (type1 != type2 || type1 != Datatype.INT) {
                    throw RuntimeException("Illegal types in comparison: $type1 and $type2")
                }
                resultType = Datatype.BOOLEAN
            }
            else -> throw RuntimeException("unreachable")
        }

        exp.type = resultType
        return resultType
    }

    override fun visit(exp: Expression.Literal): Datatype {
        val type = when (exp.literal.tokenType) {
            TokenType.INT -> Datatype.INT
            TokenType.STRING -> Datatype.STRING
            TokenType.FLOAT -> Datatype.FLOAT
            TokenType.BOOLEAN -> Datatype.BOOLEAN
            else -> throw RuntimeException("unreachable")
        }
        exp.type = type
        return type
    }

    override fun visit(stmt: Statement.ExpressionStatement): Datatype {
        check(stmt.exp)
        return Datatype.VOID
    }

    override fun visit(stmt: Statement.Function): Datatype {
        curFunction = stmt

        val newVars = mutableMapOf<Int, Datatype>()
        for (i in stmt.args.indices) newVars[i] = stmt.args[i].second
        vars = newVars
        stmt.statements.accept(this)
        return Datatype.VOID
    }

    override fun visit(stmt: Statement.Program): Datatype {
        curProgram = stmt
        for (func in stmt.funcs) precCalcFuncArgs(func)
        for (func in stmt.funcs) func.accept(this)
        for (c in stmt.classes) c.accept(this)
        return Datatype.VOID
    }

    private fun precCalcFuncArgs(func: Statement.Function) { //TODO: fix for class funcs
        curFunction = func

        val args = mutableListOf<Pair<String, Datatype>>()
        for (arg in func.argTokens) args.add(Pair(arg.first.lexeme, tokenToDataType(arg.second.tokenType)))
        func.args = args

        func.returnType = func.returnTypeToken?.let { tokenToDataType(it.tokenType) } ?: Datatype.VOID
    }

    override fun visit(stmt: Statement.Print): Datatype {
        check(stmt.toPrint)
        return Datatype.VOID
    }

    override fun visit(stmt: Statement.Block): Datatype {
        for (s in stmt.statements) s.accept(this)
        return Datatype.VOID
    }

    override fun visit(exp: Expression.Variable): Datatype {
        val datatype = vars[exp.index] ?: throw RuntimeException("unreachable")
        exp.type = datatype
        return datatype
    }

    override fun visit(stmt: Statement.VariableDeclaration): Datatype {
        val type = check(stmt.initializer)
        if (stmt.typeToken != null) {
            val type2 = tokenToDataType(stmt.typeToken!!.tokenType)
            if (type2 != type) throw RuntimeException("Incompatible types in declaration: $type2 and $type")
        }
        vars[stmt.index] = type
        stmt.type = type

        return Datatype.VOID
    }

    override fun visit(stmt: Statement.VariableAssignment): Datatype {
        val type = check(stmt.expr)
        val varType = vars[stmt.index] ?: throw RuntimeException("unreachable")
        if (type != varType) throw RuntimeException("tried to assign $type to $varType")
        stmt.type = type
        return Datatype.VOID
    }

    override fun visit(stmt: Statement.Loop): Datatype {
        stmt.stmt.accept(this)
        return Datatype.VOID
    }

    override fun visit(stmt: Statement.If): Datatype {
        val type = check(stmt.condition)
        if (type != Datatype.BOOLEAN) throw RuntimeException("Expected Boolean value")
        stmt.ifStmt.accept(this)
        stmt.elseStmt?.accept(this)
        return Datatype.VOID
    }

    override fun visit(exp: Expression.Unary): Datatype {
        val type = check(exp.exp)
        if (exp.operator.tokenType == TokenType.MINUS) {
            if (type != Datatype.INT) throw RuntimeException("cant negate $type")
        } else {
            if (type != Datatype.BOOLEAN) throw RuntimeException("cant invert $type")
        }
        exp.type = type
        return type
    }

    override fun visit(exp: Expression.Group): Datatype {
        return check(exp.grouped)
    }

    override fun visit(stmt: Statement.While): Datatype {
        if (check(stmt.condition) != Datatype.BOOLEAN) throw RuntimeException("Expected Boolean value")
        stmt.body.accept(this)
        return Datatype.VOID
    }

    override fun visit(exp: Expression.FunctionCall): Datatype {
        val thisSig = mutableListOf<Datatype>()
        for (arg in exp.arguments) {
            check(arg)
            thisSig.add(arg.type)
        }
        var funcIndex: Int? = null
        for (i in curProgram.funcs.indices) {
            if (!doFuncSigsMatch(thisSig, curProgram.funcs[i].args)) continue
            funcIndex = i
        }
        if (funcIndex == null) throw RuntimeException("Function ${exp.name.lexeme} does not exist")
        exp.funcIndex = funcIndex
        val type = curProgram.funcs[funcIndex].returnType
        exp.type = type
        return type
    }

    override fun visit(stmt: Statement.Return): Datatype {
        val type = stmt.returnExpr?.let { check(it) } ?: Datatype.VOID
        if (curFunction.returnType != type) {
            throw RuntimeException("incompatible return types: $type and ${curFunction.returnType}")
        }
        return type
    }

    override fun visit(stmt: Statement.VarIncrement): Datatype {
        val varType = vars[stmt.index] ?: throw RuntimeException("unreachable")
        if (varType != Datatype.INT) TODO("not yet implemented")
        stmt.type = varType
        return Datatype.VOID
    }

    override fun visit(stmt: Statement.ArtClass): Datatype {
        for (func in stmt.funcs) func.accept(this)
        return Datatype.VOID
    }

    override fun visit(exp: Expression.WalrusAssign): Datatype {
        val type = check(exp.assign)
        exp.type = type
        return type
    }

    private fun doFuncSigsMatch(types1: List<Datatype>, types2: List<Pair<String, Datatype>>): Boolean {
        if (types1.size != types2.size) return false
        for (i in types1.indices) if (types1[i] != types2[i].second) return false
        return true
    }

    private fun check(exp: Expression): Datatype = exp.accept(this)

    private fun tokenToDataType(token: TokenType): Datatype = when (token) {
        TokenType.T_BOOLEAN -> Datatype.BOOLEAN
        TokenType.T_INT -> Datatype.INT
        TokenType.T_STRING -> Datatype.STRING
        else -> throw RuntimeException("invalid type")
    }

    enum class Datatype {
        INT, FLOAT, STRING, VOID, BOOLEAN //TODO: more types
    }
}