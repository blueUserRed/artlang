import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Object containing utility functions
 */
object Utils {

    /**
     * concatenates multiple arrays
     */
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

    /**
     * returns the last two bytes of an int
     */
    fun getLastTwoBytes(i: Int): ByteArray = arrayOf(
        ((i shr 8) and 0x00FF).toByte(),
        (i and 0x00FF).toByte()
    ).toByteArray()

    /**
     * returns an int as byte array
     */
    fun getIntAsBytes(i: Int): ByteArray = arrayOf(
        ((i shr 24) and 0xFF).toByte(),
        ((i shr 16) and 0xFF).toByte(),
        ((i shr 8) and 0xFF).toByte(),
        ((i shr 0) and 0xFF).toByte(),
    ).toByteArray()

    /**
     * returns a long as byte array
     */
    fun getLongAsBytes(l: Long): ByteArray = arrayOf(
        ((l shr 56) and 0xFF).toByte(),
        ((l shr 48) and 0xFF).toByte(),
        ((l shr 40) and 0xFF).toByte(),
        ((l shr 32) and 0xFF).toByte(),
        ((l shr 24) and 0xFF).toByte(),
        ((l shr 16) and 0xFF).toByte(),
        ((l shr 8) and 0xFF).toByte(),
        ((l shr 0) and 0xFF).toByte(),
    ).toByteArray()

    /**
     * returns a short as byte array
     */
    fun getShortAsBytes(s: Short): ByteArray = arrayOf(
        ((s.toInt() shr 8) and 0xFF).toByte(),
        ((s.toInt() shr 0) and 0xFF).toByte(),
    ).toByteArray()


    /**
     * converts a string to modified utf8 encoding, which is used by the class-file format for encoding string-constants
     */
    fun strToModifiedUTF8(str: String): ByteArray {

        // https://stackoverflow.com/questions/57419723/how-to-convert-the-java-modified-utf-8-to-the-regular-utf-8-and-back

        // how can something as simple as encoding text be such a mess?

        val chars = str.toCharArray()
        val bytes = mutableListOf<Byte>()

        for (c in chars) {
            if (c.code in 1..0x7F) {
                bytes.add(c.code.toByte())
            }
            else if (c.code > 0x7FF) {
                bytes.add((0xE0 or (((c.code shr 12) and 0xF))).toByte())
                bytes.add((0x80 or (((c.code shr 6) and 0x3F))).toByte())
                bytes.add((0x80 or (c.code and 0x3F)).toByte())
            } else {
                bytes.add((0xc0 or (((c.code shr 6) and 0x1F))).toByte())
                bytes.add((0x80 or (c.code and 0x3F)).toByte())
            }
        }
        return bytes.toByteArray()
    }
}

/**
 * a class that can contain either one value or another
 */
sealed class Either<out A, out B> {
    class Left<out T> (val value: T) : Either<T, Nothing>()
    class Right<out T> (val value: T) : Either<Nothing, T>()
}

/**
 * Object containing constants for ANSI-colors
 */
object Ansi {
    const val reset = "\u001B[0m"
    const val yellow = "\u001B[33m"
    const val red = "\u001B[31m"
    const val blue = "\u001B[34m"
    const val white = "\u001B[37m"
    const val green = "\u001B[32m"
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
