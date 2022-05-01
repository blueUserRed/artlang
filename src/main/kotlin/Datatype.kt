import ast.AstNode
import ast.FunctionDescriptor
import ast.SyntheticAst

abstract class Datatype(val kind: Datakind) {

    abstract val descriptorType: String

    abstract fun compatibleWith(other: Datatype): Boolean

    abstract override fun equals(other: Any?): Boolean
    abstract override fun toString(): String

    fun matches(vararg kinds: Datakind): Boolean {
        for (kind in kinds) if (kind == this.kind) return true
        return false
    }

    class Integer : Datatype(Datakind.INT) {
        override val descriptorType: String = "I"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Integer::class
        override fun toString(): String = "int"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.INT)
    }
    class Byte : Datatype(Datakind.BYTE) {
        override val descriptorType: String = "B"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Byte::class
        override fun toString(): String = "byte"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.BYTE)
    }
    class Short : Datatype(Datakind.SHORT) {
        override val descriptorType: String = "S"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Short::class
        override fun toString(): String = "short"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.SHORT)
    }
    class Long : Datatype(Datakind.LONG) {
        override val descriptorType: String = "J"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Long::class
        override fun toString(): String = "long"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.LONG)
    }

    class Float : Datatype(Datakind.FLOAT) {
        override val descriptorType: String = "F"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Float::class
        override fun toString(): String = "float"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.FLOAT)
    }
    class Double : Datatype(Datakind.DOUBLE) {
        override val descriptorType: String = "D"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Double::class
        override fun toString(): String = "double"
        override fun compatibleWith(other: Datatype): Boolean = other.kind in arrayOf(Datakind.ERROR, Datakind.DOUBLE)
    }


    class Bool : Datatype(Datakind.BOOLEAN) {
        override val descriptorType: String = "Z"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Bool::class
        override fun toString(): String = "bool"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.BOOLEAN, Datakind.ERROR)
        }
    }

    class Str: Datatype.Object(SyntheticAst.stringClass) {
//        override val descriptorType: String = "Ljava/lang/String;"
//        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Str::class
//        override fun toString(): String = "str"
//        override fun compatibleWith(other: Datatype): Boolean {
//            return other.kind in arrayOf(Datakind.STRING, Datakind.ERROR)
//        }
    }

    class Void : Datatype(Datakind.VOID) {
        override val descriptorType: String = "V"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Void::class
        override fun toString(): String = "void"
        override fun compatibleWith(other: Datatype): Boolean = false
    }

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

        fun lookupFunc(name: String, sig: List<Datatype>): AstNode.Function? {
            for (func in clazz.funcs) if (func.name == name && func.functionDescriptor.isCompatibleWith(sig)) return func
            if (clazz.extends != null) return Object(clazz.extends!!).lookupFunc(name, sig)
            return null
        }

        fun lookupFuncExact(name: String, sig: FunctionDescriptor): AstNode.Function? {
            for (func in clazz.funcs) if (func.name == name && func.functionDescriptor.matches(sig)) return func
            if (clazz.extends != null) return Object(clazz.extends!!).lookupFuncExact(name, sig)
            return null
        }

        fun lookupField(name: String): AstNode.Field? {
            for (field in clazz.fields) if (field.name == name) return field
            if (clazz.extends != null) return Object(clazz.extends!!).lookupField(name)
            return null
        }

    }

    class StatClass(val clazz: AstNode.ArtClass) : Datatype(Datakind.STAT_CLASS) {

        override val descriptorType: String = "Ljava/lang/Class;"

        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other::class == StatClass::class && clazz === (other as StatClass).clazz
        }

        fun lookupFunc(name: String, sig: List<Datatype>): AstNode.Function? {
            for (func in clazz.staticFuncs) if (func.name == name && func.functionDescriptor.isCompatibleWith(sig)) return func
            return null
        }

        fun lookupFuncExact(name: String, sig: FunctionDescriptor): AstNode.Function? {
            for (func in clazz.staticFuncs) if (func.name == name && func.functionDescriptor.matches(sig)) return func
            return null
        }

        fun lookupField(name: String): AstNode.Field? {
            for (field in clazz.staticFields) if (field.name == name) return field
            return null
        }

        fun isSuperClassOf(other: AstNode.ArtClass): Boolean {
            if (other.extends == null) return false
            if (other.extends === clazz) return true
            return isSuperClassOf(other.extends!!)
        }

        override fun toString(): String = "Class<${clazz.name}>"

        override fun compatibleWith(other: Datatype): Boolean = false
    }

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
    }

    class ErrorType : Datatype(Datakind.ERROR) {
        override val descriptorType: String = "--ERROR--"
        override fun compatibleWith(other: Datatype): Boolean = true
        override fun toString(): String = "--ERROR--"
        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other::class == ErrorType::class
        }
    }

}

enum class Datakind {
    INT, LONG, BYTE, SHORT, FLOAT, DOUBLE, STRING, VOID, BOOLEAN, OBJECT,
    ARRAY,
    ERROR,
    STAT_CLASS
}
