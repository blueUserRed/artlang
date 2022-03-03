import ast.ASTPrinter
import compiler.Compiler
import parser.Parser
import passes.ControlFlowChecker
import passes.TypeChecker
import passes.VariableResolver
import tokenizer.Tokenizer
import java.io.File
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Main {

    @JvmStatic
    fun main(args: Array<String>) {

        val file = "test/src/Test.art"
        val outdir = "src/main/res/test/out"

        val code = Utils.readFile(file)
        println("----------------code----------------")
        println(code)
        println("------------------------------------\n\n")

        val tokens = Tokenizer.tokenize(code, file)

        println("---------------tokens---------------")
        tokens.forEach(::println)
        println("------------------------------------\n\n")

        val program = Parser.parse(tokens)

        println("----------------AST-----------------")
        println(program.accept(ASTPrinter()))
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

        println("Compiling into dir: $outdir/tmp")
        Compiler().compile(program, "$outdir/tmp", "Test")
        println("done\n")

        println("creating jar Test.jar")
        createJar(
            Paths.get("$outdir/tmp").toAbsString(),
            "Test.jar",
            "Test\$\$ArtTopLevel"
        )
        println("done")
        //TODO: remove tmp dir
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

    private fun Path.toAbsString(): String = this.toAbsolutePath().toString()
}
