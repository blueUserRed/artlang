import ast.AstPrinter
import compiler.Compiler
import parser.Parser
import passes.ControlFlowChecker
import passes.TypeChecker
import passes.VariableResolver
import tokenizer.Tokenizer
import java.io.File
import java.io.IOException
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Main {

    @JvmStatic
    fun main(args: Array<String>) {

        val instructions = Settings.parseArgs(args)

        if (instructions.isEmpty()) {
            println("Please specify a subcommand")
            printHelp()
            return
        }

        when (instructions[0]) {
            "help" -> {
                printHelp()
                return
            }
            "compile" -> {
                if (instructions.size < 2) {
                    println("Please supply a filename")
                    printHelp()
                    return
                }
                compile(instructions[1])
            }
        }
    }

    private fun compile(file: String) {
        val outDir = "./out"

        val code: String
        val fileName: String

        try {
            val srcFile = Paths.get(file).toFile()
            fileName = srcFile.nameWithoutExtension
            code = srcFile.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            println("couldn't access file $file")
            return
        }

        if (Settings.printCode) {
            println("----------------code----------------")
            println(code)
            println("------------------------------------\n\n")
        }

        val tokens = Tokenizer.tokenize(code, file)

        if (Settings.printTokens) {
            println("---------------tokens---------------")
            tokens.forEach(::println)
            println("------------------------------------\n\n")
        }

        val program = Parser.parse(tokens)

        if (Settings.printAst) {
            println("----------------AST-----------------")
            println(program.accept(AstPrinter()))
            println("------------------------------------\n\n")
        }

        if (Settings.verbose) println("running variable resolver")
        program.accept(VariableResolver())
        if (Settings.verbose) println("done\n")

        if (Settings.verbose) println("running type checker")
        program.accept(TypeChecker())
        if (Settings.verbose) println("done\n")

        if (Settings.verbose) println("running controlFlow checker")
        program.accept(ControlFlowChecker())
        if (Settings.verbose) println("done\n")

        if (Settings.printAst) {
            println("------------revised AST-------------")
            println(program.accept(AstPrinter()))
            println("------------------------------------\n\n")
        }

        Files.walk(Paths.get("$outDir/tmp/")).skip(1).forEach(Files::delete)
        Files.delete(Paths.get("$outDir/tmp"))

        if (Settings.verbose) println("Compiling into dir: $outDir/tmp")
        Compiler().compile(program, "$outDir/tmp", fileName)
        if (Settings.verbose) println("done\n")

        if (Settings.verbose) println("creating jar $fileName.jar")
        createJar(
            Paths.get("$outDir/tmp").toAbsString(),
            "$fileName.jar",
            "$fileName\$\$ArtTopLevel"
        )
        if (Settings.verbose) println("done")

        if (!Settings.leaveTmp) {
            Files.walk(Paths.get("$outDir/tmp/")).skip(1).forEach(Files::delete)
            Files.delete(Paths.get("$outDir/tmp"))
        }
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
        val process = builder.start()
        process.waitFor()
        if (process.exitValue() != 0) throw RuntimeException("creating jar failed")
    }

    private fun printHelp() {
        println("Usage: artlang <subcommand> <options>")
        println("Subcommands:")
        println("compile <file>     Compile a file")

    }

    private fun Path.toAbsString(): String = this.toAbsolutePath().toString()
}
