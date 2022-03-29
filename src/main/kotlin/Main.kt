import ast.AstNode
import ast.AstPrinter
import compiler.Compiler
import errors.ErrorPool
import parser.Parser
import passes.ControlFlowChecker
import passes.TypeChecker
import passes.VariableResolver
import tokenizer.Token
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
        println("\n")
        val instructions = Settings.parseArgs(args)

        if (instructions.isEmpty()) {
            println("Please specify a subcommand\n")
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

    /**
     * compiles a given file, generates console output
     *
     * assumes outdir is ./out
     */
    private fun compile(file: String) {
        val outdir = "./out"

        val code: String
        val fileName: String

        val stopwatch = Stopwatch() //times the whole compilation
        stopwatch.start()

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

        var lastErrors = 0

        var tokens: List<Token>
        val tokenizationTime = Stopwatch.time { tokens = Tokenizer.tokenize(code, file) }
        if (ErrorPool.errors.size != lastErrors) {
            if (Settings.verbose) {
                println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size - lastErrors} error(s)" +
                        " during tokenization${Ansi.reset}")
            }
            lastErrors = ErrorPool.errors.size
        }
        if (Settings.verbose) println("tokenization took ${tokenizationTime}ms\n")

        if (Settings.printTokens) {
            println("---------------tokens---------------")
            tokens.forEach(::println)
            println("------------------------------------\n\n")
        }

        val program: AstNode.Program
        val parseTime = Stopwatch.time { program = Parser.parse(tokens, code) }
        if (ErrorPool.errors.size != lastErrors) {
            if (Settings.verbose) {
                println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size - lastErrors} error(s)" +
                        " during parsing${Ansi.reset}")
            }
            lastErrors = ErrorPool.errors.size
        }
        if (Settings.verbose) println("parsing took ${parseTime}ms\n")

        if (Settings.printAst) {
            println("----------------AST-----------------")
            println(program.accept(AstPrinter()))
            println("------------------------------------\n\n")
        }

        if (Settings.verbose) println("running variable resolver")
        val variableResolverTime = Stopwatch.time { program.accept(VariableResolver()) }
        if (ErrorPool.errors.size != lastErrors) {
            if (Settings.verbose) {
                println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size - lastErrors} errors${Ansi.reset}")
            }
            lastErrors = ErrorPool.errors.size
        }
        if (Settings.verbose) println("done in ${variableResolverTime}ms\n")

        if (Settings.verbose) println("running type checker")
        val typeCheckingTime = Stopwatch.time { program.accept(TypeChecker().apply { srcCode = code }) }
        if (ErrorPool.errors.size != lastErrors) {
            if (Settings.verbose) {
                println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size - lastErrors} errors${Ansi.reset}")
            }
            lastErrors = ErrorPool.errors.size
        }
        if (Settings.verbose) println("done in ${typeCheckingTime}ms\n")

        if (Settings.verbose) println("running controlFlow checker")
        val controlFlowCheckingTime = Stopwatch.time { program.accept(ControlFlowChecker()) }
        if (ErrorPool.errors.size != lastErrors) {
            if (Settings.verbose) {
                println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size - lastErrors} errors${Ansi.reset}")
            }
        }
        if (Settings.verbose) println("done in ${controlFlowCheckingTime}ms\n")

        if (Settings.printAst) {
            println("------------revised AST-------------")
            println(program.accept(AstPrinter()))
            println("------------------------------------\n\n")
        }

        if (Files.exists(Paths.get("$outdir/tmp"))) {
            Files.walk(Paths.get("$outdir/tmp/")).skip(1).forEach(Files::delete)
            Files.delete(Paths.get("$outdir/tmp"))
        }

        if (ErrorPool.hasErrors()) {
            println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size} errors, skipping Compilation${Ansi.reset}")
            stopwatch.stop()
            println("\nTook ${stopwatch.time}ms in total\n")
            ErrorPool.printErrors()
            return
        }

        if (Settings.verbose) println("Compiling into dir: $outdir/tmp")
        val compilationTime = Stopwatch.time { Compiler().compileProgram(program, "$outdir/tmp", fileName) }
        if (Settings.verbose) println("done in ${compilationTime}ms\n")

        if (Settings.verbose) println("creating jar $fileName.jar")
        val jarCreationTime = Stopwatch.time {
            createJar(
                Paths.get("$outdir/tmp").toAbsString(),
                "$fileName.jar",
                "$fileName\$\$ArtTopLevel"
            )
        }
        if (Settings.verbose) println("done in ${jarCreationTime}ms\n")

        if (!Settings.leaveTmp) {
            Files.walk(Paths.get("$outdir/tmp/")).skip(1).forEach(Files::delete)
            Files.delete(Paths.get("$outdir/tmp"))
        }

        stopwatch.stop()
        println("\nTook ${stopwatch.time}ms in total")
    }

    /**
     * uses the
     * [jar-utility](https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jar.html#:~:text=J%20and%20option\).-,DESCRIPTION,applications%20into%20a%20single%20archive.)
     * included in the jdk to generate a jar-file from a direcory
     * @param fromDir the directory containing the .class files which will be bundled to a jar
     * @param name The name of the jar file that is created
     * @param entryPoint the class-file which contains the main method and serves as the entry point to the program
     */
    private fun createJar(fromDir: String, name: String, entryPoint: String) {
        val builder = ProcessBuilder("jar", "cfe", "../$name", entryPoint)

        Files
            .walk(Paths.get(fromDir))
            .filter { path -> !path.toFile().isDirectory }
            .forEach { path -> builder.command().add(Paths.get(fromDir).relativize(path).toString()) }

        //redirect input/output of the jar command to stdout and stdin of this program
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.directory(File(fromDir))
        val process = builder.start()
        process.waitFor()
        if (process.exitValue() != 0) throw RuntimeException("creating jar failed")
    }

    /**
     * prints the help-information for the command
     */
    private fun printHelp() {
        println("Usage: artlang <subcommand> <options>")
        println("")
        println("Subcommands:")
        println("-------------------------------------------------------------------------------------------")
        println("compile <file>     Compile a file")
        println("help               Display this message (hint: any invalid subcommand will also display it)")
        println("")
        println("Options:")
        println("-------------------------------------------------------------------------------------------")
        println("-leaveTmp          Don't delete the tmp directory after compiling")
        println("-verbose (-v)      Print additional output")
        println("-printAst          Print the Abstract Syntax Tree")
        println("-printTokens       Print the Tokens")
        println("-printCode         Print the Code")
        println("-help --help -h    Print this message")
    }

    /**
     * returns the absolute path as a string
     */
    private fun Path.toAbsString(): String = this.toAbsolutePath().toString()
}
