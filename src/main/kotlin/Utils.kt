import java.nio.file.Paths

object Utils {

    fun readFile(file: String): String {
        return Paths.get("src/main/res/$file").toFile().readText(Charsets.UTF_8)
    }

}