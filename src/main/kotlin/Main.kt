import ast.ASTPrinter
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

    val statements = Parser.parse(tokens)

    println("----------------AST-----------------")
    statements.forEach { println(it.accept(ASTPrinter())) }
    println("------------------------------------\n\n")
}