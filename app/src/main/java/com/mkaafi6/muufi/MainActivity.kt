package com.mkaafi6.muufi

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * muufi (GeckoView edition) — full Gecko engine + the real uBlock Origin.
 *
 * Diagnostic build: adds a Block-ads On/Off toggle + uBO status + crash/failure
 * toasts so we can tell whether a blank page is uBO over-blocking or a
 * GeckoView content-process crash.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var toolbar: Toolbar

    private val homeUrl = "https://mkaafi6.github.io/muufi/"
    private val history = ArrayDeque<String>()

    @Volatile
    private var ubo: WebExtension? = null

    @Volatile
    private var uboState: String = "unknown"

    /** Last URL we navigated to, used to recover after a process kill. */
    @Volatile
    private var currentUrl: String? = null

    companion object {
        @Volatile
        private var runtime: GeckoRuntime? = null
        private const val TAG = "muufi"
        // Built-in extensions are installed from an *unpacked folder* under
        // assets, not from an .xpi file (an .xpi yields
        // "This URL does not point to a folder").
        private const val UBO_URI = "resource://android/assets/ublock_origin/"
        private const val UBO_ID = "uBlock0@raymondhill.net"
        private const val PREFS = "muufi"
        private const val PREF_ADS = "block_ads"
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun adsEnabled(): Boolean = prefs().getBoolean(PREF_ADS, true)

    private fun toast(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        geckoView = findViewById(R.id.geckoview)

        session = GeckoSession().apply {
            contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {
                    supportActionBar?.title = title ?: getString(R.string.app_name)
                }

                override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                    applyFullscreen(fullScreen)
                }

                override fun onCrash(session: GeckoSession) {
                    toast("Gecko content process CRASHED — recovering")
                    recoverSession()
                }

                override fun onKill(session: GeckoSession) {
                    toast("Gecko content process was KILLED — recovering")
                    recoverSession()
                }
            }

            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    if (history.lastOrNull() != url) {
                        history.addLast(url)
                        if (history.size > 100) history.removeFirst()
                    }
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    if (!success) toast("Page failed to load")
                }
            }
        }

        if (runtime == null) {
            // Conservative process settings: some custom ROMs/devices kill Gecko's
            // content process (app-zygote / isolation / fission). Disabling those
            // keeps it in a plain child process that survives.
            val settings = GeckoRuntimeSettings.Builder()
                .appZygoteProcessEnabled(false)
                .isolatedProcessEnabled(false)
                .fissionEnabled(false)
                .build()
            runtime = GeckoRuntime.create(this, settings)
        }
        session.open(runtime!!)
        geckoView.setSession(session)

        installUblock(runtime!!)
        wireBottomBar()

        session.loadUri(homeUrl)
    }

    /** Installs the bundled uBlock Origin .xpi, auto-approving its permissions. */
    private fun installUblock(rt: GeckoRuntime) {
        val controller = rt.webExtensionController
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(true, true, true)
                )
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        controller.install(UBO_XPI, WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { ext ->
                ubo = ext
                uboState = "installed"
                toast("uBO: $uboState")
                if (!adsEnabled()) ext?.let { controller.disable(it, WebExtensionController.EnableSource.USER) }
            },
            { e ->
                uboState = "INSTALL FAILED"
                toast("uBO install FAILED: ${e?.message}")
            }
        )
    }

    private fun wireBottomBar() {
        findViewById<LinearLayout>(R.id.btnHome).setOnClickListener {
            session.loadUri(homeUrl)
        }
        findViewById<LinearLayout>(R.id.btnRefresh).setOnClickListener {
            session.reload()
        }
        findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { goBack() }
        findViewById<LinearLayout>(R.id.btnInfo).setOnClickListener { showAbout() }
    }

    private fun showAbout() {
        val uboLine = "uBlock Origin: $uboState"
        val adsLine = "Block ads: " + if (adsEnabled()) "ON" else "OFF"
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_body) + "\n\n$uboLine\n$adsLine")
            .setPositiveButton("Toggle ads") { _, _ -> toggleAds() }
            .setNeutralButton("Reload") { _, _ -> session.reload() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun toggleAds() {
        val nowEnabled = !adsEnabled()
        prefs().edit().putBoolean(PREF_ADS, nowEnabled).apply()
        val rt = runtime
        val ext = ubo
        if (rt != null && ext != null) {
            if (nowEnabled) rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
            else rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
            toast("Block ads: " + if (nowEnabled) "ON" else "OFF")
        } else {
            toast("uBO not available (install failed)")
        }
        session.reload()
    }

    private fun goBack() {
        if (history.size > 1) {
            history.removeLast()
            session.goBack()
        } else {
            finish()
        }
    }

    private fun recoverSession() {
        val rt = runtime ?: return
        runOnUiThread {
            try {
                session.open(rt)
                geckoView.setSession(session)
                session.loadUri(homeUrl)
            } catch (t: Throwable) {
                toast("recover failed: ${t.message}")
            }
        }
    }

    override fun onBackPressed() {
        goBack()
    }

    private fun applyFullscreen(full: Boolean) {
        val bar = findViewById<View>(R.id.bottombar)
        if (full) {
            toolbar.visibility = View.GONE
            bar.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            enterImmersive()
        } else {
            toolbar.visibility = View.VISIBLE
            bar.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            exitImmersive()
        }
    }

    @Suppress("DEPRECATION")
    private fun enterImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    @Suppress("DEPRECATION")
    private fun exitImmersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
