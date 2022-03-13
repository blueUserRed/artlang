package ast

interface AstNodeVisitor<T> {

    fun visit(binary: AstNode.Binary): T
    fun visit(literal: AstNode.Literal): T
    fun visit(variable: AstNode.Variable): T
    fun visit(group: AstNode.Group): T
    fun visit(unary: AstNode.Unary): T
    fun visit(funcCall: AstNode.FunctionCall): T
    fun visit(walrus: AstNode.WalrusAssign): T
    fun visit(exprStmt: AstNode.ExpressionStatement): T
    fun visit(function: AstNode.Function): T
    fun visit(program: AstNode.Program): T
    fun visit(clazz: AstNode.ArtClass): T
    fun visit(print: AstNode.Print): T
    fun visit(block: AstNode.Block): T
    fun visit(varDec: AstNode.VariableDeclaration): T
    fun visit(varAssign: AstNode.VariableAssignment): T
    fun visit(loop: AstNode.Loop): T
    fun visit(ifStmt: AstNode.If): T
    fun visit(whileStmt: AstNode.While): T
    fun visit(returnStmt: AstNode.Return): T
    fun visit(varInc: AstNode.VarIncrement): T
    fun visit(get: AstNode.Get): T
    fun visit(set: AstNode.Set): T
    fun visit(walrus: AstNode.WalrusSet): T
    fun visit(cont: AstNode.Continue): T
    fun visit(breac: AstNode.Break): T
    fun visit(constructorCall: AstNode.ConstructorCall): T
    fun visit(field: AstNode.FieldDeclaration): T
    fun visit(fieldGet: AstNode.FieldReference): T
    fun visit(fieldSet: AstNode.FieldSet): T

}
