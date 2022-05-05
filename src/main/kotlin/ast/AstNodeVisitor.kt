package ast

/**
 * The visitor-interface for the AST
 *
 * [Visitor Pattern](https://en.wikipedia.org/wiki/Visitor_pattern)
 */
interface AstNodeVisitor<T> {

    fun visit(binary: AstNode.Binary): T
    fun visit(literal: AstNode.Literal): T
    fun visit(variable: AstNode.Variable): T
    fun visit(group: AstNode.Group): T
    fun visit(unary: AstNode.Unary): T
    fun visit(funcCall: AstNode.FunctionCall): T
    fun visit(exprStmt: AstNode.ExpressionStatement): T
    fun visit(function: AstNode.Function): T
    fun visit(program: AstNode.Program): T
    fun visit(clazz: AstNode.ArtClass): T
    fun visit(print: AstNode.Print): T
    fun visit(block: AstNode.Block): T
    fun visit(varDec: AstNode.VariableDeclaration): T
    fun visit(varAssign: AstNode.Assignment): T
    fun visit(loop: AstNode.Loop): T
    fun visit(ifStmt: AstNode.If): T
    fun visit(whileStmt: AstNode.While): T
    fun visit(returnStmt: AstNode.Return): T
    fun visit(varInc: AstNode.VarAssignShorthand): T
    fun visit(varInc: AstNode.VarIncrement): T
    fun visit(get: AstNode.Get): T
    fun visit(cont: AstNode.Continue): T
    fun visit(breac: AstNode.Break): T
    fun visit(constructorCall: AstNode.ConstructorCall): T
    fun visit(field: AstNode.Field): T
    fun visit(arr: AstNode.ArrayCreate): T
    fun visit(arr: AstNode.ArrayLiteral): T
    fun visit(arr: AstNode.ArrGet): T
    fun visit(arr: AstNode.ArrSet): T
    fun visit(yieldArrow: AstNode.YieldArrow): T
    fun visit(nul: AstNode.Null): T

}
