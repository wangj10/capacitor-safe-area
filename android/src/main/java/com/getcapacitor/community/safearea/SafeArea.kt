package com.getcapacitor.community.safearea

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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

    private var lastSystemBarsInsets: Insets? = null
    private var lastKeyboardHeight = -1
    private var lastKeyboardVisible = false

    private var appearanceConfigCache: AppearanceConfig = AppearanceConfig()

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        insets?.let {
            updateInsets(it)
            if (!appearanceUpdatedInListener) {
                updateAppearance(appearanceConfigCache)
                appearanceUpdatedInListener = true
            }
        }
    }

    fun enable(updateInsets: Boolean, appearanceConfig: AppearanceConfig) {
        this.appearanceConfigCache = appearanceConfig

        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        resetDecorFitsSystemWindows()
        updateAppearance(appearanceConfig)

        if (updateInsets) {
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            insets?.let { updateInsets(it) }
        }
    }

    fun disable(appearanceConfig: AppearanceConfig) {
        activity.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            activity.window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        }
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

    private fun updateInsets(windowInsets: WindowInsetsCompat) {
        activity.runOnUiThread {
            if (!decorFitsSystemWindowsNegated) {
                decorFitsSystemWindowsNegated = true
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            }

            val decorView = activity.window.decorView
            val contentView = decorView.findViewById<ViewGroup>(android.R.id.content)
            val density = activity.resources.displayMetrics.density

            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseBottomHeight = systemBarsInsets.bottom

            var isKeyboardVisible = false
            var absoluteKeyboardHeight = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
                isKeyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())

                if (isKeyboardVisible) {
                    absoluteKeyboardHeight = imeInsets.bottom
                }
            } else {
                val rect = Rect()
                decorView.getWindowVisibleDisplayFrame(rect)
                val physicalBottomGap = decorView.height - rect.bottom
                isKeyboardVisible = physicalBottomGap > (baseBottomHeight + 100)
                if (isKeyboardVisible) {
                    absoluteKeyboardHeight = physicalBottomGap
                }
            }

            if (lastSystemBarsInsets == systemBarsInsets &&
                lastKeyboardHeight == absoluteKeyboardHeight &&
                lastKeyboardVisible == isKeyboardVisible) {
                return@runOnUiThread
            }

            lastSystemBarsInsets = systemBarsInsets
            lastKeyboardHeight = absoluteKeyboardHeight
            lastKeyboardVisible = isKeyboardVisible

            setProperty("top", Math.round(systemBarsInsets.top / density) + offset)
            setProperty("left", Math.round(systemBarsInsets.left / density))
            setProperty("right", Math.round(systemBarsInsets.right / density))

            if (isKeyboardVisible) {
                setProperty("bottom", offset)
            } else {
                setProperty("bottom", Math.round(baseBottomHeight / density) + offset)
            }

            var paddingToApply = 0
            if (isKeyboardVisible) {
                val currentContentPadding = contentView?.paddingBottom ?: 0
                val externalShrinkage = (decorView.height - webView.height) - currentContentPadding

                if (externalShrinkage > absoluteKeyboardHeight * 0.5) {
                    paddingToApply = 0
                } else {
                    paddingToApply = absoluteKeyboardHeight
                }
            }

            if (contentView != null && contentView.paddingBottom != paddingToApply) {
                contentView.setPadding(0, 0, 0, paddingToApply)
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
            webView.evaluateJavascript(
                "document.querySelector(':root')?.style.setProperty('--safe-area-inset-$position', 'max(env(safe-area-inset-$position), ${size}px)');",
                null
            )
        }
    }
}