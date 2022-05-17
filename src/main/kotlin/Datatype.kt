import ast.AstNode
import ast.FunctionDescriptor
import ast.SyntheticAst

/**
 * represents a datatype
 * @param kind simpler representation of the type using [Datakind]
 */
abstract class Datatype(val kind: Datakind) {

    /**
     * the descriptor of the type on the jvm
     */
    abstract val descriptorType: String

    /**
     * @return true if this can be assigned to [other]
     */
    abstract fun compatibleWith(other: Datatype): Boolean

    abstract override fun equals(other: Any?): Boolean
    abstract override fun toString(): String

    /**
     * @return true if [kind] matches any kind in [kinds]
     */
    fun matches(vararg kinds: Datakind): Boolean {
        for (kind in kinds) if (kind == this.kind) return true
        return false
    }

    /**
     * represents a primitive Integer
     */
    class Integer : Datatype(Datakind.INT) {
        override val descriptorType: String = "I"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Integer::class
        override fun toString(): String = "int"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.INT)
    }

    /**
     * represents a primitive Byte
     */
    class Byte : Datatype(Datakind.BYTE) {
        override val descriptorType: String = "B"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Byte::class
        override fun toString(): String = "byte"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.BYTE)
    }

    /**
     * represents a primitive Short
     */
    class Short : Datatype(Datakind.SHORT) {
        override val descriptorType: String = "S"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Short::class
        override fun toString(): String = "short"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.SHORT)
    }

    /**
     * represents a primitive Long
     */
    class Long : Datatype(Datakind.LONG) {
        override val descriptorType: String = "J"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Long::class
        override fun toString(): String = "long"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.LONG)
    }

    /**
     * represents a primitive Float
     */
    class Float : Datatype(Datakind.FLOAT) {
        override val descriptorType: String = "F"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Float::class
        override fun toString(): String = "float"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.FLOAT)
    }

    /**
     * represents a primitive Double
     */
    class Double : Datatype(Datakind.DOUBLE) {
        override val descriptorType: String = "D"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Double::class
        override fun toString(): String = "double"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.DOUBLE)
    }

    /**
     * represents a primitive Boolean
     */
    class Bool : Datatype(Datakind.BOOLEAN) {
        override val descriptorType: String = "Z"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Bool::class
        override fun toString(): String = "bool"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.BOOLEAN, Datakind.ERROR)
        }
    }

    /**
     * represents a string, extends Datatype.Object
     */
    class Str : Datatype.Object(SyntheticAst.stringClass)

    /**
     * represents void
     */
    class Void : Datatype(Datakind.VOID) {
        override val descriptorType: String = "V"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Void::class
        override fun toString(): String = "void"
        override fun compatibleWith(other: Datatype): Boolean = false
    }

    /**
     * represents a Object
     * @param clazz the class this object is from
     */
    open class Object(val clazz: AstNode.ArtClass) : Datatype(Datakind.OBJECT) {

        override val descriptorType: String = "L${clazz.jvmName};"

        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other is Object && clazz.jvmName == other.clazz.jvmName
        }

        override fun toString(): String = clazz.name

        override fun compatibleWith(other: Datatype): Boolean {
            if (other.kind != Datakind.OBJECT) return false
            other as Object
            if (other.clazz === clazz) return true
            return StatClass(other.clazz).isSuperClassOf(clazz)
        }

        /**
         * looks up a function on this object that can be called with [sig] and has the name [name]
         * @param origClass not intended to be set; internal param for recursion
         */
        fun lookupFunc(name: String, sig: List<Datatype>, origClass: AstNode.ArtClass? = null): AstNode.Function? {
            for (func in clazz.funcs) if (func.name == name && func.functionDescriptor.isCompatibleWith(sig)) return func
            if (clazz.extends === origClass) return null //protect against inheritance loops
            if (clazz.extends != null) return Object(clazz.extends!!).lookupFunc(name, sig, origClass ?: clazz)
            return null
        }

        /**
         * looks up a function with the *exact* signature [sig]
         */
        fun lookupFuncExact(name: String, sig: FunctionDescriptor, origClass: AstNode.ArtClass? = null): AstNode.Function? {
            for (func in clazz.funcs) if (func.name == name && func.functionDescriptor.matches(sig)) return func
            if (clazz.extends === origClass) return null //protect against inheritance loops
            if (clazz.extends != null) return Object(clazz.extends!!).lookupFuncExact(name, sig, origClass ?: clazz)
            return null
        }

        /**
         * looks up a field with the name [name]
         */
        fun lookupField(name: String): AstNode.Field? {
            for (field in clazz.fields) if (field.name == name) return field
            if (clazz.extends != null) return Object(clazz.extends!!).lookupField(name)
            return null
        }

    }

    /**
     * represents a reference to a class
     */
    class StatClass(val clazz: AstNode.ArtClass) : Datatype(Datakind.STAT_CLASS) {

        override val descriptorType: String = "Ljava/lang/Class;"

        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other::class == StatClass::class && clazz === (other as StatClass).clazz
        }

        /**
         * looks up a function with name [name] that can be called with [sig]
         */
        fun lookupFunc(name: String, sig: List<Datatype>): AstNode.Function? {
            for (func in clazz.staticFuncs) if (func.name == name && func.functionDescriptor.isCompatibleWith(sig)) return func
            return null
        }

        /**
         * looks up a function with the *exact* signature [sig]
         */
        fun lookupFuncExact(name: String, sig: FunctionDescriptor): AstNode.Function? {
            for (func in clazz.staticFuncs) if (func.name == name && func.functionDescriptor.matches(sig)) return func
            return null
        }

        /**
         * looks up a field with name [name]
         */
        fun lookupField(name: String): AstNode.Field? {
            for (field in clazz.staticFields) if (field.name == name) return field
            return null
        }

        /**
         * check if other extends this
         */
        fun isSuperClassOf(other: AstNode.ArtClass): Boolean {
            if (other.extends == null) return false
            if (other.extends === clazz) return true
            return isSuperClassOf(other.extends!!)
        }

        override fun toString(): String = "Class<${clazz.name}>"

        override fun compatibleWith(other: Datatype): Boolean = false
    }

    /**
     * represents an array; for multidimensional arrays, multiple ArrayTypes are nested
     * @param type the type of the contents of the array
     */
    class ArrayType(val type: Datatype) : Datatype(Datakind.ARRAY) {

        override val descriptorType: String = "[${type.descriptorType}"

        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other::class == ArrayType::class && (other as ArrayType).type == type
        }

        override fun toString(): String {
            return "Array<$type>"
        }

        override fun compatibleWith(other: Datatype): Boolean {
            if (other.matches(Datakind.ERROR)) return true
            if (other.matches(Datakind.ARRAY)) return type == (other as ArrayType).type
            return false
        }

        fun countDimensions(): Int {
            if (type !is ArrayType) return 1
            return 1 + type.countDimensions()
        }

        fun getRootType(): Datatype {
            if (type !is ArrayType) return type
            return type.getRootType()
        }

    }

    /**
     * represents an error type. Used by the typechecker when it can't figure out the type of an expression because of an
     * error in the code
     */
    class ErrorType : Datatype(Datakind.ERROR) {
        override val descriptorType: String = "--ERROR--"
        override fun compatibleWith(other: Datatype): Boolean = true
        override fun toString(): String = "--ERROR--"
        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other::class == ErrorType::class
        }
    }

    /**
     * represents 'null'
     */
    class NullType : Datatype(Datakind.NULL) {
        override val descriptorType: String = "Ljava/lang/Object;"

        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.ERROR, Datakind.OBJECT, Datakind.NULL)
        }

        override fun equals(other: Any?): Boolean = other != null && other::class == NullType::class

        override fun toString(): String = "NullType"
    }

}

/**
 * simpler representation of a datatype. Does for example not distinguish between different Objects
 */
enum class Datakind {
    INT, LONG, BYTE, SHORT, FLOAT, DOUBLE, VOID, BOOLEAN, OBJECT,
    ARRAY,
    ERROR, NULL,
    STAT_CLASS
}
