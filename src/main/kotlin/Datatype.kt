import ast.AstNode

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
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.INT, Datakind.FLOAT, Datakind.ERROR)
        }
    }
    class Byte : Datatype(Datakind.BYTE) {
        override val descriptorType: String = "B"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Byte::class
        override fun toString(): String = "byte"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.BYTE, Datakind.SHORT, Datakind.INT, Datakind.FLOAT, Datakind.ERROR)
        }
    }
    class Short : Datatype(Datakind.SHORT) {
        override val descriptorType: String = "S"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Short::class
        override fun toString(): String = "short"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.SHORT, Datakind.INT, Datakind.FLOAT, Datakind.ERROR)
        }
    }
    class Long : Datatype(Datakind.LONG) {
        override val descriptorType: String = "J"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Long::class
        override fun toString(): String = "long"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.LONG, Datakind.ERROR)
        }
    }

    class Float : Datatype(Datakind.FLOAT) {
        override val descriptorType: String = "F"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Float::class
        override fun toString(): String = "float"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.FLOAT, Datakind.ERROR)
        }
    }
    class Double : Datatype(Datakind.DOUBLE) {
        override val descriptorType: String = "D"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Double::class
        override fun toString(): String = "double"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.DOUBLE, Datakind.ERROR)
        }
    }


    class Bool : Datatype(Datakind.BOOLEAN) {
        override val descriptorType: String = "Z"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Bool::class
        override fun toString(): String = "bool"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.BOOLEAN, Datakind.ERROR)
        }
    }

    class Str: Datatype(Datakind.STRING) {
        override val descriptorType: String = "Ljava/lang/String;"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Str::class
        override fun toString(): String = "str"
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.STRING, Datakind.ERROR)
        }
    }

    class Void : Datatype(Datakind.VOID) {
        override val descriptorType: String = "V"
        override fun equals(other: Any?): Boolean = if (other == null) false else other::class == Void::class
        override fun toString(): String = "void"
        override fun compatibleWith(other: Datatype): Boolean = false
    }

    class Object(val name: String, val clazz: AstNode.ArtClass) : Datatype(Datakind.OBJECT) {
        override val descriptorType: String = "L$name;"
        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other::class == Object::class && name == (other as Object).name
        }
        override fun toString(): String = name
        override fun compatibleWith(other: Datatype): Boolean {
            return other.kind in arrayOf(Datakind.OBJECT, Datakind.ERROR)
        }

        fun lookupFunc(name: String, sig: List<Datatype>): AstNode.Function? {
            for (func in clazz.funcs) if (func.name == name && func.functionDescriptor.matches(sig)) {
                return func
            }
            return null
        }
    }

    class StatClass(val clazz: AstNode.ArtClass) : Datatype(Datakind.STAT_CLASS) {

        override val descriptorType: String = "Ljava/lang/Class;"

        override fun equals(other: Any?): Boolean {
            return if (other == null) false else other::class == StatClass::class && clazz === (other as StatClass).clazz
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
