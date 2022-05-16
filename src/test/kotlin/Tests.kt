import ast.AstPrinter
import parser.Parser
import passes.ControlFlowChecker
import passes.TypeChecker
import passes.VariableResolver
import tokenizer.Tokenizer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

fun main() {
//    TestsDeprecated.test(Paths.get("${programDir}HelloWorld.art"), Paths.get("${sampleOutDir}HelloWorld.art"))
    val test = Test("HelloWorld.art")
    test.test()
}

class Test(val testfileName: String, val printOutput: Boolean = true) {

    var sampleOutput = Files.readAllLines(Paths.get("$sampleOutDir/$testfileName")).toString().substring(1)

    fun test() {
        sampleOutput = sampleOutput.substring(0, sampleOutput.length - 1) //TODO beautify
        Main.main(arrayOf("compile", "$srcDir$testfileName", "-v"))
        val output = runProgram()
        if (printOutput) println(output)
        if (sampleOutput == output) {
            println("Test s")
            println(Ansi.green + "Test succeeded [\u2713]")
        } else {
            println(Ansi.red + "Test failed [\u2718]")
            // TODO compare is to should - Output
            println(output)
            println(sampleOutput)
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

object TestsDeprecated {
    // Working directory set to artlang/src/test/res
    val programDir = "src/"
    val sampleOutDir = "sampleOutputs/"
    val outDir = "out/"

    var testNum: Int = 0

    /**
     * Tests whether //TODO
     */
    fun test(artFile: Path, sampleOutput: Path) {
        Main.main(arrayOf("compile", "src/FizzBuzz.art", "-v"))
        println(runProgram())
    }

    fun testDeprecated(artFile: Path, sampleOutput: Path) {
        val code = Paths.get("$artFile").toFile().readText(Charsets.UTF_8)
        val tokens = Tokenizer.tokenize(code, artFile.toString())
        val program = Parser().parse(tokens, code)
        testNum++

        println("----------------code----------------")
        println(code)
        println("------------------------------------\n\n")

        println("---------------tokens---------------")
        tokens.forEach(::println)
        println("------------------------------------\n\n")

        println("----------------AST-----------------")
        println(program.accept(AstPrinter()))
        println("------------------------------------\n\n")

        println("running variable resolver")
        program.accept(VariableResolver())
        println("done\n")

        println("running type checker")
        program.accept(TypeChecker())
        println("done\n")

        println("running controlFlow checker")
        program.accept(ControlFlowChecker())
        println("done\n")

        println("Compiling into dir: $outDir/tmp")
        compiler.Compiler().compileProgram(program, "$outDir/tmp", "Test")
        println("done\n")

        println("Creating jar Test.jar")
        createJar(
                Paths.get("$outDir/tmp").toAbsString(),
                "Test.jar",
                "Test\$\$ArtTopLevel"
        )
        println("done")

        println("Running program")
        println("Program output: ")
        val out = runProgram()
        println(Ansi.yellow + out + Ansi.reset)
        println("Sample output: ")
        val sampleOutput = Files.readAllLines(
                Paths.get(sampleOutDir
                        + artFile.name.split(".")[0])
        ).toString().removePrefix("[").removeSuffix("]")
        println(Ansi.yellow
                + sampleOutput
                + Ansi.reset
                + "\n"
        )

        testEquals(out, sampleOutput)

        //TODO: remove tmp dir
//    Files.walk(Paths.get("$outDir/tmp/")).skip(1).forEach(Files::delete)
//    Files.delete(Paths.get("$outDir/tmp"))

    }

    fun testEquals(out: String, sampleOut: String) {
        if (out == sampleOut) {
            println("\u001B[32mTest $testNum [\u2713]")
        } else {
            println("\u001B[31mTest $testNum [\u2718]")
            println("Output ------------------------------------ Sample Output")
            println(showComparison(out, sampleOut))
        }
        print("\u001B[37m")
    }

    private fun showComparison(s1: String, s2: String): String {
        TODO("Not yet implemented")
    }

    private fun createJar(fromDir: String, name: String, entryPoint: String) {
        val builder = ProcessBuilder("jar", "cfe", "../$name", entryPoint)

        Files
                .walk(Paths.get(fromDir))
                .filter { path -> !path.toFile().isDirectory }
                .forEach { path -> builder.command().add(Paths.get(fromDir).relativize(path).toString()) }

        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.directory(File(fromDir))
        println(builder.command())
        val process = builder.start()
        process.waitFor()
        if (process.exitValue() != 0) throw RuntimeException("creating jar failed")
    }

    private fun runProgram(): String {
        var builder = ProcessBuilder("java", "-jar", "FizzBuzz.jar")
//        print(Utils.Ansi.yellow)
//        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
//        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.directory(File(outDir))
//        println(builder.command() + Utils.Ansi.reset)

        val process = builder.start()
        val output = java.lang.StringBuilder()
        process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { output.append(it).append("\n") }
        process.waitFor()
        if (process.exitValue() != 0) throw RuntimeException("running jar file failed")
        return output.toString()
    }

    private fun Path.toAbsString(): String = this.toAbsolutePath().toString()


}
