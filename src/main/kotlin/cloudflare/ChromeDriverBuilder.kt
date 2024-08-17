package com.exprnc.cspredictions.cloudflare

import com.alibaba.fastjson2.JSONObject
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.*
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.regex.Pattern

var _userDataDir: String? = null

class ChromeDriverBuilder() {
    //false: use temp user data dir
    private var _keepUserDataDir = true

    //user data dir path

    //chrome exec path
    private var _binaryLocation: String? = null

    //chromeOptions args
    private var args: MutableList<String?>? = mutableListOf()

    /**
     * step1: Patcher build
     * @param driverExecutablePath driver path
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    private fun buildPatcher(driverExecutablePath: String) {
        //patcher
        val patcher = Patcher(driverExecutablePath)

        try {
            patcher.Auto()
        } catch (e: Exception) {
            throw RuntimeException("patcher cdc replace fail")
        }
    }

    /**
     * step2：find a free port and set host in options
     * @param chromeOptions
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    private fun setHostAndPort(chromeOptions: ChromeOptions): ChromeOptions {
        //debug host and port
        var debugHost: String? = null
        var debugPort = -1
        if (args != null && args!!.size > 0) {
            for (arg: String? in args!!) {
                if (arg!!.contains("--remote-debugging-host")) {
                    try {
                        debugHost = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                    } catch (ignored: Exception) {
                    }
                }
                if (arg.contains("--remote-debugging-port")) {
                    try {
                        debugPort = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toInt()
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        if (debugHost == null) {
            debugHost = "127.0.0.1"
            chromeOptions.addArguments("--remote-debugging-host=$debugHost")
        }
        if (debugPort == -1) {
            debugPort = findFreePort()
        }
        if (debugPort == -1) {
            throw RuntimeException("free port not find")
        } else {
            chromeOptions.addArguments("--remote-debugging-port=$debugPort")
        }

        try {
            val experimentalOptions = chromeOptions.javaClass.superclass.getDeclaredField("experimentalOptions")
            experimentalOptions.isAccessible = true
            val experimentals: Map<String, Any?>? = experimentalOptions[chromeOptions] as Map<String, Any?>
            if (experimentals != null && experimentals["debuggerAddress"] != null) {
                return chromeOptions
            }
        } catch (ignored: Exception) {
        }
        chromeOptions.setExperimentalOption("debuggerAddress", "$debugHost:$debugPort")
        return chromeOptions
    }

    /**
     * step3: set user data dir arg for chromeOptions
     * @param chromeOptions
     * @return
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    private fun setUserDataDir(chromeOptions: ChromeOptions?): ChromeOptions? {
        //find user data dir in chromeOptions
        if (args != null) {
            for (arg: String? in args!!) {
                if (arg!!.contains("--user-data-dir")) {
                    try {
                        _userDataDir = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                    } catch (ignored: Exception) {
                    }
                    break
                }
            }
        }
        if (_userDataDir == null || (_userDataDir == "")) {
            //no user data dir in it
//            _keepUserDataDir = false
            try {
                //create temp dir
                _userDataDir = Files.createTempDirectory("undetected_chrome_driver").toString()
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("temp user data dir create fail")
            }
            //add into options
            chromeOptions!!.addArguments("--user-data-dir=$_userDataDir")
        } else {
//            _keepUserDataDir = true
        }
        return chromeOptions
    }

    /**
     * step4: set browser language
     * @param chromeOptions
     * @return
     * @throws RuntimeException
     */
    private fun setLanguage(chromeOptions: ChromeOptions?): ChromeOptions? {
        if (args != null) {
            for (arg: String? in args!!) {
                if (arg!!.contains("--lang=")) {
                    return chromeOptions
                }
            }
        }
        //no argument lang
        val language = Locale.getDefault().language.replace("_", "-")
        chromeOptions!!.addArguments("--lang=$language")
        return chromeOptions
    }

    /**
     * step5: find and set chrome BinaryLocation
     * @param chromeOptions
     * @return
     */
    private fun setBinaryLocation(chromeOptions: ChromeOptions?, binaryLocation: String?): ChromeOptions? {
        var binaryLocation = binaryLocation
        if (binaryLocation == null) {
            try {
                binaryLocation = _getChromePath()
            } catch (e: Exception) {
                throw RuntimeException("chrome not find")
            }
            if ((binaryLocation == "")) {
                throw RuntimeException("chrome not find")
            }
            chromeOptions!!.setBinary(binaryLocation)
        } else {
            chromeOptions!!.setBinary(binaryLocation)
        }
        _binaryLocation = binaryLocation
        return chromeOptions
    }

    /**
     * step 6: suppressWelcome
     * @param chromeOptions
     * @param suppressWelcome
     * @return
     */
    private fun suppressWelcome(chromeOptions: ChromeOptions?, suppressWelcome: Boolean): ChromeOptions? {
        if (suppressWelcome) {
            if (args != null) {
                if (!args!!.contains("--no-default-browser-check")) {
                    chromeOptions!!.addArguments("--no-default-browser-check")
                }
                if (!args!!.contains("--no-first-run")) {
                    chromeOptions!!.addArguments("--no-first-run")
                }
            } else {
                chromeOptions!!.addArguments("--no-default-browser-check", "--no-first-run")
            }
        }
        return chromeOptions
    }

    /**
     * step7, set headless arg
     * @param chromeOptions
     * @param headless
     * @return
     */
    private fun setHeadless(chromeOptions: ChromeOptions?, headless: Boolean): ChromeOptions? {
        if (headless) {
            if (args != null) {
                if (!args!!.contains("--headless=new") || !args!!.contains("--headless=chrome")) {
                    //we consider that the chromedriver version is greater than 108.x.x.x
                    chromeOptions!!.addArguments("--headless=new")
                }
                var hasWindowSize = false
                for (arg: String? in args!!) {
                    if (arg!!.contains("--window-size=")) {
                        hasWindowSize = true
                        break
                    }
                }
                if (!hasWindowSize) {
                    chromeOptions!!.addArguments("--window-size=1920,1080")
                }
                if (!args!!.contains("--start-maximized")) {
                    chromeOptions!!.addArguments("--start-maximized")
                }
                if (!args!!.contains("--no-sandbox")) {
                    chromeOptions!!.addArguments("--no-sandbox")
                }
            } else {
                chromeOptions!!.addArguments("--headless=new")
                chromeOptions.addArguments("--window-size=1920,1080")
                chromeOptions.addArguments("--start-maximized")
                chromeOptions.addArguments("--no-sandbox")
            }
        }
        return chromeOptions
    }

    /**
     * step8, set log level
     * @param chromeOptions
     * @return
     */
    private fun setLogLevel(chromeOptions: ChromeOptions?): ChromeOptions? {
        if (args != null) {
            for (arg: String? in args!!) {
                if (arg!!.contains("--log-level=")) {
                    return chromeOptions
                }
            }
        }
        chromeOptions!!.addArguments("--log-level=0")
        return chromeOptions
    }

    /**
     * step9, add prefs into user dir
     * @param userDataDir
     * @param prefs
     */
    @Throws(RuntimeException::class)
    private fun handlePrefs(userDataDir: String?, prefs: Map<String, Any>) {
        val defaultPath = userDataDir + File.separator + "Default"
        if (!File(defaultPath).exists()) {
            File(defaultPath).mkdirs()
        }

        var newPrefs: MutableMap<String, Any>? = HashMap(prefs)

        val prefsFile = defaultPath + File.separator + "Preferences"
        if (File(prefsFile).exists()) {
            var br: BufferedReader? = null
            try {
                br = BufferedReader(FileReader(prefsFile, StandardCharsets.ISO_8859_1))
                var line: String? = null
                val stringBuilder = StringBuilder()
                while ((br.readLine().also { line = it }) != null) {
                    stringBuilder.append(line)
                    stringBuilder.append("\n")
                }
                newPrefs = JSONObject.parseObject(stringBuilder.toString())
            } catch (e: Exception) {
                throw RuntimeException("Default Preferences dir not find")
            } finally {
                if (br != null) {
                    try {
                        br.close()
                    } catch (ignored: Exception) {
                    }
                }
            }

            try {
                for (pref: Map.Entry<String, Any> in prefs.entries) {
                    undotMerge(pref.key, pref.value, newPrefs)
                }
            } catch (e: Exception) {
                throw RuntimeException("Prefs merge fail")
            }

            try {
                BufferedWriter(FileWriter(prefsFile, StandardCharsets.ISO_8859_1)).use { bw ->
                    bw.write(JSONObject.toJSONString(newPrefs))
                    bw.flush()
                }
            } catch (e: Exception) {
                throw RuntimeException("prefs write to file fail")
            }
        }
    }

    /**
     * step10, fix exit type
     * @param chromeOptions
     * @return
     */
    private fun fixExitType(chromeOptions: ChromeOptions?): ChromeOptions? {
        var reader: BufferedReader? = null
        var writer: BufferedWriter? = null
        try {
            val filePath = _userDataDir + File.separator + "Default" + File.separator + "Preferences"

            reader = BufferedReader(FileReader(filePath, StandardCharsets.ISO_8859_1))

            var line: String? = null
            val jsonStr = StringBuilder()
            while ((reader.readLine().also { line = it }) != null) {
                jsonStr.append(line)
                jsonStr.append("\n")
            }
            reader.close()

            var json = jsonStr.toString()
            val pattern = Pattern.compile("(?<=exit_type\"\":)(.*?)(?=,)")
            val matcher = pattern.matcher(json)
            if (matcher.find()) {
                writer = BufferedWriter(FileWriter(filePath, StandardCharsets.ISO_8859_1))
                json = json.replace(matcher.group(), "null")
                writer.write(json)
                writer.close()
            }
        } catch (ignored: Exception) {
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (ignored: Exception) {
                }
            }
            if (writer != null) {
                try {
                    writer.close()
                } catch (ignored: Exception) {
                }
            }
        }
        return chromeOptions
    }

    /**
     * step11: open chrome by args on new process
     * @param chromeOptions
     * @return
     */
    @Throws(RuntimeException::class)
    private fun createBrowserProcess(chromeOptions: ChromeOptions?, needPrintChromeInfo: Boolean): Process? {
        LoadChromeOptionsArgs(chromeOptions)
        if (args == null) {
            throw RuntimeException("can't open browser, args not found")
        }
        var p: Process? = null
        try {
            args!!.add(0, _binaryLocation)
            p = ProcessBuilder(args).start()
        } catch (e: Exception) {
            throw RuntimeException("chrome open fail")
        }

        val browser = p

        val outputThread = Thread(Runnable {
            try {
                val br = BufferedReader(InputStreamReader(browser!!.inputStream))
                var buff: String? = null
                while ((br.readLine().also { buff = it }) != null) {
                    println(buff)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })

        val errorPutThread = Thread(object : Runnable {
            override fun run() {
                try {
                    val er = BufferedReader(InputStreamReader(browser!!.errorStream))
                    var errors: String? = null
                    while ((er.readLine().also { errors = it }) != null) {
                        println(errors)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })

        if (needPrintChromeInfo) {
            outputThread.start()
            errorPutThread.start()
        }

        return browser
    }

    /**
     * build a undetected chrome driver
     * @param options               chromeOptions           required no prefs args in experiment map, if has prefs, use param prefs
     * @param driverExecutablePath  chrome driver path      not null
     * @param binaryLocation        chrome path
     * @param headless              is headless
     * @param suppressWelcome       suppress welcome
     * @param needPrintChromeInfo   need print chrome process information and errors
     * @param prefs                 add prefs into user-data-dir
     * @return
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    fun build(
        options: ChromeOptions?,
        driverExecutablePath: String?,
        binaryLocation: String?,
        headless: Boolean,
        suppressWelcome: Boolean,
        needPrintChromeInfo: Boolean,
        prefs: Map<String, Any>?
    ): ChromeDriver {
        //Create ChromeDriver
        if (driverExecutablePath == null) {
            throw RuntimeException("driverExecutablePath is required.")
        }
        //step 0, load origin args for options
        LoadChromeOptionsArgs(options)

        //step 1，patcher replace cdc mark
        buildPatcher(driverExecutablePath)

        //chrome options assert not null
        var chromeOptions = options
        if (chromeOptions == null) {
            chromeOptions = ChromeOptions()
        }

        //step 2, set host and port
        chromeOptions = setHostAndPort(chromeOptions)

        //step3, set user data dir
        chromeOptions = setUserDataDir(chromeOptions)

        //step4, set language
        chromeOptions = setLanguage(chromeOptions)

        //step5, set binaryLocation
        chromeOptions = setBinaryLocation(chromeOptions, binaryLocation)

        //step6, suppressWelcome
        chromeOptions = suppressWelcome(chromeOptions, suppressWelcome)

        //step7, set headless arguments
        chromeOptions = setHeadless(chromeOptions, headless)

        //step8, set logLevel
        chromeOptions = setLogLevel(chromeOptions)

        //step9 ,merge prefs
        if (prefs != null) {
            handlePrefs(_userDataDir, prefs)
        }

        //step10, fix exit type
        chromeOptions = fixExitType(chromeOptions)

        //step11, start process
        val browser = createBrowserProcess(chromeOptions, needPrintChromeInfo)

        //step12, make undetectedChrome chrome driver
        val undetectedChromeDriver =
            UndetectedChromeDriver(chromeOptions, headless, _keepUserDataDir, _userDataDir, browser)

        return undetectedChromeDriver
    }

    /**
     * recommend use it
     * @param options
     * @param driverExecutablePath
     * @param binaryLocation
     * @param suppressWelcome
     * @param needPrintChromeInfo
     * @return
     */
    fun build(
        options: ChromeOptions?,
        driverExecutablePath: String?,
        binaryLocation: String?,
        suppressWelcome: Boolean,
        needPrintChromeInfo: Boolean
    ): ChromeDriver {
        //operator headless
        var headless = false
        try {
            val argsField = options!!.javaClass.superclass.getDeclaredField("args")
            argsField.isAccessible = true
            val args = argsField[options] as List<String>
            if (args.contains("--headless") || args.contains("--headless=new") || args.contains("--headless=chrome")) {
                headless = true
            }
        } catch (ignored: Exception) {
        }

        var prefs: Map<String, Any>? = null
        try {
            val argsField = options!!.javaClass.superclass.getDeclaredField("experimentalOptions")
            argsField.isAccessible = true
            val args = argsField[options] as MutableMap<String, Any>
            if (args.containsKey("prefs")) {
                prefs = HashMap(args["prefs"] as Map<String, Any>?)
                args.remove("prefs")
            }
        } catch (ignored: Exception) {
        }

        return this.build(
            options,
            driverExecutablePath,
            binaryLocation,
            headless,
            suppressWelcome,
            needPrintChromeInfo,
            prefs
        )
    }

    /**
     * recommend use it
     * @param options
     * @param driverExecutablePath
     * @param suppressWelcome
     * @param needPrintChromeInfo
     * @return
     */
    /**
     * recommend use it
     * @param options
     * @param driverExecutablePath
     * @return
     */
    @JvmOverloads
    fun build(
        options: ChromeOptions?,
        driverExecutablePath: String?,
        suppressWelcome: Boolean = true,
        needPrintChromeInfo: Boolean = false
    ): ChromeDriver {
        return this.build(options, driverExecutablePath, null, suppressWelcome, needPrintChromeInfo)
    }

    /**
     * recommend use it
     * @param driverExecutablePath
     * @return
     */
    fun build(driverExecutablePath: String?): ChromeDriver {
        return this.build(null, driverExecutablePath)
    }

    /**
     * find free port
     * @return
     */
    private fun findFreePort(): Int {
        var socket: ServerSocket? = null
        try {
            socket = ServerSocket(0)
            return socket.getLocalPort()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
            }
        }
    }

    /**
     * find args in chromeOptions
     * @param chromeOptions
     */
    private fun LoadChromeOptionsArgs(chromeOptions: ChromeOptions?) {
        try {
            val argsField = chromeOptions!!.javaClass.superclass.getDeclaredField("args")
            argsField.isAccessible = true
            args = ArrayList(argsField[chromeOptions] as List<String?>)
        } catch (ignored: Exception) {
        }
    }

    /**
     * find chrome.exe or chrome
     * @return
     */
    @Throws(RuntimeException::class)
    private fun _getChromePath(): String {
        val os = System.getProperties().getProperty("os.name")
        var chromeDataPath: String? = null
        val IS_POSIX = SysUtil.isMacOs || SysUtil.isLinux
        val possibles: MutableSet<String> = HashSet()
        if (IS_POSIX) {
            val names: List<String> = mutableListOf(
                "google-chrome",
                "chromium",
                "chromium-browser",
                "chrome",
                "google-chrome-stable"
            )
            for (path: String in SysUtil.path) {
                for (name: String in names) {
                    possibles.add(path + File.separator + name)
                }
            }
            if (SysUtil.isMacOs) {
                possibles.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
                possibles.add("/Applications/Chromium.app/Contents/MacOS/Chromium")
            }
        } else {
            val paths: MutableList<String> = ArrayList()
            paths.add(SysUtil.getString("PROGRAMFILES"))
            paths.add(SysUtil.getString("PROGRAMFILES(X86)"))
            paths.add(SysUtil.getString("LOCALAPPDATA"))

            val middles = Arrays.asList(
                "Google" + File.separator + "Chrome" + File.separator + "Application",
                "Google" + File.separator + "Chrome Beta" + File.separator + "Application",
                "Google" + File.separator + "Chrome Canary" + File.separator + "Application"
            )

            for (path: String in paths) {
                for (middle: String in middles) {
                    possibles.add(path + File.separator + middle + File.separator + "chrome.exe")
                }
            }
        }

        for (possible: String in possibles) {
            val file = File(possible)
            if (file.exists() && file.canExecute()) {
                chromeDataPath = file.absolutePath
                break
            }
        }

        if (chromeDataPath == null) {
            throw RuntimeException("chrome not find in your pc, please use arg binaryLocation")
        }

        return chromeDataPath
    }

    /**
     * merge param for user-data
     * @param key
     * @param value
     * @param dict
     */
    private fun undotMerge(key: String, value: Any, dict: MutableMap<String, Any>?) {
        if (key.contains(".")) {
            val splits = key.split("\\.".toRegex(), limit = 2).toTypedArray()
            val k1 = splits[0]
            val k2 = splits[1]

            if (!dict!!.containsKey(k1)) {
                dict[k1] = HashMap<String, Any>()
            }
            try {
                undotMerge(k2, value, dict[k1] as MutableMap<String, Any>?)
            } catch (ignored: Exception) {
            }
            return
        }
        dict!![key] = value
    }
}