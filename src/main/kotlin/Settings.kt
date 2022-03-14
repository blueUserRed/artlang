object Settings {

    var leaveTmp: Boolean = false
        private set

    var verbose: Boolean = false
        private set

    var printAst: Boolean = false
        private set

    var printTokens: Boolean = false
        private set

    var printCode: Boolean = false
        private set

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