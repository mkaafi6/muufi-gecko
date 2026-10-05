package com.mkaafi6.muufi

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * muufi (GeckoView edition) — full Gecko engine + the real uBlock Origin
 * extension, installed from the bundled signed .xpi.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var toolbar: Toolbar

    private val homeUrl = "https://mkaafi6.github.io/muufi/"
    private val history = ArrayDeque<String>()

    companion object {
        @Volatile
        private var runtime: GeckoRuntime? = null
        private const val TAG = "muufi"
        private const val UBO_XPI = "resource://android/assets/ublock_origin.xpi"
        private const val UBO_ID = "uBlock0@raymondhill.net"
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
            }

            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    if (history.lastOrNull() != url) {
                        history.addLast(url)
                        if (history.size > 100) history.removeFirst()
                    }
                }
            }
        }

        if (runtime == null) {
            runtime = GeckoRuntime.create(this)
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
                Log.i(TAG, "Granting install for ${extension.metaData?.name}")
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

        controller.ensureBuiltIn(UBO_XPI, UBO_ID)
            .accept(
                { ext -> Log.i(TAG, "uBO installed: ${ext?.id}") },
                { e -> Log.e(TAG, "uBO install failed", e) }
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
        findViewById<LinearLayout>(R.id.btnInfo).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun goBack() {
        if (history.size > 1) {
            history.removeLast()
            session.goBack()
        } else {
            finish()
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
