import errors.ErrorPool
import java.io.File
import java.lang.Exception
import java.nio.file.Paths
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val ts = TestSuite.byName(args[0])
//    recordOutputs(ts)
    ts.run()
}

/**
 * runs all test in a testsuite and saves the output in the corresponding outfile
 *
 * TODO: outputs nonsense in the console
 */
fun recordOutputs(suite: TestSuite) {
    for (test in suite.tests) if (test.outputFielName != null) {
        test.test()
        File("sampleOutputs/${test.outputFielName}").writeText(test.programOutput ?: "")
    }
}

/**
 * Test a single .art file form WORKING_DIR/src which has an equally named sample output in WORKING_DIR/sampleOutputs
 * The .art file gets compiled, then it is run and the output is compared to the sample output.
 * The meanwhile generated .jar file is placed in WORKING_DIR/out
 */
class Test(
    val testFileName: String,
    val outputFielName: String?,
    val expectCompileFailure: Boolean,
    val expectRuntimeFailure: Boolean,
    private val printOutput: Boolean = false
) {

    private var hasBeenTested = false

    private val sampleOutput: String? = run {
        if (outputFielName == null) null else try {
            Paths.get("$sampleOutDir/$outputFielName").readText()
        } catch(e: java.nio.file.NoSuchFileException) {
            println("Unable to read file $outputFielName")
            null
        }
    }

    var succeeded: Boolean = false

    var programOutput: String? = null

    init {
        if (expectCompileFailure && expectRuntimeFailure) {
            throw IllegalArgumentException("cant expect compiletime and runtime failure simultaneously")
        }
    }

    fun test() {
        File(File(testFileName).nameWithoutExtension + ".jar").delete()

        println("---------------------------------------------")
        println("testing: $testFileName")
        if (expectCompileFailure) println("Expecting compilation to fail")
        if (expectRuntimeFailure) println("Expecting program to fail at runtime")

        val args = arrayOf("compile", "$srcDir$testFileName")
        ErrorPool.clear()

        try {
            Main.main(args)
        } catch (e: Exception) { // catch all Exceptions thrown by the compiler
            println("compiler threw an Exception: ")
            println(Ansi.red)
            e.printStackTrace(System.out)
            println(Ansi.reset)
            println("\n")
            println("${Ansi.red}$testFileName => Test failed [\u2718]${Ansi.reset}")
            println("---------------------------------------------")
            hasBeenTested = true
            return
        }
        val hadCompileFailures = ErrorPool.hasErrors()

        programOutput = if (hadCompileFailures) null else runProgram()

        println()
        if (printOutput) println(programOutput)

        val outputsMatch = sampleOutput?.replace("\r", "") /* I love Windows */ == programOutput

        if (hadCompileFailures && expectCompileFailure) {
            println("${Ansi.green}$testFileName => Test succeeded [\u2713]${Ansi.reset}")
            succeeded = true
        } else if (!hadCompileFailures && expectRuntimeFailure && programOutput == null) {
            println("${Ansi.green}$testFileName => Test succeeded [\u2713]${Ansi.reset}")
            succeeded = true
        } else if (!expectRuntimeFailure && !expectCompileFailure && outputsMatch && programOutput != null) {
            println("${Ansi.green}$testFileName => Test succeeded [\u2713]${Ansi.reset}")
            succeeded = true
        } else {
            println("${Ansi.red}$testFileName => Test failed [\u2718]${Ansi.reset}")
            println("Program Output: «\n$programOutput\n»")
            if (sampleOutput != null) println("Sample Output: «\n$sampleOutput\n»") else {
                println("Sample output does not exist.")
            }
        }

        println("---------------------------------------------")
        hasBeenTested = true
    }

    private fun runProgram(): String? {
        try {
            val jarFile = File(testFileName).nameWithoutExtension + ".jar"
            println("running: $jarFile")
            val builder = ProcessBuilder("java", "-jar", jarFile)
            builder.directory(File(outDir))

            builder.redirectError(ProcessBuilder.Redirect.INHERIT)

            val process = builder.start()
            val output = java.lang.StringBuilder()
            process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { output.append(it).append("\n") }
            process.waitFor()
            if (process.exitValue() != 0) return null
            return output.toString()
        } catch (e: Exception) { //TODO: figure out better exception to catch
            return null
        }
    }



    companion object {
        // Working directory set to artlang/src/test/res
        const val outDir = "out/"
        const val srcDir = "src/"
        const val sampleOutDir = "sampleOutputs/"
    }

    override fun toString(): String {
        return "File to test: " + this.testFileName +
                "\n Print Output? " + this.printOutput +
                "\nTest succeeded? " + this.succeeded +
                "\nHas been tested? " + this.hasBeenTested
    }

}