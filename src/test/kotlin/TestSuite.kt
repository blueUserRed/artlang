import onj.*
import java.util.HashMap
import java.util.stream.Stream

/**
 * @author Simon Berthold
 *
 * TestSuite is an array of tests which can all be performed at once.
 * There are predefined Suites in testSuites.csv which can be selected with byName or byId. Otherwise it is possible to
 * create custom suites.
 */
class TestSuite private constructor(val tests: List<Test>) {

    @Override
    override fun toString(): String {
        return this.tests.toString()
    }

    companion object {
        private val testSuitesCSV: List<String> = emptyList()

        fun byId(id: Int): TestSuite {
            val testSuiteOnj = readTestSuitesOnj().value
                    .filter { (it as OnjObject).get<Long>("id").toInt() == id }.get(0)
            val message = testSuiteOnj.value as LinkedHashMap<*, *>
            val tests = message["tests"] as OnjArray
            val testSuites : List<Test> = tests.value.stream().map { Test(it.toString()) }.toList()
            println(testSuites)
//            println(message.toString() +"\n"+ tests)
//            println((testSuiteOnj as OnjObject).get<OnjString>("tests"))

            return TestSuite(TODO())
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