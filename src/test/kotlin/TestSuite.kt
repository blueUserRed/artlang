import onj.*

/**
 * @author Simon Berthold
 *
 * TestSuite is an array of tests which can all be performed at once.
 * There are predefined Suites in testSuites.onj which can be selected with byName or byId. Otherwise it is possible to
 * create custom suites.
 */
class TestSuite private constructor(private val tests: List<Test>) {

    /**
     * runs one test after the other, prints summary at the end
     * returns whether tests succeeded
     */
    fun run(): Boolean {
        tests.forEach{it.test()}
        return false //TODO
    }

    @Override
    override fun toString(): String {
        return this.tests.toString()
    }

    companion object {
        fun byId(id: Int): TestSuite {
            val testSuiteOnj = readTestSuitesOnj().value
                    .filter { (it as OnjObject).get<Long>("id").toInt() == id }[0]
            val linkedHashMap = testSuiteOnj.value as LinkedHashMap<*, *>
            val tests = linkedHashMap["tests"] as OnjArray
            val testSuite: List<Test> = tests.value.stream().map { Test(it.toString().substring(1, it.toString().lastIndexOf('\''))) }.toList()
            testSuite.forEach(::println)
            return TestSuite(testSuite)//TODO: better code
        }

        fun byName(name: String): TestSuite {
            val testSuitesString = readTestSuitesOnj()
            return TestSuite(TODO())
        }

        fun custom(tests: List<Test>): TestSuite {
            return TestSuite(tests)
        }

        private fun readTestSuitesOnj(): OnjArray {
            val testSuitesONJ = OnjParser.parseFile("src/testSuites.onj") as OnjObject
            val testSuites = testSuitesONJ.get<OnjArray>("testSuites")
            return (testSuites)
        }
    }
}

//TODO testduite aufruf TestSuite(ts1, ts2), dass summary output ganzunten also suites kombinieren