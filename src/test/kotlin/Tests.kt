import errors.ErrorPool
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.readLines
import kotlin.io.path.readText

fun main() {
    //val test = Test("FizzBuzz.art", false)
    val test = Test("HelloWorld.art")
    test.test()
}

class Test(private val testfileName: String, private val printOutput: Boolean = true) {

    private var sampleOutput = try {
        Paths.get("$sampleOutDir/$testfileName").readText()
    } catch (e: java.nio.file.NoSuchFileException) {
        println("The sample output for this test does not exits. It can therefore not be compared with the programs output.")
        null
    }

    fun test() {
        val args = arrayOf("compile", "$srcDir$testfileName")
        Main.main(args)
        val output = runProgram()
        println()
        if (printOutput) println(output)
        if (sampleOutput == output && !ErrorPool.hasErrors()) {
            println(Ansi.green + "$testfileName => Test succeeded [\u2713]")
        } else {
            println(Ansi.red + "$testfileName => Test failed [\u2718]")
            // TODO compare is to should - Output
            println("Program Output: «$output»")
            if (sampleOutput != null) println("Sample Output: «$sampleOutput»") else {
                println("Sample output does not exist.")
            }
        }
        println(Ansi.reset)
    }

    private fun runProgram(): String {
        //TODO: warum ist pwd …/res/out/
        val jarFile = testfileName.split(".")[0] + ".jar"
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

}