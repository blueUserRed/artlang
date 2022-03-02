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
    assertEq("test", "test")
    assertEq("test", "tes1t")
}

var testNum: Int = 0

/**
 * Tests whether a1 equals a2
 */
fun assertEq(a1: Any, a2: Any) {
    testNum++
    if (a1 == a2){
        println("\u001B[32mTest $testNum [\u2713]")
    } else println("\u001B[31mTest $testNum [\u2718]")
    print("\u001B[37m")
}
