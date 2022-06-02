import java.io.File
import parser.Parser
import ast.AstPrinter
import ast.SyntheticAst
import errors.ErrorPool
import compiler.Compiler
import passes.*
import java.nio.file.Path
import tokenizer.Tokenizer
import java.io.IOException
import java.nio.file.Paths
import java.nio.file.Files
import java.lang.RuntimeException

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        val instructions = Settings.parseArgs(args)
        if (Settings.verbose) println("\n")

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

        val tokens = doTask("Tokenizer") { Tokenizer.tokenize(code, file) }

        if (Settings.printTokens) {
            println("---------------tokens---------------")
            tokens.forEach(::println)
            println("------------------------------------\n\n")
        }

        val program = doTask("Parser") { Parser().parse(tokens, code) }

        SyntheticAst.addSyntheticTreeParts(program)
        SyntheticAst.addSwingTreeParts(program) // this adds a few classes and function from java.swing, only wrote it for the ticTacToe-demo to work

        if (Settings.printAst) {
            println("----------------AST-----------------")
            println(program.accept(AstPrinter()))
            println("------------------------------------\n\n")
        }

        doTask("variable resolver") { program.accept(VariableResolver()) }
        doTask("type checker") { program.accept(TypeChecker()) }
        doTask("control-flow checker") { program.accept(ControlFlowChecker()) }
        doTask("inheritance checker") { program.accept(InheritanceChecker()) }
        doTask("jvm variable resolver") { program.accept(JvmVariableResolver()) }

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
            println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size} error(s), skipping Compilation${Ansi.reset}")
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

        if (!Settings.verbose) println("Compiled successfully")

        stopwatch.stop()
        println("\nTook ${stopwatch.time}ms in total")
    }

    /**
     * executes a part of the compilation process and prints output to the console if [Settings.verbose] is set
     */
    private fun <T> doTask(name: String, task: () -> T): T {
        if (Settings.verbose) println("running $name")
        val errsBefore = ErrorPool.errors.size
        val retVal: T
        val time = Stopwatch.time { retVal = task() }
        if (ErrorPool.errors.size != errsBefore) {
            if (Settings.verbose) {
                println("${Ansi.yellow}Accumulated ${ErrorPool.errors.size - errsBefore} error(s)${Ansi.reset}")
            }
        }
        if (Settings.verbose) println("done in ${time}ms\n")
        return retVal
    }

    /**
     * uses the
     * [jar-utility](https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jar.html#:~:text=J%20and%20option\).-,DESCRIPTION,applications%20into%20a%20single%20archive.)
     * included in the jdk to generate a jar-file from a directory
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
