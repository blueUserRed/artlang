package ast

import Datatype

/**
 * synthetic parts of the ast implement this interface to indicate that they are synthetic
 */
interface SyntheticNode

/**
 * used to include synthetic classes/fields/functions in the ast. For example used for including things that are not
 * present in the source code (e.g. java.lang.Object). May also be useful in the future, when importing .jar files
 * is implemented
 */
object SyntheticAst {

    /**
     * represent java.lang.Object
     */
    val objectClass: SyntClass = SyntClass(
        "Object",
        staticFuncs = mutableListOf(),
        funcs = mutableListOf(),
        staticFields = mutableListOf(),
        fields = mutableListOf(),
        isAbstract = false,
        null,
        "java/lang/Object"
    )

    val stringClass: SyntClass = SyntClass(
        "\$String",
        staticFuncs = mutableListOf(),
        funcs = mutableListOf(),
        staticFields = mutableListOf(),
        fields = mutableListOf(),
        isAbstract = false,
        objectClass,
        "java/lang/String"
    )

    init {
        objectClass.funcs.addAll(arrayOf(

            SyntFunction(
                "toString",
                FunctionDescriptor(mutableListOf(), Datatype.Str()),
                isStatic = false,
                isTopLevel = false,
                isPrivate = false,
                isAbstract = false,
                objectClass
            ),

            SyntFunction(
                "equals",
                FunctionDescriptor(mutableListOf(Pair("other", Datatype.Object(objectClass))), Datatype.Bool()),
                isStatic = false,
                isTopLevel = false,
                isPrivate = false,
                isAbstract = false,
                objectClass
            )

        ))

    }

    /**
     * represent a synthetic class
     * @param name the name used to refer to this class in the source code
     * @param staticFuncs the static functions present in this class
     * @param fields the non-static functions present in this class
     * @param staticFields the static fields present in this class
     * @param filds the non-static fields present in the class
     * @param extends the superclass of this class; null if this java.lang.Object
     * @param jvmName the name that is used to refer to this on the jvm
     */
    class SyntClass(
        override val name: String,
        staticFuncs: MutableList<Function>,
        funcs: MutableList<Function>,
        staticFields: MutableList<Field>,
        fields: MutableList<Field>,
        override val isAbstract: Boolean,
        override val extends: ArtClass?,
        override val jvmName: String
    ) : AstNode.ArtClass(staticFuncs, funcs, staticFields, fields, listOf()), SyntheticNode {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")
    }

    /**
     * represents a synthetic function
     * @param name the name of this function
     * @param functionDescriptor the descriptor of this function
     * @param isStatic true if this function is static
     * @param isTopLevel true if this function is located in the top-level
     * @param isPrivate true if this function is private
     * @param clazz the class in which this function is defined; null if this function is defined in the top-level
     */
    class SyntFunction(
        override val name: String,
        override val functionDescriptor: FunctionDescriptor,
        override val isStatic: Boolean,
        override val isTopLevel: Boolean,
        override val isPrivate: Boolean,
        override val isAbstract: Boolean,
        override var clazz: ArtClass?
    ) : AstNode.Function(listOf()), SyntheticNode {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")
    }

    /**
     * represents a synthetic field
     * @param name the name of this field
     * @param isPrivate true if the field is private
     * @param isConst true if the field is static
     * @param isTopLevel true if the field is defined in the top-level
     * @param fieldType the datatype that is stored in the field
     * @param isConst true if the field is constant (final)
     * @param clazz the class in which the field is defined; null if the field is defined in the top-level
     */
    class SyntField(
        override val name: String,
        override val isPrivate: Boolean,
        override val isStatic: Boolean,
        override val isTopLevel: Boolean,
        override val fieldType: Datatype,
        override val isConst: Boolean,
        override var clazz: ArtClass?
    ) : AstNode.Field(listOf()), SyntheticNode {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")

    }

    /**
     * adds the synthetic tree to the real tree
     * @param root the real tree
     */
    fun addSyntheticTreeParts(root: AstNode.Program) {
        root.classes.add(objectClass)
        root.classes.add(stringClass)
    }

}
