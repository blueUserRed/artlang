package ast

import passes.TypeChecker
import tokenizer.TokenType

class AstPrinter : AstNodeVisitor<String> {

    override fun visit(binary: AstNode.Binary): String {
        return "(${binary.left.accept(this)} ${binary.right.accept(this)} ${binary.operator.lexeme})"
    }

    override fun visit(literal: AstNode.Literal): String {
        if (literal.literal.tokenType == TokenType.STRING) return "'${literal.literal.lexeme}'"
        return literal.literal.lexeme
    }

    override fun visit(exprStmt: AstNode.ExpressionStatement): String {
        return exprStmt.exp.accept(this)
    }

    override fun visit(function: AstNode.Function): String {
        val builder = StringBuilder("\n")
        for (modifier in function.modifiers) builder.append(modifier).append(" ")
        builder.append("fn ").append(function.name.lexeme).append("() {\n")
        for (s in function.statements.statements) builder.append(s.accept(this)).append("\n")
        builder.append("}\n")
        return builder.toString()
    }

    override fun visit(program: AstNode.Program): String {
        val builder = StringBuilder()
        for (field in program.fields) builder.append(field.accept(this))
        for (func in program.funcs) builder.append(func.accept(this))
        for (c in program.classes) builder.append(c.accept(this))
        return builder.toString()
    }

    override fun visit(print: AstNode.Print): String {
        return "(p ${print.toPrint.accept(this)})"
    }

    override fun visit(block: AstNode.Block): String {
        val builder = StringBuilder()
        builder.append("{\n")
        for (s in block.statements) builder.append(s.accept(this)).append("\n")
        builder.append("}")
        return builder.toString()
    }

    override fun visit(variable: AstNode.Variable): String {
        return variable.name.lexeme
    }

    override fun visit(varDec: AstNode.VariableDeclaration): String {
        return "(let ${varDec.name.lexeme} ${varDec.initializer.accept(this)})"
    }

    override fun visit(varAssign: AstNode.Assignment): String {
        return "(${varAssign.name.accept(this)} = ${varAssign.toAssign.accept(this)})"
    }

    override fun visit(loop:AstNode.Loop): String {
        return "loop ${loop.body.accept(this)}"
    }

    override fun visit(ifStmt: AstNode.If): String {
        val ifPart = "if ${ifStmt.condition.accept(this)} ${ifStmt.ifStmt.accept(this)}"
        val elseStmt = ifStmt.elseStmt ?: return ifPart
        return "$ifPart else ${elseStmt.accept(this)}"
    }

    override fun visit(group: AstNode.Group): String {
        return "(${group.grouped.accept(this)})"
    }

    override fun visit(unary: AstNode.Unary): String {
        return "(${unary.operator.lexeme} ${unary.on.accept(this)})"
    }

    override fun visit(whileStmt: AstNode.While): String {
        return "while ${whileStmt.condition.accept(this)} ${whileStmt.body.accept(this)}"
    }

    override fun visit(funcCall: AstNode.FunctionCall): String {
        val builder = StringBuilder()
        builder
            .append("(i ")
            .append(funcCall.getFullName())
        for (arg in funcCall.arguments) {
            builder
                .append(" ")
                .append(arg.accept(this))
        }
        builder.append(")")
        return builder.toString()
    }

    override fun visit(returnStmt: AstNode.Return): String {
        return "(r ${returnStmt.toReturn?.accept(this) ?: ""})"
    }

    override fun visit(varInc: AstNode.VarIncrement): String {
        return "(${varInc.name.accept(this)} ${varInc.toAdd} ++)"
    }

    override fun visit(clazz: AstNode.ArtClass): String {
        val builder = StringBuilder()
        builder.append("\nclass ${clazz.name.lexeme} {\n")
        for (field in clazz.fields) builder.append(field.accept(this)).append("\n")
        for (field in clazz.staticFields) builder.append(field.accept(this))
        for (func in clazz.staticFuncs) builder.append(func.accept(this))
        for (func in clazz.funcs) builder.append(func.accept(this))
        builder.append("}\n")
        return builder.toString()
    }

    override fun visit(walrus: AstNode.WalrusAssign): String {
        return "(${walrus.name.accept(this)} ${walrus.toAssign.accept(this)} :=)"
    }

    override fun visit(get: AstNode.Get): String {
        return if (get.from == null) "(${get.name.lexeme})" else "(${get.from!!.accept(this)}.${get.name.lexeme})"
    }

    override fun visit(cont: AstNode.Continue): String {
        return "(continue)"
    }

    override fun visit(breac: AstNode.Break): String {
        return "(break)"
    }

    override fun visit(constructorCall: AstNode.ConstructorCall): String {
        return "(new ${constructorCall.clazz.name.lexeme})"
    }

    override fun visit(field: AstNode.FieldDeclaration): String {
        val builder = StringBuilder()
        for (modifier in field.modifiers) builder.append(modifier.lexeme).append(" ")
        if (field.isConst) builder.append("const ")
        builder
            .append(field.name.lexeme)
            .append(" = ")
            .append(field.initializer.accept(this))
            .append("\n")
        return builder.toString()
    }

    override fun visit(arr: AstNode.ArrayCreate): String {
        return "new ${arr.typeNode}[${arr.amount.accept(this)}]"
    }

    override fun visit(arr: AstNode.ArrayLiteral): String {
        val builder = StringBuilder()
        builder.append("[")
        for (el in arr.elements) builder.append(el.accept(this)).append(", ")
        builder.append("]")
        return builder.toString()
    }
}
