package ast

import tokenizer.TokenType

class ASTPrinter : AstNodeVisitor<String> {

    override fun visit(exp: AstNode.Binary): String {
        return "(${exp.left.accept(this)} ${exp.right.accept(this)} ${exp.operator.lexeme})"
    }

    override fun visit(exp: AstNode.Literal): String {
        if (exp.literal.tokenType == TokenType.STRING) return "'${exp.literal.lexeme}'"
        return exp.literal.lexeme
    }

    override fun visit(stmt: AstNode.ExpressionStatement): String {
        return "${stmt.exp.accept(this)}\n"
    }

    override fun visit(stmt: AstNode.Function): String {
        val builder = StringBuilder()
        builder.append("fn ").append(stmt.name.lexeme).append("() {\n")
        for (s in stmt.statements.statements) builder.append(s.accept(this))
        builder.append("}\n")
        return builder.toString()
    }

    override fun visit(stmt: AstNode.Program): String {
        val builder = StringBuilder()
        for (func in stmt.funcs) builder.append(func.accept(this))
        for (c in stmt.classes) builder.append(c.accept(this))
        return builder.toString()
    }

    override fun visit(stmt: AstNode.Print): String {
        return "(p ${stmt.toPrint.accept(this)})\n"
    }

    override fun visit(stmt: AstNode.Block): String {
        val builder = StringBuilder()
        builder.append("{\n")
        for (s in stmt.statements) builder.append(s.accept(this))
        builder.append("}")
        return builder.toString()
    }

    override fun visit(exp: AstNode.Variable): String {
        return exp.name.lexeme
    }

    override fun visit(stmt: AstNode.VariableDeclaration): String {
        return "(let ${stmt.name.lexeme} ${stmt.initializer.accept(this)})\n"
    }

    override fun visit(stmt: AstNode.VariableAssignment): String {
        return "(${stmt.name.lexeme} = ${stmt.toAssign.accept(this)})\n"
    }

    override fun visit(stmt:AstNode.Loop): String {
        return "loop {\n${stmt.body.accept(this)}}\n"
    }

    override fun visit(stmt: AstNode.If): String {
        val ifPart = "if ${stmt.condition.accept(this)} ${stmt.ifStmt.accept(this)}\n"
        val elseStmt = stmt.elseStmt ?: return ifPart
        return "${ifPart}else ${elseStmt.accept(this)}"
    }

    override fun visit(exp: AstNode.Group): String {
        return "(${exp.grouped.accept(this)})"
    }

    override fun visit(exp: AstNode.Unary): String {
        return "(${exp.operator.lexeme} ${exp.on.accept(this)})"
    }

    override fun visit(stmt: AstNode.While): String {
        return "while ${stmt.condition.accept(this)} ${stmt.body.accept(this)}\n"
    }

    override fun visit(exp: AstNode.FunctionCall): String {
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

    override fun visit(stmt: AstNode.Return): String {
        return "(r ${stmt.toReturn?.accept(this) ?: ""})\n"
    }

    override fun visit(stmt: AstNode.VarIncrement): String {
        return "(${stmt.name.lexeme} ${stmt.toAdd} ++)\n"
    }

    override fun visit(stmt: AstNode.ArtClass): String {
        val builder = StringBuilder()
        builder.append("class ${stmt.name.lexeme} {\n")
        for (func in stmt.funcs) builder.append(func.accept(this))
        builder.append("}\n")
        return builder.toString()
    }

    override fun visit(exp: AstNode.WalrusAssign): String {
        return "(${exp.name.lexeme} ${exp.toAssign.accept(this)} :=)"
    }
}
