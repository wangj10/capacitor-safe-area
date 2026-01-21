package com.getcapacitor.community.safearea

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class SafeArea(private val activity: Activity, private val webView: WebView) {
    var offset = 0
    private var appearanceUpdatedInListener = false
    private var decorFitsSystemWindowsNegated = false

    fun enable(updateInsets: Boolean, appearanceConfig: AppearanceConfig) {
        activity.window.decorView.getRootView().setOnApplyWindowInsetsListener { view, insets ->
            updateInsets()
            if (!appearanceUpdatedInListener) {
                // @TODO: appearance is sometimes not updated on app load
                // probably because it is superseded by another plugin or native thing that updates the appearance
                // This is probably not the best way to override that behaviour
                // So we should think of something better than simply calling `updateAppearance` here
                updateAppearance(appearanceConfig)
                // Only update it once, to prevent an infinite loop
                appearanceUpdatedInListener = true
            }
            view.onApplyWindowInsets(insets)
        }

        resetDecorFitsSystemWindows()
        updateAppearance(appearanceConfig)

        if (updateInsets) {
            updateInsets()
        }
    }

    fun disable(appearanceConfig: AppearanceConfig) {
        activity.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        }
        activity.window.decorView.getRootView().setOnApplyWindowInsetsListener(null)
        resetProperties()

        updateAppearance(appearanceConfig)
    }

    fun resetDecorFitsSystemWindows() {
        decorFitsSystemWindowsNegated = false
    }

    private fun updateAppearance(appearanceConfig: AppearanceConfig) {
        activity.runOnUiThread {
            val window = activity.window
            val decorView = window.decorView

            val insetsController =
                WindowCompat.getInsetsController(window, decorView)
            insetsController.isAppearanceLightStatusBars =
                appearanceConfig.statusBarContent == "dark"
            insetsController.isAppearanceLightNavigationBars =
                appearanceConfig.navigationBarContent == "dark"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = window.insetsController
                controller?.apply {
                    setSystemBarsAppearance(
                        if (appearanceConfig.statusBarContent == "dark")
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        else 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )

                    setSystemBarsAppearance(
                        if (appearanceConfig.navigationBarContent == "dark")
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        else 0,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }

                if (appearanceConfig.customColorsForSystemBars) {
                    window.statusBarColor = Color.parseColor(appearanceConfig.statusBarColor)
                    window.navigationBarColor = Color.parseColor(appearanceConfig.navigationBarColor)
                } else {
                    window.statusBarColor = Color.TRANSPARENT
                    window.navigationBarColor = Color.TRANSPARENT
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (appearanceConfig.statusBarContent == "dark") {
                    decorView.systemUiVisibility =
                        decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }

                if (appearanceConfig.customColorsForSystemBars) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    window.statusBarColor = Color.parseColor(appearanceConfig.statusBarColor)
                    window.navigationBarColor = Color.parseColor(appearanceConfig.navigationBarColor)
                } else {
                    window.statusBarColor = Color.TRANSPARENT
                    window.navigationBarColor = Color.TRANSPARENT
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            }
        }
    }

    private fun updateInsets() {
        activity.runOnUiThread {
            if (!decorFitsSystemWindowsNegated) {
                decorFitsSystemWindowsNegated = true
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            }

            val windowInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val systemBarsInsets =
                windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE
            val navBarInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.navigationBars()) ?: Insets.NONE
            val imeInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.ime()) ?: Insets.NONE

            val density = activity.resources.displayMetrics.density

            setProperty("top", Math.round(systemBarsInsets.top / density) + offset)
            setProperty("left", Math.round(systemBarsInsets.left / density))
            setProperty("right", Math.round(systemBarsInsets.right / density))

            val bottomHeight = navBarInsets.bottom
            setProperty("bottom", Math.round(bottomHeight / density) + offset)

            // To get the actual height of the keyboard, we need to subtract the height of the system bars from the height of the ime
            // Source: https://stackoverflow.com/a/75328335/8634342
            val imeHeight = (imeInsets.bottom - systemBarsInsets.bottom).coerceAtLeast(0)

            // Set padding of decorview so the scroll view stays correct.
            // Otherwise the content behind the keyboard cannot be viewed by the user.
            activity.window.decorView.setPadding(0, 0, 0, imeHeight)
        }
    }

    private fun resetProperties() {
        setProperty("top", 0)
        setProperty("left", 0)
        setProperty("bottom", 0)
        setProperty("right", 0)
    }

    private fun setProperty(position: String, size: Int) {
        activity.runOnUiThread {
            webView.loadUrl("javascript:document.querySelector(':root')?.style.setProperty('--safe-area-inset-" + position + "', 'max(env(safe-area-inset-" + position + "), " + size + "px)');void(0);")
        }
    }
}
