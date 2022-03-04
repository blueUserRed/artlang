package ast

interface AstNodeVisitor<T> {

    fun visit(exp: AstNode.Binary): T
    fun visit(exp: AstNode.Literal): T
    fun visit(exp: AstNode.Variable): T
    fun visit(exp: AstNode.Group): T
    fun visit(exp: AstNode.Unary): T
    fun visit(exp: AstNode.FunctionCall): T
    fun visit(exp: AstNode.WalrusAssign): T
    fun visit(stmt: AstNode.ExpressionStatement): T
    fun visit(stmt: AstNode.Function): T
    fun visit(stmt: AstNode.Program): T
    fun visit(stmt: AstNode.ArtClass): T
    fun visit(stmt: AstNode.Print): T
    fun visit(stmt: AstNode.Block): T
    fun visit(stmt: AstNode.VariableDeclaration): T
    fun visit(stmt: AstNode.VariableAssignment): T
    fun visit(stmt: AstNode.Loop): T
    fun visit(stmt: AstNode.If): T
    fun visit(stmt: AstNode.While): T
    fun visit(stmt: AstNode.Return): T
    fun visit(stmt: AstNode.VarIncrement): T

}
