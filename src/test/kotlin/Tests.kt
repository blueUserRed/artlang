import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val test = Test("HelloWorld.art")
    test.test()
}

class Test(private val testfileName: String, private val printOutput: Boolean = true) {

    private var sampleOutput = Files.readAllLines(Paths.get("$sampleOutDir/$testfileName")).toString().substring(1)

    fun test() {
        sampleOutput = sampleOutput.substring(0, sampleOutput.length - 1) //TODO beautify
        val args = arrayOf("compile", "$srcDir$testfileName", "-v")
        Main.main(args)
        val output = runProgram()
        if (printOutput) println(output)
        if (sampleOutput == output) {
            println("kotlin.Test s")
            println(Ansi.green + "kotlin.Test succeeded [\u2713]")
        } else {
            println(Ansi.red + "kotlin.Test failed [\u2718]")
            // TODO compare is to should - Output
            println("Program Output: «$output»")
            println("Sample Output: «$sampleOutput»")
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