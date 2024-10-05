package testpredict

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

object FileManager {

    private var prefix = "./"
    private const val postfix = ".json"

    fun putToFile(fileName: String, dataJson: String) {
        val matchesFile = File(prefix + fileName + postfix)

        FileOutputStream(matchesFile).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { osw ->
                BufferedWriter(osw).use { bf ->
                    var initial = 0
                    while (true) {
                        if (initial + 1000 > dataJson.length) {
                            val text = dataJson.substring(initial, dataJson.length)
                            bf.write(text)
                            break
                        } else {
                            val text = dataJson.substring(initial, initial + 1000)
                            bf.write(text)
                            initial += 1000
                        }
                    }
                }
            }
        }
    }

    fun readFromFile(fileName: String): String {
        return try {
            return File(prefix + fileName + postfix).inputStream().readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
