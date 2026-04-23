package com.getcapacitor.community.safearea

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class SafeArea(private val activity: Activity, private val webView: WebView) {
    var offset = 0
    private var appearanceUpdatedInListener = false
    private var decorFitsSystemWindowsNegated = false

    private var isEnvironmentAutoResizing = false

    fun enable(updateInsets: Boolean, appearanceConfig: AppearanceConfig) {
        activity.window.decorView.getRootView().setOnApplyWindowInsetsListener { view, insets ->
            updateInsets()
            if (!appearanceUpdatedInListener) {
                updateAppearance(appearanceConfig)
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            val insetsController = WindowCompat.getInsetsController(window, decorView)
            insetsController.isAppearanceLightStatusBars = appearanceConfig.statusBarContent == "dark"
            insetsController.isAppearanceLightNavigationBars = appearanceConfig.navigationBarContent == "dark"

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
                        if (appearanceConfig.statusBarContent == "dark") WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                    setSystemBarsAppearance(
                        if (appearanceConfig.navigationBarContent == "dark") WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
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
                    decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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

    private fun updateInsets() {
        activity.runOnUiThread {
            if (!decorFitsSystemWindowsNegated) {
                decorFitsSystemWindowsNegated = true
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            }

            val decorView = activity.window.decorView
            val windowInsets = ViewCompat.getRootWindowInsets(decorView) ?: return@runOnUiThread

            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = activity.resources.displayMetrics.density

            val rect = Rect()
            decorView.getWindowVisibleDisplayFrame(rect)

            val physicalBottomGap = decorView.height - rect.bottom

            val trueNavHeight = Math.min(navBarInsets.bottom, physicalBottomGap)

            val isKeyboardVisible = physicalBottomGap > (trueNavHeight + 100)

            val actualImeHeight = if (isKeyboardVisible) physicalBottomGap else 0

            setProperty("top", Math.round(systemBarsInsets.top / density) + offset)
            setProperty("left", Math.round(systemBarsInsets.left / density))
            setProperty("right", Math.round(systemBarsInsets.right / density))

            if (isKeyboardVisible) {
                setProperty("bottom", offset)
            } else {
                setProperty("bottom", Math.round(trueNavHeight / density) + offset)
            }

            val contentView = decorView.findViewById<ViewGroup>(android.R.id.content)

            contentView?.let { content ->
                if (isEnvironmentAutoResizing) {
                    content.setPadding(0, 0, 0, 0)
                    return@let
                }

                if (isKeyboardVisible) {
                    val currentPaddingBottom = content.paddingBottom

                    webView.evaluateJavascript("document.body.clientHeight") { result ->
                        val webHeight = result?.replace("\"", "")?.toFloatOrNull() ?: 0f
                        val screenHeightCss = decorView.height / density

                        val imeHeightCss = actualImeHeight / density

                        val isWindowShrunk = actualImeHeight > 0 && webHeight > 0 && (screenHeightCss - webHeight) > (imeHeightCss * 0.8)

                        val isShrunkByOurPadding = isWindowShrunk && currentPaddingBottom > 0

                        activity.runOnUiThread {
                            if (isWindowShrunk && !isShrunkByOurPadding) {
                                content.setPadding(0, 0, 0, 0)
                                isEnvironmentAutoResizing = true
                            } else if (!isEnvironmentAutoResizing) {
                                content.setPadding(0, 0, 0, actualImeHeight)
                            }
                        }
                    }
                } else {
                    content.setPadding(0, 0, 0, 0)
                }
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