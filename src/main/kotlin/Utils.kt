import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Utils {

    object Ansi {
        val reset = "\u001B[0m"
        val yellow = "\u001B[33m"
        val red = "\u001B[31m"
        val blue = "\u001B[34m"
        val white = "\u001B[37m"
    }

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
        ((i shr 8) and 0x00FF).toByte(),
        (i and 0x00FF).toByte()
    ).toByteArray()

    fun getIntAsBytes(i: Int): ByteArray = arrayOf(
        ((i shr 24) and 0xFF).toByte(),
        ((i shr 16) and 0xFF).toByte(),
        ((i shr 8) and 0xFF).toByte(),
        ((i shr 0) and 0xFF).toByte(),
    ).toByteArray()

    fun getShortAsBytes(s: Short): ByteArray = arrayOf(
        ((s.toInt() shr 8) and 0xFF).toByte(),
        ((s.toInt() shr 0) and 0xFF).toByte(),
    ).toByteArray()

    fun zipDirectory(directory: String, target: String) {
        val src = Paths.get(directory).toFile()
        val zipOutput = ZipOutputStream(Files.newOutputStream(Paths.get(target)))
        Files
            .walk(src.toPath())
            .filter { file -> !Files.isDirectory(file) }
            .filter { file -> !file.fileName.startsWith(".")}
            .forEach { path ->
                val zipEntry = ZipEntry("${src.toPath().relativize(path)}")
                zipOutput.putNextEntry(zipEntry)
                Files.copy(path, zipOutput)
                zipOutput.closeEntry()
            }
        zipOutput.close()
    }
}