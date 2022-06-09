import onj.*

/**
 * @author Simon Berthold
 *
 * TestSuite is an array of tests which can all be performed at once.
 * There are predefined Suites in testSuites.onj which can be selected with byName or byId. Otherwise it is possible to
 * create custom suites.
 */
class TestSuite private constructor(val tests: List<Test>) {

    /**
     * runs one test after the other, prints summary at the end
     * returns whether tests succeeded
     */
    fun run(): Boolean {
        println(
            """
            ${Ansi.blue}
            ····························
            ·· TestSuite is being run ··
            ····························
            ${Ansi.reset}
            """.trimIndent()
        )
        tests.forEach { it.test() }
        println(
            """
            ${Ansi.blue}
            ·······················
            ·· TestSuite Summary ··
            ·······················
            ${Ansi.reset}
            """.trimIndent()
        )

        var suiteFailed = false
        tests.forEach {
            if (it.succeeded) println("${Ansi.green}[✓] Test ${it.testFileName} succeeded${Ansi.reset}")
            else {
                suiteFailed = true
                println("${Ansi.red}[✘] Test ${it.testFileName} failed${Ansi.reset}")
            }
        }
        return !suiteFailed
    }

    @Override
    override fun toString(): String {
        return this.tests.toString()
    }

    companion object {

//        fun byId(id: Int): TestSuite { //TODO: necessary?
//            val testSuiteOnj = readTestSuitesOnj()
//                .value
//                .filter { (it as OnjObject).get<Long>("id").toInt() == id }
//                .apply { if (isEmpty()) throw RuntimeException("no test with id '$id'") }[0]
//            val hashMap = testSuiteOnj.value as HashMap<*, *>
//            val tests = hashMap["tests"] as OnjArray
//            val testSuite: List<Test> = tests
//                .value
//                .stream()
//                .map {
//                    Test(it.toString().substring(1, it.toString().lastIndexOf('\'')))
//                }
//                .toList()
//            return TestSuite(testSuite)//TODO: better code
//        }

        val testSuitesOnjSchema: OnjSchema by lazy { OnjParser.parseSchema(testSuitesOnjSchemaString) }

        val testSuitesOnj: OnjArray by lazy {
            val testSuitesOnj = OnjParser.parseFile("src/testSuites.onj")

            testSuitesOnjSchema.assertMatches(testSuitesOnj)

            testSuitesOnj as OnjObject
            testSuitesOnj.get<OnjArray>("testSuites")
        }

        fun byName(name: String): TestSuite {
            val testSuiteOnj = testSuitesOnj.value
                .filter { (it as OnjObject).get<String>("name").toString() == name }
                .apply { if (isEmpty()) throw RuntimeException("no test with name '$name'") }[0]
            val hashMap = testSuiteOnj.value as HashMap<*, *>
            val tests = hashMap["tests"] as OnjArray
            val testSuite: List<Test> = tests
                .value
                .stream()
                .map { onjToTest(it as OnjObject) }
                .toList()
            return TestSuite(testSuite)//TODO: better code
        }

        fun getAll(): List<TestSuite> {
            val suites: MutableList<TestSuite> = mutableListOf()
            for (suite in testSuitesOnj.value) {
                suite as OnjObject
                suites.add(TestSuite(
                    suite.get<OnjArray>("tests")
                        .value
                        .map { onjToTest(it as OnjObject) }
                ))
            }
            return suites
        }

        fun custom(tests: List<Test>): TestSuite {
            return TestSuite(tests)
        }

        private fun onjToTest(obj: OnjObject): Test {
            val srcFile = obj.get<String>("srcFile")
            val outputFile = obj.get<String?>("outputFile")
            val expectCompileFailure = obj.get<Boolean>("expectCompileFailure")
            val expectRuntimeFailure = obj.get<Boolean>("expectRuntimeFailure")
            return Test(srcFile, outputFile, expectCompileFailure, expectRuntimeFailure)
        }


        const val testSuitesOnjSchemaString = """
            
            !test = {
                srcFile: string
                outputFile: string?
                expectCompileFailure: boolean
                expectRuntimeFailure: boolean
            }
           
           
            !testSuite = {
                id: int
                name: string
                description: string
                tests: !test[*]
            }
            
            testSuites: !testSuite[*]
            
        """
    }
}