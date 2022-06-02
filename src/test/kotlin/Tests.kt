import errors.ErrorPool
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.readText

fun main() {
//    val test = Test("FizzBuzz.art", false)
//    val test = Test("HelloWorld.art")
//    test.test()

    val ts: TestSuite = TestSuite.custom(listOf(Test("HelloWorld.art"), Test("BlockExpressions.art")))
//    println(ts.toString())
    ts.run()


}

/**
 * Test a single .art file form WORKING_DIR/src which has an equally named sample output in WORKING_DIR/sampleOutputs
 * The .art file gets compiled, then it is run and the output is compared to the sample output.
 * The meanwhile generated .jar file is placed in WORKING_DIR/out
 */
class Test(val testFileName: String, private val printOutput: Boolean = true) {

    private var hasbeentested = false

    private var sampleOutput = try {
        Paths.get("$sampleOutDir/$testFileName").readText()
    } catch (e: java.nio.file.NoSuchFileException) {
        println("The sample output for this test does not exits. It can therefore not be compared with the programs output.")
        null
    }
    var succeeded: Boolean = false

    fun test() {
        val args = arrayOf("compile", "$srcDir$testFileName")
        Main.main(args)
        val output = runProgram()
        println()
        if (printOutput) println(output)
        if (sampleOutput == output && !ErrorPool.hasErrors()) {
            println(Ansi.green + "$testFileName => Test succeeded [\u2713]")
            succeeded = true
        } else {
            println(Ansi.red + "$testFileName => Test failed [\u2718]")
            println("Program Output: «$output»")
            if (sampleOutput != null) println("Sample Output: «$sampleOutput»") else {
                println("Sample output does not exist.")
            }
        }
        println(Ansi.reset)
        hasbeentested = true
    }

    private fun runProgram(): String {
        val jarFile = testFileName.split(".")[0] + ".jar"
        println(jarFile)
        val builder = ProcessBuilder("java", "-jar", jarFile)
        builder.directory(File(outDir))

        builder.redirectError(ProcessBuilder.Redirect.INHERIT)

        val process = builder.start()
        val output = java.lang.StringBuilder()
        process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { output.append(it).append("\n") }
        process.waitFor()
        if (process.exitValue() != 0) throw RuntimeException("running jar file failed")
        return output.toString()
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
                "\nHas been tested? " + this.hasbeentested
    }

}