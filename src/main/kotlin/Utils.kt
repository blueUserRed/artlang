import java.nio.file.Paths

object Utils {

    fun readFile(file: String): String {
        return Paths.get("src/main/res/$file").toFile().readText(Charsets.UTF_8)
    }

    fun arrayConcat(vararg byteArrs: ByteArray): ByteArray {
        var length = 0
        for (byteArr in byteArrs) length += byteArr.size

        val result = ByteArray(length)

        var cur = 0
        for (byteArr in byteArrs) {
            byteArr.copyInto(result, cur)
            cur += byteArr.size
        }
        return result
    }

    fun getLastTwoBytes(i: Int): ByteArray = arrayOf(
        (i and 0xFF00).toByte(),
        (i and 0x00FF).toByte()
    ).toByteArray()

    fun getIntAsBytes(i: Int): ByteArray = arrayOf(
        ((i shr 24) and 0xFF).toByte(),
        ((i shr 16) and 0xFF).toByte(),
        ((i shr 8) and 0xFF).toByte(),
        ((i shr 0) and 0xFF).toByte(),
    ).toByteArray()

}