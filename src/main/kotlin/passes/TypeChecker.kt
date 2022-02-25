package passes

import ast.Expression
import ast.ExpressionVisitor
import ast.Statement
import ast.StatementVisitor
import tokenizer.TokenType
import java.lang.RuntimeException

class TypeChecker : ExpressionVisitor<TypeChecker.Datatype>, StatementVisitor<TypeChecker.Datatype> {

    private val vars: MutableMap<Int, Datatype> = mutableMapOf()

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
            TokenType.D_EQ, TokenType.NOT_EQ -> {
                if (type1 != type2) throw RuntimeException("Illegal types in equals: $type1 and $type2")
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
        stmt.statements.accept(this)
        return Datatype.VOID
    }

    override fun visit(stmt: Statement.Program): Datatype {
        for (func in stmt.funcs) func.accept(this)
        return Datatype.VOID
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

    private fun check(exp: Expression): Datatype = exp.accept(this)

    enum class Datatype {
        INT, FLOAT, STRING, VOID, BOOLEAN //TODO: more types
    }
}