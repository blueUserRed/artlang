import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

object Utils {

    object Ansi {
        const val reset = "\u001B[0m"
        const val yellow = "\u001B[33m"
        const val red = "\u001B[31m"
        const val blue = "\u001B[34m"
        const val white = "\u001B[37m"
    }

    fun readFile(file: String): String {
        return Paths.get(file).toFile().readText(Charsets.UTF_8)
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

sealed class Either<out A, out B> {
    class Left<out T> (val value: T) : Either<T, Nothing>()
    class Right<out T> (val value: T) : Either<Nothing, T>()
}

/**
 * stops time
 *
 * if stopped/started paused/unpaused in the wrong order, the StopWatch will not throw an Exception, but may return null
 * when the time-property is accessed
 *
 * (copied from another project)
 */
class Stopwatch {

    private var startTime: Long? = null
    private var endTime: Long? = null

    private var pauseTimes: MutableList<Pair<Long, Long?>> = mutableListOf()

    /**
     * the time in ms the stopwatch has been running for. null if the Stopwatch is not in a clean state. (for example
     * the stopwatch has not been started and stopped yet or the stopwatch is paused and has not been unpaused)
     */
    val time: Long?
        get() {
            if (startTime == null || endTime == null) return null
            var time = endTime!! - startTime!!
            if (time < 0) return null //stopwatch has been stopped before it was started
            for (pauseTime in pauseTimes) {
                if (pauseTime.second == null) continue
                time -= pauseTime.second!! - pauseTime.first
            }
            return time
        }

    /**
     * starts the stopwatch
     */
    fun start() {
        this.startTime = System.currentTimeMillis()
    }

    /**
     * stops the stopwatch
     */
    fun stop() {
        this.endTime = System.currentTimeMillis()
    }

    /**
     * pauses the stopwatch. While it is paused the time does not increase
     */
    fun pause() {
        if (pauseTimes.isNotEmpty() && pauseTimes[pauseTimes.size - 1].second == null) return
        pauseTimes.add(Pair(System.currentTimeMillis(), null))
    }

    /**
     * unpauses the stopwatch
     */
    fun unpause() {
        if (pauseTimes.isEmpty() || pauseTimes[pauseTimes.size - 1].second != null) return
        pauseTimes[pauseTimes.size - 1] = Pair(pauseTimes[pauseTimes.size - 1].first, System.currentTimeMillis())
    }

    companion object {

        /**
         * times how long a task took to execute
         * @param task the task
         * @return the time in ms it took to execute
         */
        @OptIn(ExperimentalContracts::class)
        inline fun time(task: () -> Unit): Long {
            contract {
                callsInPlace(task, InvocationKind.EXACTLY_ONCE)
            }
            val stopwatch = Stopwatch()
            stopwatch.start()
            task()
            stopwatch.stop()
            return stopwatch.time ?: throw RuntimeException("unreachable")
        }

    }
}
