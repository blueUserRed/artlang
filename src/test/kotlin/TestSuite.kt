import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.readText

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
            return TestSuite(listOf())
        }

        fun byName(name: String): TestSuite {
            val testSuitesString = readTestSuitesCSV().map { it[0] }.toTypedArray()
            val testSuites = testSuitesString.map { Test(it/*TODO filename*/, false) }
            return TestSuite(testSuites)
        }

        fun custom(tests: List<Test>): TestSuite {
            return TestSuite(tests)
        }

        private fun readTestSuitesCSV(): Array<Array <String>> {
            val testSuitesCSV: String = Paths.get("testSuites.csv").readText()
            println(testSuitesCSV)
            val lines = testSuitesCSV.split(Regex(Pattern.quote("\n"))).toTypedArray()
            val values = lines.forEach { s: String -> s.split(";") }
        }
    }
}