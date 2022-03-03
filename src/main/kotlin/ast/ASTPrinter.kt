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
        for (c in stmt.classes) builder.append(c.accept(this))
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

    override fun visit(stmt: Statement.If): String {
        val ifPart = "if ${stmt.condition.accept(this)} ${stmt.ifStmt.accept(this)}\n"
        val elseStmt = stmt.elseStmt ?: return ifPart
        return "${ifPart}else ${elseStmt.accept(this)}"
    }

    override fun visit(exp: Expression.Group): String {
        return "(${exp.grouped.accept(this)})"
    }

    override fun visit(exp: Expression.Unary): String {
        return "(${exp.operator.lexeme} ${exp.exp.accept(this)})"
    }

    override fun visit(stmt: Statement.While): String {
        return "while ${stmt.condition.accept(this)} ${stmt.body.accept(this)}\n"
    }

    override fun visit(exp: Expression.FunctionCall): String {
        val builder = StringBuilder()
        builder
            .append("(i ")
            .append(exp.name.lexeme)
        for (arg in exp.arguments) {
            builder
                .append(" ")
                .append(arg.accept(this))
        }
        builder.append(")")
        return builder.toString()
    }

    override fun visit(stmt: Statement.Return): String {
        return "(r ${stmt.returnExpr?.accept(this) ?: ""})\n"
    }

    override fun visit(stmt: Statement.VarIncrement): String {
        return "(${stmt.name.lexeme} ${stmt.toAdd} ++)\n"
    }

    override fun visit(stmt: Statement.ArtClass): String {
        val builder = StringBuilder()
        builder.append("class ${stmt.name.lexeme} {\n")
        for (func in stmt.funcs) builder.append(func.accept(this))
        builder.append("}\n")
        return builder.toString()
    }
}