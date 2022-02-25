import ast.ASTPrinter
import compiler.Compiler
import parser.Parser
import passes.TypeChecker
import passes.VariableResolver
import tokenizer.Tokenizer

fun main() {

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

    println("Compiling into dir: $outdir")
    Compiler().compile(program, outdir, "Test")
    println("done")



}