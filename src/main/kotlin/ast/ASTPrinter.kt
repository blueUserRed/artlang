package ast

import tokenizer.TokenType

class ASTPrinter : ExpressionVisitor<String>, StatementVisitor<String> {

    override fun visit(exp: Expression.Binary): String {
        return "(${exp.left.accept(this)} ${exp.right.accept(this)} ${exp.operator.lexeme})"
    }

    override fun visit(exp: Expression.Literal): String {
        if (exp.literal.tokenType == TokenType.STRING) return "'${exp.literal.lexeme}'"
        return exp.literal.lexeme
    }

    override fun visit(stmt: Statement.ExpressionStatement): String {
        return "${stmt.exp.accept(this)}\n"
    }

    override fun visit(stmt: Statement.Function): String {
        val builder = StringBuilder()
        builder.append("fn ").append(stmt.name.lexeme).append("() {\n")
        for (s in stmt.statements.statements) builder.append(s.accept(this))
        builder.append("}\n")
        return builder.toString()
    }

    override fun visit(stmt: Statement.Program): String {
        val builder = StringBuilder()
        for (func in stmt.funcs) builder.append(func.accept(this))
        return builder.toString()
    }

    override fun visit(stmt: Statement.Print): String {
        return "(p ${stmt.toPrint.accept(this)})\n"
    }

    override fun visit(stmt: Statement.Block): String {
        val builder = StringBuilder()
        builder.append("{\n")
        for (s in stmt.statements) builder.append(s.accept(this))
        builder.append("}")
        return builder.toString()
    }

    override fun visit(exp: Expression.Variable): String {
        return exp.name.lexeme
    }

    override fun visit(stmt: Statement.VariableDeclaration): String {
        return "(let ${stmt.name.lexeme} ${stmt.initializer.accept(this)})\n"
    }

    override fun visit(stmt: Statement.VariableAssignment): String {
        return "(${stmt.name.lexeme} = ${stmt.expr.accept(this)})\n"
    }

    override fun visit(stmt: Statement.Loop): String {
        return "loop {\n${stmt.stmt.accept(this)}}\n"
    }
}