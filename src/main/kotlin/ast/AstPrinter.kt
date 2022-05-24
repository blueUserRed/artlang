package ast

import tokenizer.TokenType

/**
 * Converts the AST into a string representation
 */
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
        function as AstNode.FunctionDeclaration
        val builder = StringBuilder("\n")
        for (modifier in function.modifiers) builder.append(modifier.lexeme).append(" ")
        builder.append("fn ").append(function.name).append("()")
        if (function.statements != null) {
            builder.append(" {\n")
            for (s in function.statements!!.statements) builder.append(s.accept(this)).append("\n")
            builder.append("}\n")
        } else builder.append("\n")
        return builder.toString()
    }

    override fun visit(program: AstNode.Program): String {
        val builder = StringBuilder()
        for (field in program.fields) if (field !is SyntheticNode) builder.append(field.accept(this))
        for (func in program.funcs) if (func !is SyntheticNode) builder.append(func.accept(this))
        for (c in program.classes) if (c !is SyntheticNode) builder.append(c.accept(this))
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
        return "(${
            if (varAssign.from != null) "${varAssign.from!!.accept(this)}.${varAssign.name.lexeme}"
            else varAssign.name.lexeme
        } ${
            if (varAssign.isWalrus) ":=" else "="
        } ${varAssign.toAssign.accept(this)})"
    }

    override fun visit(arr: AstNode.ArrGet): String {
        return "${arr.from.accept(this)}[${arr.arrIndex.accept(this)}]"
    }

    override fun visit(arr: AstNode.ArrSet): String {
        return "${arr.from.accept(this)}[${arr.arrIndex.accept(this)}] = ${arr.to.accept(this)}"
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
        clazz as AstNode.ClassDefinition
        val builder = StringBuilder()
        builder.append("\nclass ${clazz.name} ")
        if (clazz.extendsToken != null) builder.append(": ${clazz.extendsToken.lexeme} ")
//        if (clazz.interfaces.isNotEmpty()) {
//            builder.append("~ ")
//            for (int in clazz.interfaces) builder.append(int.lexeme).append(" ")
//        }
        builder.append("{\n")
        for (field in clazz.fields) builder.append(field.accept(this)).append("\n")
        for (field in clazz.staticFields) builder.append(field.accept(this))
        for (func in clazz.staticFuncs) builder.append(func.accept(this))
        for (func in clazz.funcs) builder.append(func.accept(this))
        for (c in clazz.constructors) builder.append(c.accept(this))
        builder.append("}\n")
        return builder.toString()
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
        return "(new ${constructorCall.clazz.name})"
    }

    override fun visit(field: AstNode.Field): String {
        field as AstNode.FieldDeclaration
        val builder = StringBuilder()
        for (modifier in field.modifiers) builder.append(modifier.lexeme).append(" ")
        if (field.isConst) builder.append("const ")
        builder
            .append(field.name)
            .append(" = ")
            .append(field.initializer.accept(this))
            .append("\n")
        return builder.toString()
    }

    override fun visit(arr: AstNode.ArrayCreate): String {
        val builder = StringBuilder()
        builder
            .append("new ")
            .append(arr.of)
        for (amount in arr.amounts) {
            builder
                .append("[")
                .append(amount.accept(this))
                .append("]")
        }
        return builder.toString()
    }

    override fun visit(arr: AstNode.ArrayLiteral): String {
        val builder = StringBuilder()
        builder.append("[")
        for (el in arr.elements) builder.append(el.accept(this)).append(", ")
        builder.append("]")
        return builder.toString()
    }

    override fun visit(yieldArrow: AstNode.YieldArrow): String {
        return "(=> ${yieldArrow.expr.accept(this)})"
    }

    override fun visit(varInc: AstNode.VarAssignShorthand): String {
        return "(${
            if (varInc.from == null) varInc.name.lexeme
            else "${varInc.from!!.accept(this)}.${varInc.name.lexeme}"
        } ${varInc.operator.lexeme} ${varInc.toAdd.accept(this)})"
    }

    override fun visit(nul: AstNode.Null): String {
        return "null"
    }

    override fun visit(convert: AstNode.TypeConvert): String {
        return "(${convert.toConvert.accept(this)}.${convert.to.lexeme})"
    }

    override fun visit(supCall: AstNode.SuperCall): String {
        val builder = StringBuilder()
        builder
            .append("(super.")
            .append(supCall.name.lexeme)
        for (arg in supCall.arguments) {
            builder
                .append(" ")
                .append(arg.accept(this))
        }
        builder.append(")")
        return builder.toString()
    }


    override fun visit(cast: AstNode.Cast): String {
        return "(${cast.toCast.accept(this)} as ${cast.to})"
    }

    override fun visit(instanceOf: AstNode.InstanceOf): String {
        return "(${instanceOf.toCheck.accept(this)} as ${instanceOf.checkTypeNode})"
    }

    override fun visit(constructor: AstNode.Constructor): String {
        constructor as AstNode.ConstructorDeclaration
        val builder = StringBuilder()
        builder.append("constructor(")
        for (arg in constructor.arguments) {
            builder
                .append(arg.first.lexeme)
                .append(": ")
                .append(arg.second.toString())
                .append(", ")
        }
        builder.append(")")
        if (constructor.body != null) {
            builder.append(" {\n")
            for (s in constructor.body!!.statements) builder.append(s.accept(this)).append("\n")
            builder.append("}\n")
        } else builder.append("\n")
        return builder.toString()
    }
}
