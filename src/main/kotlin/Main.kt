import ast.ASTPrinter
import compiler.Compiler
import parser.Parser
import tokenizer.Tokenizer

fun main() {

    val file = "test/src/Test.art"

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

    val outdir = "src/main/res/test/out"
    Compiler().compile(program, outdir, "Test")

    println("Compiling into dir: $outdir")
    println("done")

}