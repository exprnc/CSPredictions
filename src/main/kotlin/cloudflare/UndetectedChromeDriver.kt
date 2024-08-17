package com.exprnc.cspredictions.cloudflare

import org.openqa.selenium.Capabilities
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class UndetectedChromeDriver(
    private val chromeOptions: ChromeOptions?,
    private val _headless: Boolean,
    private val _keepUserDataDir: Boolean,
    private val _userDataDir: String?,
    private val _browser: Process?
) : ChromeDriver(chromeOptions) {
    override fun get(url: String) {
        if (_headless) {
            _headless()
        }
        _cdcProps()
        _stealth()
        super.get(url)
    }


    override fun quit() {
        super.quit()
        // kill process
        _browser?.destroyForcibly()
        //delete temp user dir
        if (_keepUserDataDir.not()) {
            for (i in 0..4) {
                try {
                    val file = File(_userDataDir)
                    if (!file.exists()) {
                        break
                    }
                    val f = file.delete()
                    if (f) {
                        break
                    }
                } catch (e: Exception) {
                    try {
                        Thread.sleep(300)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        deleteChromeTmpDirs()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun deleteChromeTmpDirs() {
        val tmpDirPath = Paths.get("../../../tmp/snap-private-tmp/snap.chromium/tmp")
        try {
            Files.newDirectoryStream(tmpDirPath, ".org.chromium.Chromium.*").use { stream ->
                stream.forEach { path ->
                    try {
                        path.deleteRecursively()
                    } catch (e: Exception) {
                        println("Error removing $path: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error getting .org.chromium.Chromium.*")
        }
    }

    /**
     * configure headless
     */
    private fun _headless() {
        //set navigator.webdriver
        val f = this.executeScript("return navigator.webdriver") ?: return

        val params1: MutableMap<String, Any> = HashMap()
        params1["source"] = """Object.defineProperty(window, 'navigator', {
    value: new Proxy(navigator, {
        has: (target, key) => (key === 'webdriver' ? false : key in target),
        get: (target, key) =>
            key === 'webdriver' ?
            false :
            typeof target[key] === 'function' ?
            target[key].bind(target) :
            target[key]
        })
});"""

        this.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params1)

        //set ua
        val params2: MutableMap<String, Any> = HashMap()
        params2["userAgent"] =
            (this.executeScript("return navigator.userAgent") as String).replace("Headless", "")
        this.executeCdpCommand("Network.setUserAgentOverride", params2)

        val params3: MutableMap<String, Any> = HashMap()
        params3["source"] = "Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 1});"
        this.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params3)

        val params4: MutableMap<String, Any> = HashMap()
        params4["source"] = """Object.defineProperty(navigator.connection, 'rtt', {get: () => 100});
// https://github.com/microlinkhq/browserless/blob/master/packages/goto/src/evasions/chrome-runtime.js
window.chrome = {
        app: {
            isInstalled: false,
            InstallState: {
                DISABLED: 'disabled',
                INSTALLED: 'installed',
                NOT_INSTALLED: 'not_installed'
            },
            RunningState: {
                CANNOT_RUN: 'cannot_run',
                READY_TO_RUN: 'ready_to_run',
                RUNNING: 'running'
            }
        },
        runtime: {
            OnInstalledReason: {
                CHROME_UPDATE: 'chrome_update',
                INSTALL: 'install',
                SHARED_MODULE_UPDATE: 'shared_module_update',
                UPDATE: 'update'
            },
            OnRestartRequiredReason: {
                APP_UPDATE: 'app_update',
                OS_UPDATE: 'os_update',
                PERIODIC: 'periodic'
            },
            PlatformArch: {
                ARM: 'arm',
                ARM64: 'arm64',
                MIPS: 'mips',
                MIPS64: 'mips64',
                X86_32: 'x86-32',
                X86_64: 'x86-64'
            },
            PlatformNaclArch: {
                ARM: 'arm',
                MIPS: 'mips',
                MIPS64: 'mips64',
                X86_32: 'x86-32',
                X86_64: 'x86-64'
            },
            PlatformOs: {
                ANDROID: 'android',
                CROS: 'cros',
                LINUX: 'linux',
                MAC: 'mac',
                OPENBSD: 'openbsd',
                WIN: 'win'
            },
            RequestUpdateCheckStatus: {
                NO_UPDATE: 'no_update',
                THROTTLED: 'throttled',
                UPDATE_AVAILABLE: 'update_available'
            }
        }
}

// https://github.com/microlinkhq/browserless/blob/master/packages/goto/src/evasions/navigator-permissions.js
if (!window.Notification) {
        window.Notification = {
            permission: 'denied'
        }
}

const originalQuery = window.navigator.permissions.query
window.navigator.permissions.__proto__.query = parameters =>
        parameters.name === 'notifications'
            ? Promise.resolve({ state: window.Notification.permission })
            : originalQuery(parameters)
        
const oldCall = Function.prototype.call 
function call() {
        return oldCall.apply(this, arguments)
}
Function.prototype.call = call

const nativeToStringFunctionString = Error.toString().replace(/Error/g, 'toString')
const oldToString = Function.prototype.toString

function functionToString() {
        if (this === window.navigator.permissions.query) {
            return 'function query() { [native code] }'
        }
        if (this === functionToString) {
            return nativeToStringFunctionString
        }
        return oldCall.call(oldToString, this)
}
// eslint-disable-next-line
Function.prototype.toString = functionToString"""
        this.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params4)
    }

    /**
     * remove cdc
     */
    private fun _cdcProps() {
        val f = this.executeScript(
            """let objectToInspect = window,
    result = [];
while(objectToInspect !== null)
{ result = result.concat(Object.getOwnPropertyNames(objectToInspect));
  objectToInspect = Object.getPrototypeOf(objectToInspect); }
return result.filter(i => i.match(/.+_.+_(Array|Promise|Symbol)/ig))"""
        ) as List<String>

        if (f != null && f.size > 0) {
            val param: MutableMap<String, Any> = HashMap()
            param["source"] = """let objectToInspect = window,
    result = [];
while(objectToInspect !== null)
{ result = result.concat(Object.getOwnPropertyNames(objectToInspect));
  objectToInspect = Object.getPrototypeOf(objectToInspect); }
result.forEach(p => p.match(/.+_.+_(Array|Promise|Symbol)/ig)
                    &&delete window[p]&&console.log('removed',p))"""
            this.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", param)
        }
    }

    /**
     * set stealth
     */
    private fun _stealth() {
        val stringBuffer = StringBuilder()
        var bufferedReader: BufferedReader? = null
        try {
            val `in` = this.javaClass.getResourceAsStream("/stealth.min.js")
            bufferedReader = BufferedReader(InputStreamReader(`in`))
            var str: String? = null
            while ((bufferedReader.readLine().also { str = it }) != null) {
                stringBuffer.append(str)
                stringBuffer.append("\n")
            }
            `in`.close()
            bufferedReader.close()
        } catch (ignored: Exception) {
            println("error stealth")
        }
        val params: MutableMap<String, Any> = HashMap()
        params["source"] = stringBuffer.toString()

        this.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params)
    }

    public override fun startSession(capabilities: Capabilities) {
        var capabilities: Capabilities? = capabilities
        if (capabilities == null) {
            capabilities = this.chromeOptions
        }
        super.startSession(capabilities)
    }
}