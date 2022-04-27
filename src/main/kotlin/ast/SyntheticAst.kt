package ast

import Datatype

interface SyntheticNode

class SyntClass(
    override val name: String,
    staticFuncs: MutableList<Function>,
    funcs: MutableList<Function>,
    staticFields: MutableList<Field>,
    fields: MutableList<Field>,
    override val extends: ArtClass?
) : AstNode.ArtClass(staticFuncs, funcs, staticFields, fields), SyntheticNode {

    override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

    override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")
}

class SyntFunction(
    override val name: String,
    override val functionDescriptor: FunctionDescriptor,
    override val isStatic: Boolean,
    override val isTopLevel: Boolean,
    override val isPrivate: Boolean,
    override var clazz: ArtClass?
) : AstNode.Function(), SyntheticNode {

    override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

    override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")
}

class SyntField(
    override val name: String,
    override val isPrivate: Boolean,
    override val isStatic: Boolean,
    override val isTopLevel: Boolean,
    override val fieldType: Datatype,
    override val isConst: Boolean,
    override var clazz: ArtClass?
) : AstNode.Field(), SyntheticNode {

    override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

    override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")

}
