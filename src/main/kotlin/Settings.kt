/**
 * Object containing the settings that are set via commandline arguments
 */
object Settings {

    /**
     * if true the tmp-directory will not be deleted
     */
    var leaveTmp: Boolean = false
        private set

    /**
     * if true extra output is printed
     */
    var verbose: Boolean = false
        private set

    /**
     * if true the abstract Syntax Tree is printed
     */
    var printAst: Boolean = false
        private set

    /**
     * if true the tokens are printed
     */
    var printTokens: Boolean = false
        private set

    /**
     * if true the code is printed
     */
    var printCode: Boolean = false
        private set

    /**
     * filters out the options from the command and sets the options accordingly
     * @param args the program arguments
     * @return the program arguments without the options
     */
    fun parseArgs(args: Array<String>): Array<String> {
        val newArgs = mutableListOf<String>()
        for (arg in args) {
            if (!arg.startsWith("-")) {
                newArgs.add(arg)
                continue
            }
            when (arg) {
                "-leaveTmp" -> leaveTmp = true
                "-verbose", "-v" -> verbose = true
                "-printAst" -> printAst = true
                "-printTokens" -> printTokens = true
                "-printCode" -> printCode = true
                "-h", "-help", "--help" -> return arrayOf("help")
            }
        }
        return newArgs.toTypedArray()
    }

}