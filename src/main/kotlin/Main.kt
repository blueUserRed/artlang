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

}