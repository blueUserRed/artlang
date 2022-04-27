package ast

import Datatype

interface SyntheticNode

object SyntheticAst {

    val objetClass: SyntClass = SyntClass(
        "Object",
        staticFuncs = mutableListOf(),
        funcs = mutableListOf(),
        staticFields = mutableListOf(),
        fields = mutableListOf(),
        null,
        "java/lang/Object"
    )

    init {
        objetClass.funcs.add(
            SyntFunction(
                "toString",
                FunctionDescriptor(mutableListOf(), Datatype.Str()),
                isStatic = false,
                isTopLevel = false,
                isPrivate = false,
                objetClass
            )
        )
    }

    class SyntClass(
        override val name: String,
        staticFuncs: MutableList<Function>,
        funcs: MutableList<Function>,
        staticFields: MutableList<Field>,
        fields: MutableList<Field>,
        override val extends: ArtClass?,
        override val jvmName: String
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

    fun addSyntheticTreeParts(root: AstNode.Program) {
        root.classes.add(objetClass)
    }

}
