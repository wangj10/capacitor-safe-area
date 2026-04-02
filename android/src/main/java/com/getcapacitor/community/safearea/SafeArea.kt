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
        activity.runOnUiThread {
            if (!decorFitsSystemWindowsNegated) {
                decorFitsSystemWindowsNegated = true
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(activity.window.decorView.rootView) { view, insets ->
            updateInsets(insets)
            if (!appearanceUpdatedInListener) {
                updateAppearance(appearanceConfig)
                appearanceUpdatedInListener = true
            }
            WindowInsetsCompat.CONSUMED
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            }

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
                    window.statusBarColor = safeParseColor(appearanceConfig.statusBarColor)
                    window.navigationBarColor = safeParseColor(appearanceConfig.navigationBarColor)
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
                    window.statusBarColor = safeParseColor(appearanceConfig.statusBarColor)
                    window.navigationBarColor = safeParseColor(appearanceConfig.navigationBarColor)
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

    private fun safeParseColor(colorStr: String?): Int {
        if (colorStr.isNullOrEmpty()) return Color.TRANSPARENT
        return try {
            Color.parseColor(colorStr)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            Color.TRANSPARENT
        }
    }

    private fun updateInsets(providedInsets: WindowInsetsCompat? = null) {
        activity.runOnUiThread {
            if (!decorFitsSystemWindowsNegated) {
                decorFitsSystemWindowsNegated = true
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            }

            val windowInsets = providedInsets ?: ViewCompat.getRootWindowInsets(activity.window.decorView)
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

            val isSamsungS10OnAndroid10 = Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Build.MODEL.startsWith("SM-G97") &&
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || isSamsungS10OnAndroid10) {
                activity.window.decorView.setPadding(0, 0, 0, 0)
            } else {
                activity.window.decorView.setPadding(0, 0, 0, imeHeight)
            }
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
