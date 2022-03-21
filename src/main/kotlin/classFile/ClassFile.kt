package classFile

import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * builder that can be used to create a class file
 *
 * [the class file format](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#)
 */
class ClassFileBuilder {

    /**
     * the constant pool of the class file
     */
    private val constantPool: MutableList<ConstantInfo> = mutableListOf()

    /**
     * the methods of the class file
     */
    private val methods: MutableList<MethodBuilder> = mutableListOf()

    /**
     * the attributes of the class
     */
    private val attributes: MutableList<Attribute> = mutableListOf()

    /**
     * the fields of the class
     */
    private val fields: MutableList<Field> = mutableListOf()

    /**
     * the name of the class
     */
    var thisClass: String = ""

    /**
     * the name of the super class
     */
    var superClass: String = ""

    var isPublic: Boolean = false
    var isFinal: Boolean = false
    var isSuper: Boolean = false
    var isInterface: Boolean = false
    var isAbstract: Boolean = false
    var isSynthetic: Boolean = false
    var isAnnotation: Boolean = false
    var isEnum: Boolean = false

    /**
     * adds a utf8Info of a string to the constant pool and returns its index
     */
    fun utf8Info(utf8: String): Int {
        val toAdd = ConstantUTF8Info(utf8)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    /**
     * adds a string info to the constant pool and returns its index
     */
    fun stringInfo(utf8Index: Int): Int {
        val toAdd = ConstantStringInfo(utf8Index)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    /**
     * adds a class info to the constant pool and returns its index
     */
    fun classInfo(nameIndex: Int): Int {
        val toAdd = ConstantClassInfo(nameIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    /**
     * adds an integer info to the constant pool and returns its index
     */
    fun integerInfo(i: Int): Int {
        val toAdd = ConstantIntegerInfo(i)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    /**
     * adds a fieldRef info to the constant pool and returns its index
     */
    fun fieldRefInfo(classIndex: Int, nameAndTypeIndex: Int): Int {
        val toAdd = ConstantFieldRefInfo(classIndex, nameAndTypeIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    /**
     * adds a nameAndType info to the constant pool and returns its index
     */
    fun nameAndTypeInfo(nameIndex: Int, descriptorIndex: Int): Int {
        val toAdd = ConstantNameAndTypeInfo(nameIndex, descriptorIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    /**
     * adds a methodRef info to the constant pool and returns its index
     */
    fun methodRefInfo(classIndex: Int, nameAndTypeIndex: Int): Int {
        val toAdd = ConstantMethodRefInfo(classIndex, nameAndTypeIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    /**
     * adds a [MethodBuilder] to this ClassFileBuilder
     */
    fun addMethod(method: MethodBuilder) = methods.add(method)

    /**
     * adds an Attribute to this ClassFileBuilder
     */
    fun addAttribute(attrib: Attribute) = attributes.add(attrib)

    /**
     * adds a field to this ClassFileBuilder
     */
    fun addField(field: Field) = fields.add(field)

    /**
     * builds the ClassFile
     * @param path the path to which the file is built
     */
    fun build(path: String) {

        val thisClassIndex = classInfo(utf8Info(thisClass))
        val superClassIndex = classInfo(utf8Info(superClass))

        val methodBytes = Utils.arrayConcat(*Array(methods.size) { methods[it].build(this) })
        val fieldBytes = Utils.arrayConcat(*Array(fields.size) { fields[it].toBytes() })

        val out = Files.newOutputStream(Paths.get(path))

        out.write(magicNumber)
        out.write(version)

        out.write(Utils.getLastTwoBytes(constantPool.size + 1))
        out.write(getConstantPoolAsBytes())

        out.write(getAccessFlagsAsBytes())

        out.write(Utils.getLastTwoBytes(thisClassIndex))
        out.write(Utils.getLastTwoBytes(superClassIndex))

        out.write(Utils.getLastTwoBytes(0)) //interface count

        out.write(Utils.getLastTwoBytes(fields.size))
        out.write(fieldBytes)

        out.write(Utils.getLastTwoBytes(methods.size))
        out.write(methodBytes)

        out.write(Utils.getLastTwoBytes(0)) //attributes count

        out.close()
    }

    /**
     * finds an info in the constant pool
     */
    private fun findInfo(info: ConstantInfo): Int? {
        for (i in 0 until constantPool.size) if (constantPool[i] == info) return i
        return null
    }

    /**
     * returns the constant pool in its binary representation
     */
    private fun getConstantPoolAsBytes(): ByteArray {
        val bytes: Array<ByteArray> = Array(constantPool.size) {
            constantPool[it].toBytes()
        }
        return Utils.arrayConcat(*bytes)
    }

    /**
     * converts the access flags to bytes
     */
    private fun getAccessFlagsAsBytes(): ByteArray {
        var flags = 0
        if (isPublic) flags = flags or 0x0001
        if (isFinal) flags = flags or 0x0010
        if (isSuper) flags = flags or 0x0020
        if (isInterface) flags = flags or 0x0200
        if (isAbstract) flags = flags or 0x0400
        if (isSynthetic) flags = flags or 0x1000
        if (isAnnotation) flags = flags or 0x2000
        if (isEnum) flags = flags or 0x4000
        return Utils.getLastTwoBytes(flags)
    }

    companion object {

        /**
         * 0xCAFEBABE
         */
        val magicNumber: ByteArray = arrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()).toByteArray()

        /**
         * the version of the class file
         */
        val version: ByteArray = arrayOf<Byte>(0x00, 0x00, 0x00, 0x3C).toByteArray()
    }

}

/**
 * represents a constant info, contained by the constant pool
 * @param tag the tag of the info structure (see oracle documentation)
 */
abstract class ConstantInfo(val tag: Byte) {

    /**
     * converts the Constant info to Bytes
     */
    abstract fun toBytes(): ByteArray
    abstract override fun equals(other: Any?): Boolean //force subclasses to override equals
}

/**
 * represents a utf8 info
 */
class ConstantUTF8Info(val string: String) : ConstantInfo(1) {

    /**
     * the string as byte array
     */
    val bytes: ByteArray = string.toByteArray(Charsets.UTF_8)

    override fun toBytes(): ByteArray = Utils.arrayConcat(
        arrayOf(tag).toByteArray(),
        Utils.getLastTwoBytes(bytes.size),
        bytes
    )

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as ConstantUTF8Info
        return other.string == this.string
    }
}

/**
 * represents a class info
 */
class ConstantClassInfo(val nameIndex: Int) : ConstantInfo(7) {

    override fun toBytes(): ByteArray = Utils.arrayConcat(
        arrayOf(tag).toByteArray(),
        Utils.getLastTwoBytes(nameIndex)
    )

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as ConstantClassInfo
        return other.nameIndex == this.nameIndex
    }
}

/**
 * represents a methodRef info
 */
class ConstantMethodRefInfo(val classIndex: Int, val nameAndTypeIndex: Int) : ConstantInfo(10) {

    override fun toBytes(): ByteArray = Utils.arrayConcat(
        arrayOf(tag).toByteArray(),
        Utils.getLastTwoBytes(classIndex),
        Utils.getLastTwoBytes(nameAndTypeIndex)
    )

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as ConstantMethodRefInfo
        return other.classIndex == this.classIndex && other.nameAndTypeIndex == this.nameAndTypeIndex
    }
}

/**
 * represents a field info
 */
class ConstantFieldRefInfo(val classIndex: Int, val nameAndTypeIndex: Int) : ConstantInfo(9) {

    override fun toBytes(): ByteArray = Utils.arrayConcat(
        arrayOf(tag).toByteArray(),
        Utils.getLastTwoBytes(classIndex),
        Utils.getLastTwoBytes(nameAndTypeIndex)
    )

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as ConstantFieldRefInfo
        return other.classIndex == this.classIndex && other.nameAndTypeIndex == this.nameAndTypeIndex
    }
}

/**
 * represents a string info
 */
class ConstantStringInfo(val utf8Index: Int) : ConstantInfo(8) {

    override fun toBytes(): ByteArray = Utils.arrayConcat(
        arrayOf(tag).toByteArray(),
        Utils.getLastTwoBytes(utf8Index)
    )

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as ConstantStringInfo
        return other.utf8Index == this.utf8Index
    }
}

/**
 * represents a name and type info
 */
class ConstantNameAndTypeInfo(val nameIndex: Int, val descriptorIndex: Int) : ConstantInfo(12) {

    override fun toBytes(): ByteArray = Utils.arrayConcat(
        arrayOf(tag).toByteArray(),
        Utils.getLastTwoBytes(nameIndex),
        Utils.getLastTwoBytes(descriptorIndex)
    )

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as ConstantNameAndTypeInfo
        return other.nameIndex == this.nameIndex && other.descriptorIndex == this.descriptorIndex
    }
}

/**
 * represents an integer info
 */
class ConstantIntegerInfo(val i: Int) : ConstantInfo(3) {

    override fun toBytes(): ByteArray = Utils.arrayConcat(
        arrayOf(tag).toByteArray(),
        Utils.getIntAsBytes(i)
    )

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) return false
        other as ConstantIntegerInfo
        return other.i == this.i
    }
}

/**
 * builds a method
 */
class MethodBuilder {

    /**
     * the name of the method
     */
    var name: String = ""

    /**
     * the method-descriptor
     */
    var descriptor: String = ""

    /**
     * the maximum stack size
     */
    var maxStack: Int = 0

    /**
     * the maximum amount of locals
     */
    var maxLocals: Int = 0

    /**
     * List containing the ByteCode
     */
    private val code: MutableList<Byte> = mutableListOf()

    /**
     * List containing all attributes
     */
    private val attributes: MutableList<Attribute> = mutableListOf()

    /**
     * List containing all attributes of the code attribute
     */
    private val codeAttributes: MutableList<Attribute> = mutableListOf()

    /**
     * List containing all StackMapFrames
     */
    private val stackMapFrames: MutableList<StackMapTableAttribute.StackMapFrame> = mutableListOf()

    var isPublic: Boolean = false
    var isPrivate: Boolean = false
    var isProtected: Boolean = false
    var isStatic: Boolean = false
    var isFinal: Boolean = false
    var isSynchronized: Boolean = false
    var isBridge: Boolean = false
    var isVarargs: Boolean = false
    var isNative: Boolean = false
    var isAbstract: Boolean = false
    var isStrict: Boolean = false
    var isSynthetic: Boolean = false

    /**
     * returns the current offset into the byteCode
     */
    val curCodeOffset: Int
        get() = code.size


    /**
     * builds the method into a byte-Array
     * @param fileBuilder the filebuilder to which the method belongs
     */
    fun build(fileBuilder: ClassFileBuilder): ByteArray {

        //remove code/StackMapTable Attribute if present, for example if method is build more than once
        removeAttrib<CodeAttribute>()
        removeAttrib<StackMapTableAttribute>()

        val stackMapTable = StackMapTableAttribute(fileBuilder.utf8Info("StackMapTable"))
        stackMapTable.frames = stackMapFrames
        codeAttributes.add(stackMapTable)

        attributes.add(
            CodeAttribute(
                fileBuilder.utf8Info("Code"),
                maxStack, maxLocals,
                code.toByteArray(),
                codeAttributes.toTypedArray()
            )
        )

        val attribBytes = Utils.arrayConcat(*Array(attributes.size) { attributes[it].toBytes() })

        return Utils.arrayConcat(
            getAccessFlagsAsBytes(),
            Utils.getLastTwoBytes(fileBuilder.utf8Info(name)),
            Utils.getLastTwoBytes(fileBuilder.utf8Info(descriptor)),
            Utils.getLastTwoBytes(attributes.size),
            attribBytes
        )
    }

    /**
     * adds an attribute to the method
     */
    fun addAttribute(attrib: Attribute) = attributes.add(attrib)

    /**
     * adds an attribute to code-attribute
     */
    fun addCodeAttribute(attrib: Attribute) = codeAttributes.add(attrib)

    /**
     * adds a new stackMapFrame
     */
    fun addStackMapFrame(stackMapFrame: StackMapTableAttribute.StackMapFrame) = stackMapFrames.add(stackMapFrame)

    /**
     * pops the latest stackMapFrame
     */
    fun popStackMapFrame() = stackMapFrames.removeLast()

    /**
     * adds byte code to the method
     */
    fun emitByteCode(vararg bytes: Byte) {
        for (byte in bytes) code.add(byte)
    }

    /**
     * overwrites the byte code at a specified offset
     * @param insertPos the offset at which the first byte is inserted
     */
    fun overwriteByteCode(insertPos: Int, vararg bytes: Byte) {
        for (i in bytes.indices) code[insertPos + i] = bytes[i]
    }

    /**
     * removes an attribute from [attributes]
     * @param T the type of the attribute
     */
    private inline fun <reified T> removeAttrib() where T : Attribute {
        val iter = attributes.iterator()
        while (iter.hasNext()) if (iter.next() is T) iter.remove()
    }

    /**
     * returns the access flags as bytes
     */
    private fun getAccessFlagsAsBytes(): ByteArray {
        var flags = 0
        if (isPublic) flags = flags or 0x0001
        if (isPrivate) flags = flags or 0x0002
        if (isProtected) flags = flags or 0x0004
        if (isStatic) flags = flags or 0x0008
        if (isFinal) flags = flags or 0x0010
        if (isSynchronized) flags = flags or 0x0020
        if (isBridge) flags = flags or 0x0040
        if (isVarargs) flags = flags or 0x0080
        if (isNative) flags = flags or 0x0100
        if (isAbstract) flags = flags or 0x0400
        if (isStrict) flags = flags or 0x0800
        if (isSynthetic) flags = flags or 0x1000
        return Utils.getLastTwoBytes(flags)
    }
}

/**
 * represents an attribute
 */
abstract class Attribute {

    /**
     * converts the attribute to a byte array
     */
    abstract fun toBytes(): ByteArray
}

/**
 * represents a code-attribute
 * @param nameIndex the index into the constant pool which points to utf8-info-structure containing the string 'Code'
 * @param maxLocals the maximum amount of locals used
 * @param maxStack the maximum stack size needed
 * @param code the byte code
 * @param attributes the attributes contained by this attribute
 */
class CodeAttribute(
    val nameIndex: Int,
    val maxStack: Int,
    val maxLocals: Int,
    val code: ByteArray,
    val attributes: Array<Attribute>
) : Attribute() {

    override fun toBytes(): ByteArray {

        val attribBytes = Utils.arrayConcat(*Array(attributes.size) { attributes[it].toBytes() })

        val b = Utils.arrayConcat(
            code,
            Utils.getLastTwoBytes(0), //Exception Table length
            Utils.getLastTwoBytes(attributes.size),
            attribBytes
        )

        return Utils.arrayConcat(
            Utils.getLastTwoBytes(nameIndex),
            Utils.getIntAsBytes(b.size + 8),
            Utils.getLastTwoBytes(maxStack),
            Utils.getLastTwoBytes(maxLocals),
            Utils.getIntAsBytes(code.size),
            b
        )
    }
}

/**
 * represents the stackMapTable-attribute
 * @param nameIndex the index into the constant pool which points to an utf8-info containing the string 'StackMapTable'
 */
class StackMapTableAttribute(val nameIndex: Int) : Attribute() {

    /**
     * the StackMapFrames that make up this StackMapTable
     */
    var frames: MutableList<StackMapFrame> = mutableListOf()

    override fun toBytes(): ByteArray {
        val frameBytes = Utils.arrayConcat(*Array(frames.size) { frames[it].toBytes() })
        return Utils.arrayConcat(
            Utils.getLastTwoBytes(nameIndex),
            Utils.getIntAsBytes(frameBytes.size + 2),
            Utils.getLastTwoBytes(frames.size),
            frameBytes
        )
    }

    /**
     * represents a StackMapFrame
     */
    abstract class StackMapFrame {

        /**
         * converts the frame to a byte array
         */
        abstract fun toBytes(): ByteArray
    }

    /**
     * the SameStackMapFrame indicates that the stack and the local stayed the same since the last frame
     * @param offset the offset (+1) from the last StackMapFrame. Must be between 0 and 63
     */
    class SameStackMapFrame(val offset: Int) : StackMapFrame() {

        init {
            if (offset !in 0..63) throw RuntimeException("in StackMapFrame offset must be between 0 an 63")
        }

        override fun toBytes(): ByteArray = arrayOf((offset and 0xFF).toByte()).toByteArray()
    }

    /**
     * the FullStackMapFrame contains an explicit offsetDelta (instead of it being encoded in the frame type). Also
     * contains a complete list of the Verification types of the stack and the locals
     * @param offsetDelta the offset from the last StackMapFrame
     */
    class FullStackMapFrame(val offsetDelta: Int) : StackMapFrame() {

        /**
         * the verification types of the locals
         */
        var locals: MutableList<VerificationTypeInfo> = mutableListOf()

        /**
         * the verification types of the stack
         */
        var stack: MutableList<VerificationTypeInfo> = mutableListOf()

        override fun toBytes(): ByteArray = Utils.arrayConcat(
            arrayOf(255.toByte()).toByteArray(),
            Utils.getLastTwoBytes(offsetDelta),
            Utils.getLastTwoBytes(locals.size),
            *Array(locals.size) { locals[it].toBytes() },
            Utils.getLastTwoBytes(stack.size),
            *Array(stack.size) { stack[it].toBytes() }
        )
    }

    /**
     * Verification type infos are used by the jvm to verify the types on the stack and in the locals array; used in
     * StackMapFrames
     */
    abstract class VerificationTypeInfo(val tag: Byte) {

        /**
         * converts the Verification type to a byteArray
         */
        open fun toBytes(): ByteArray = arrayOf(tag).toByteArray()

        /**
         * the Top verification type is used by two word long type (double, long) for the second slot
         */
        class Top : VerificationTypeInfo(0)

        /**
         * represents any numeric non floating-point type except long. Also used for Booleans
         */
        class Integer : VerificationTypeInfo(1)
        class Float : VerificationTypeInfo(2)
        class Double : VerificationTypeInfo(3)
        class Long : VerificationTypeInfo(4)
        class Null : VerificationTypeInfo(5)
        class UninitializedThis : VerificationTypeInfo(6)

        /**
         * represents a Object
         * @param classIndex the index into the constant pool at which a class info corresponding to the type can be
         * found
         */
        class ObjectVariable(val classIndex: Int) : VerificationTypeInfo(7) {
            override fun toBytes(): ByteArray = Utils.arrayConcat(
                arrayOf(tag).toByteArray(),
                Utils.getLastTwoBytes(classIndex)
            )
        }

        /**
         * represents a variable which has not yet been initialized
         * @param newOffset the offset of the instruction at which the new-opcode that initializes the variable
         * is positioned
         */
        class UninitializedVariable(val newOffset: Int) : VerificationTypeInfo(8) {
            override fun toBytes(): ByteArray = Utils.arrayConcat(
                arrayOf(tag).toByteArray(),
                Utils.getLastTwoBytes(newOffset)
            )
        }
    }
}

/**
 * represents a field in the class file
 * @param nameIndex the index into the constant pool at which an utf8 info containing the name of the field can be found
 * @param descriptorIndex the index into the constant pool at which an utf8 info containing the type descriptor of the
 * field can be found
 */
class Field(val nameIndex: Int, val descriptorIndex: Int) {

    var isPublic: Boolean = false
    var isPrivate: Boolean = false
    var isProtected: Boolean = false
    var isStatic: Boolean = false
    var isFinal: Boolean = false
    var isVolatile: Boolean = false
    var isTransient: Boolean = false
    var isSynthetic: Boolean = false
    var isEnum: Boolean = false

    /**
     * List of the attributes of this field
     */
    private val attributes: MutableList<Attribute> = mutableListOf()

    /**
     * adds an attribute to this field
     */
    fun addAttribute(attrib: Attribute) = attributes.add(attrib)

    /**
     * converts this field to a byte array
     */
    fun toBytes(): ByteArray {
        val attribBytes = Array(attributes.size) { attributes[it].toBytes() }
        return Utils.arrayConcat(
            getAccessFlagsAsBytes(),
            Utils.getLastTwoBytes(nameIndex),
            Utils.getLastTwoBytes(descriptorIndex),
            Utils.getLastTwoBytes(attributes.size),
            *attribBytes
        )
    }

    /**
     * returns the access flags as a byte array
     */
    private fun getAccessFlagsAsBytes(): ByteArray {
        var flags = 0
        if (isPublic) flags = flags or 0x0001
        if (isPrivate) flags = flags or 0x0002
        if (isProtected) flags = flags or 0x0004
        if (isStatic) flags = flags or 0x0008
        if (isFinal) flags = flags or 0x0010
        if (isVolatile) flags = flags or 0x0040
        if (isTransient) flags = flags or 0x0080
        if (isSynthetic) flags = flags or 0x1000
        if (isEnum) flags = flags or 0x4000
        return Utils.getLastTwoBytes(flags)
    }

}
