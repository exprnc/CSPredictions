package com.exprnc.cspredictions.cloudflare

import java.util.*

object SysUtil {
    val isWindows: Boolean
        /**
         * judge os is Windows
         * @return true：is windows  false：another
         */
        get() {
            val osName = osName
            return osName != null && osName.startsWith("Windows")
        }

    val isMacOs: Boolean
        /**
         * judge os is mac
         * @return true：is mac  false：another
         */
        get() {
            val osName = osName

            return osName != null && osName.startsWith("Mac")
        }

    val isLinux: Boolean
        /**
         * judge os is Linux
         * @return true：is linux  false：another
         */
        get() {
            val osName = osName

            return (osName != null && osName.startsWith("Linux")) || (!isWindows && !isMacOs)
        }

    val osName: String
        /**
         * get os name
         * @return os.name
         */
        get() = System.getProperty("os.name")

    val path: List<String>
        /**
         * get env PATH
         * @return PATHs
         */
        get() {
            val sep = System.getProperty("path.separator")
            val paths = System.getenv("PATH")
            return ArrayList(Arrays.asList(*paths.split(sep.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()))
        }

    /**
     * get one PATH by key
     * @param key PATH key
     * @return PATH value
     */
    fun getString(key: String?): String {
        return System.getenv(key)
    }
}