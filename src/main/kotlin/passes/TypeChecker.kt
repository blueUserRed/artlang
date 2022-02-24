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

        if (type1 != type2) throw RuntimeException("incopatible types in binary operation: $type1 and $type2")

        if (type1 !in arrayOf(Datatype.INT, Datatype.FLOAT, Datatype.STRING)) {
            throw RuntimeException("incompatible type in binary operation: $type1")
        }

        if (type1 == Datatype.STRING && exp.operator.tokenType != TokenType.PLUS) {
            throw RuntimeException("Strings can only be added")
        }

        exp.type = type1
        return type1
    }

    override fun visit(exp: Expression.Literal): Datatype {
        val type = when (exp.literal.tokenType) {
            TokenType.INT -> Datatype.INT
            TokenType.STRING -> Datatype.STRING
            TokenType.FLOAT -> Datatype.FLOAT
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

    private fun check(exp: Expression): Datatype = exp.accept(this)

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

    enum class Datatype {
        INT, FLOAT, STRING, VOID //TODO: more types
    }
}