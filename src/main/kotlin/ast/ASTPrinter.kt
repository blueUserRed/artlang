package ast

class ASTPrinter : ExpressionVisitor<String>, StatementVisitor<String> {

    override fun visit(exp: Expression.Binary): String {
        return "(${exp.left.accept(this)} ${exp.right.accept(this)} ${exp.operator.lexeme})"
    }

    override fun visit(exp: Expression.Literal): String {
        return exp.literal.lexeme
    }

    override fun visit(stmt: Statement.ExpressionStatement): String {
        return "${stmt.exp.accept(this)}\n"
    }

    override fun visit(stmt: Statement.Function): String {
        TODO("Not yet implemented")
    }

    override fun visit(stmt: Statement.Program): String {
        TODO("Not yet implemented")
    }

    override fun visit(stmt: Statement.Print): String {
        return "print ${stmt.toPrint.accept(this)}\n"
    }

    override fun visit(stmt: Statement.Block): String {
        val builder = StringBuilder()
        builder.append("{\n")
        for (s in stmt.statements) builder.append(s.accept(this))
        builder.append("}")
        return builder.toString()
    }

}