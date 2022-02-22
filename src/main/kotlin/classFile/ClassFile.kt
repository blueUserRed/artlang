package classFile

import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class ClassFileBuilder {

    private val constantPool: MutableList<ConstantInfo> = mutableListOf()

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

    fun build(path: String) {

        val thisClassIndex = classInfo(utf8Info(thisClass))
        val superClassIndex = classInfo(utf8Info(superClass))

        val out = Files.newOutputStream(Paths.get(path))

        out.write(magicNumber)
        out.write(version)

        out.write(Utils.getLastTwoBytes(constantPool.size + 1))
        out.write(getConstantPoolAsBytes())

        out.write(getAccessFlagsAsBytes())
        println(getAccessFlagsAsBytes().contentToString())

        out.write(Utils.getLastTwoBytes(thisClassIndex))
        out.write(Utils.getLastTwoBytes(superClassIndex))

        out.write(Utils.getLastTwoBytes(0)) //interface count //TODO: Unhardcode
        out.write(Utils.getLastTwoBytes(0)) //field count
        out.write(Utils.getLastTwoBytes(0)) //methods count
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





