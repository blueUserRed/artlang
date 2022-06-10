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
    for (test in suite.tests) if (test.outputFileName != null) {
        test.test()
        File("sampleOutputs/${test.outputFileName}").writeText(test.programOutput ?: "")
    }
}

/**
 * Test a single .art file form WORKING_DIR/src which has an equally named sample output in WORKING_DIR/sampleOutputs
 * The .art file gets compiled, then it is run and the output is compared to the sample output.
 * The meanwhile generated .jar file is placed in WORKING_DIR/out
 */
class Test(

        /**
         *  name of the file that is being tested
         */
        val testFileName: String,

        /**
         *  name of the .jar file to generate
         */
        val outputFileName: String?,

        /**
         * whether the program expects failure during compiling
         */
        val expectCompileFailure: Boolean,

        /**
         * whether the program expects failure during runtime
         */
        val expectRuntimeFailure: Boolean,

        /**
         * whether to print the output of the program
         */
        private val printOutput: Boolean = false
) {

    /**
     * Saves whether the Test has been tested yet
     */
    private var hasBeenTested = false

    /**
     * reads the sample output file and saves it to [sampleOutput]
     */
    private val sampleOutput: String? = run {
        if (outputFileName == null) null else try {
            Paths.get("$sampleOutDir/$outputFileName").readText()
        } catch (e: java.nio.file.NoSuchFileException) {
            println("Unable to read file $outputFileName")
            null
        }
    }

    /**
     * Saves if the test succeeded
     */
    var succeeded: Boolean = false

    /**
     * The output of the program is saved here
     */
    var programOutput: String? = null

    /**
     * Throws an error if the Test expects failure while compiling and runtime
     */
    init {
        if (expectCompileFailure && expectRuntimeFailure) {
            throw IllegalArgumentException("cant expect compiletime and runtime failure simultaneously")
        }
    }

    /**
     * Test the file, optionally prints additional information and whether the test succeeded
     */
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

    /**
     * Runs the program and returns the output if there were no errors else null
     */
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
        /**
         * Directory where .jar files are placed
         */
        const val outDir = "out/"

        /**
         * Directory where .art file [testFileName] is located
         */
        const val srcDir = "src/"

        /**
         * Directory where the correct outputs for the .art files are located
         */
        const val sampleOutDir = "sampleOutputs/"
    }

    /**
     * Returns a String with all important information about the Test
     */
    override fun toString(): String {
        return "File to test: " + this.testFileName +
                "\n Print Output? " + this.printOutput +
                "\nTest succeeded? " + this.succeeded +
                "\nHas been tested? " + this.hasBeenTested +
                "\nExpecting a compile failure? " + this.expectCompileFailure +
                "\nExpecting a runtime failure? " + this.expectRuntimeFailure
    }

}