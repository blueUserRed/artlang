package classFile

import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Paths

class ClassFileBuilder {

    private val constantPool: MutableList<ConstantInfo> = mutableListOf()
    private val methods: MutableList<MethodBuilder> = mutableListOf()
    private val attributes: MutableList<Attribute> = mutableListOf()

    var thisClass: String = ""
    var superClass: String = ""

    var isPublic: Boolean = false
    var isFinal: Boolean = false
    var isSuper: Boolean = false
    var isInterface: Boolean = false
    var isAbstract: Boolean = false
    var isSynthetic: Boolean = false
    var isAnnotation: Boolean = false
    var isEnum: Boolean = false

    fun utf8Info(utf8: String): Int {
        val toAdd = ConstantUTF8Info(utf8)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    fun stringInfo(utf8Index: Int): Int {
        val toAdd = ConstantStringInfo(utf8Index)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    fun classInfo(nameIndex: Int): Int {
        val toAdd = ConstantClassInfo(nameIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    fun integerInfo(i: Int): Int {
        val toAdd = ConstantIntegerInfo(i)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    fun fieldRefInfo(classIndex: Int, nameAndTypeIndex: Int): Int {
        val toAdd = ConstantFieldRefInfo(classIndex, nameAndTypeIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    fun nameAndTypeInfo(nameIndex: Int, descriptorIndex: Int): Int {
        val toAdd = ConstantNameAndTypeInfo(nameIndex, descriptorIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    fun methodRefInfo(classIndex: Int, nameAndTypeIndex: Int): Int {
        val toAdd = ConstantMethodRefInfo(classIndex, nameAndTypeIndex)
        val find = findInfo(toAdd)
        if (find != null) return find + 1
        constantPool.add(toAdd)
        return constantPool.size
    }

    fun addMethod(method: MethodBuilder) = methods.add(method)
    fun addAttribute(attrib: Attribute) = attributes.add(attrib)

    fun build(path: String) {

        val thisClassIndex = classInfo(utf8Info(thisClass))
        val superClassIndex = classInfo(utf8Info(superClass))

        val methodBytes = Utils.arrayConcat(*Array(methods.size) { methods[it].build(this) })

        val out = Files.newOutputStream(Paths.get(path))

        out.write(magicNumber)
        out.write(version)

        out.write(Utils.getLastTwoBytes(constantPool.size + 1))
        out.write(getConstantPoolAsBytes())

        out.write(getAccessFlagsAsBytes())

        out.write(Utils.getLastTwoBytes(thisClassIndex))
        out.write(Utils.getLastTwoBytes(superClassIndex))

        out.write(Utils.getLastTwoBytes(0)) //interface count //TODO: Unhardcode
        out.write(Utils.getLastTwoBytes(0)) //field count

        out.write(Utils.getLastTwoBytes(methods.size))
        out.write(methodBytes)

        out.write(Utils.getLastTwoBytes(0)) //attributes count

        out.close()
    }

    private fun findInfo(info: ConstantInfo): Int? {
        for (i in 0 until constantPool.size) if (constantPool[i] == info) return i
        return null
    }

    private fun getConstantPoolAsBytes(): ByteArray {
        val bytes: Array<ByteArray> = Array(constantPool.size) {
            constantPool[it].toBytes()
        }
        return Utils.arrayConcat(*bytes)
    }

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
        val magicNumber: ByteArray = arrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()).toByteArray()
        val version: ByteArray = arrayOf<Byte>(0x00, 0x00, 0x00, 0x3C).toByteArray()
    }

}

abstract class ConstantInfo(val tag: Byte) {
    abstract fun toBytes(): ByteArray
    abstract override fun equals(other: Any?): Boolean
}

class ConstantUTF8Info(val string: String) : ConstantInfo(1) {

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

class MethodBuilder {

    var name: String = ""
    var descriptor: String = ""

    var maxStack: Int = 0
    var maxLocals: Int = 0

    private val code: MutableList<Byte> = mutableListOf()
    private val attributes: MutableList<Attribute> = mutableListOf()
    private val codeAttributes: MutableList<Attribute> = mutableListOf()
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

    val curCodeOffset: Int
        get() = code.size


    fun build(fileBuilder: ClassFileBuilder): ByteArray {
        removeAttrib<CodeAttribute>() //remove code/StackMapTable Attribute if present, for example if method is build more than once
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

    fun addAttribute(attrib: Attribute) = attributes.add(attrib)
    fun addCodeAttribute(attrib: Attribute) = codeAttributes.add(attrib)
    fun addStackMapFrame(stackMapFrame: StackMapTableAttribute.StackMapFrame) = stackMapFrames.add(stackMapFrame)

    fun emitByteCode(vararg bytes: Byte) {
        for (byte in bytes) code.add(byte)
    }

    fun overwriteByteCode(insertPos: Int, vararg bytes: Byte) {
        for (i in bytes.indices) code[insertPos + i] = bytes[i]
    }

    private inline fun <reified T> removeAttrib() where T : Attribute {
        val iter = attributes.iterator()
        while (iter.hasNext()) if (iter.next() is T) iter.remove()
    }

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

abstract class Attribute {
    abstract fun toBytes(): ByteArray
}

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

class StackMapTableAttribute(val nameIndex: Int) : Attribute() {

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

    abstract class StackMapFrame {
        abstract fun toBytes(): ByteArray
    }

    class SameStackMapFrame(val offset: Int) : StackMapFrame() {

        init {
            if (offset !in 0..63) throw RuntimeException("in StackMapFrame offset must be between 0 an 63")
        }

        override fun toBytes(): ByteArray = arrayOf((offset and 0xFF).toByte()).toByteArray()
    }

    class FullStackMapFrame(val offsetDelta: Int) : StackMapFrame() {

        var locals: MutableList<VerificationTypeInfo> = mutableListOf()
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

    abstract class VerificationTypeInfo(val tag: Byte) {
        open fun toBytes(): ByteArray = arrayOf(tag).toByteArray()

        class Top : VerificationTypeInfo(0)
        class Integer : VerificationTypeInfo(1)
        class Float : VerificationTypeInfo(2)
        class Double : VerificationTypeInfo(3)
        class Long : VerificationTypeInfo(4)
        class Null : VerificationTypeInfo(5)
        class UninitializedThis : VerificationTypeInfo(6)

        class ObjectVariable(val classIndex: Int) : VerificationTypeInfo(7) {
            override fun toBytes(): ByteArray = Utils.arrayConcat(
                arrayOf(tag).toByteArray(),
                Utils.getLastTwoBytes(classIndex)
            )
        }

        class UninitializedVariable(val newOffset: Int) : VerificationTypeInfo(8) {
            override fun toBytes(): ByteArray = Utils.arrayConcat(
                arrayOf(tag).toByteArray(),
                Utils.getLastTwoBytes(newOffset)
            )
        }
    }
}