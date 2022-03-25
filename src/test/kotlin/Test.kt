import Test.programDir
import Test.sampleOutDir
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
    Test.test(Paths.get("${programDir}HelloWorld.art"), Paths.get("${sampleOutDir}HelloWorld"))

}

object Test {


    val programDir = "src/test/res/programs/"
    val sampleOutDir = "src/test/res/sampleOutputs/"
    val outDir = "src/test/res/out/"

    var testNum: Int = 0

    /**
     * Tests whether //TODO
     */
    fun test(artFile: Path, sampleOutput: Path) {
        val code = Paths.get("$artFile").toFile().readText(Charsets.UTF_8)
        val tokens = Tokenizer.tokenize(code, artFile.toString())
        val program = Parser.parse(tokens, code)
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
        var builder = ProcessBuilder("java", "-jar", "Test.jar")
//        print(Utils.Ansi.yellow)
//        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
//        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.directory(File(outDir))
//        println(builder.command() + Utils.Ansi.reset)

        val process = builder.start()
        var output = "";
        process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine {
            output = it
        }
        process.waitFor()
        if (process.exitValue() != 0) throw RuntimeException("running jar file failed")
        return output
    }

    private fun Path.toAbsString(): String = this.toAbsolutePath().toString()


}
