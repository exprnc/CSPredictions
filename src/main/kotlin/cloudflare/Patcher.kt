package com.exprnc.cspredictions.cloudflare

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

class Patcher(private val _driverExecutablePath: String?) {
    @Throws(Exception::class)
    fun Auto() {
        if (!isBinaryPatched) {
            patchExe()
        }
    }

    @get:Throws(Exception::class)
    private val isBinaryPatched: Boolean
        get() {
            if (_driverExecutablePath == null) {
                throw RuntimeException("driverExecutablePath is required.")
            }
            val file = File(_driverExecutablePath)

            var br: BufferedReader? = null
            try {
                br = BufferedReader(FileReader(file, StandardCharsets.ISO_8859_1))

                var line: String? = null

                while ((br.readLine().also { line = it }) != null) {
                    if (line!!.contains("undetected chromedriver")) {
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (br != null) {
                    try {
                        br.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return false
        }

    private fun patchExe(): Int {
        val linect = 0
        val replacement = genRandomCdc()
        var file: RandomAccessFile? = null
        try {
            file = RandomAccessFile(_driverExecutablePath, "rw")

            val buffer = ByteArray(1024)
            val stringBuilder = StringBuilder()
            var read: Long = 0
            while (true) {
                read = file.read(buffer, 0, buffer.size).toLong()
                if (read == 0L || read == -1L) {
                    break
                }
                stringBuilder.append(String(buffer, 0, read.toInt(), StandardCharsets.ISO_8859_1))
            }
            val content = stringBuilder.toString()
            val pattern = Pattern.compile("\\{window\\.cdc.*?;\\}")
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                val group = matcher.group()
                val newTarget = StringBuilder("{console.log(\"undetected chromedriver 1337!\"}")
                val k = group.length - newTarget.length
                for (i in 0 until k) {
                    newTarget.append(" ")
                }
                val newContent = content.replace(group, newTarget.toString())

                file.seek(0)
                file.write(newContent.toByteArray(StandardCharsets.ISO_8859_1))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (file != null) {
                try {
                    file.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return linect
    }

    private fun genRandomCdc(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        val random = Random()
        /*
        char[] cdc = new char[26];

        for(int i=0;i<26;i++){
            cdc[i] = chars.charAt(random.nextInt(chars.length()));
        }
        for (int i = 4; i <= 6; i++) {
            cdc[cdc.length - i] = Character.toUpperCase(cdc[cdc.length - i]);
        }
        cdc[2] = cdc[0];
        cdc[3] = '_';
        return new String(cdc);

         */
        val cdc = CharArray(27)
        for (i in 0..26) {
            cdc[i] = chars[random.nextInt(chars.length)]
        }
        return String(cdc)
    }
}