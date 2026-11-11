package com.leon.be_nobat.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leon.be_nobat.R

object MapHelper {

    /**
     * Show navigation options dialog to navigate to (lat, lng) or address via:
     * - Neshan
     * - Balad
     * - Google Maps
     * - Waze
     * - System default map app
     */
    fun openNavigation(context: Context, lat: Double?, lng: Double?, title: String? = null, address: String? = null) {
        val hasCoords = lat != null && lng != null && lat != 0.0 && lng != 0.0
        val effectiveTitle = title?.ifBlank { null } ?: context.getString(R.string.app_name)

        val options = mutableListOf<NavigationAppOption>()

        // 1. Neshan
        options.add(
            NavigationAppOption(
                name = context.getString(R.string.nav_app_neshan),
                iconRes = R.drawable.ic_map_pin,
                onSelect = {
                    val intent = if (hasCoords) {
                        Intent(Intent.ACTION_VIEW, Uri.parse("nshn:view?lat=$lat&lng=$lng")).apply {
                            setPackage("org.neshan.maps.and.navi")
                        }
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://neshan.org/maps/search/${Uri.encode(address ?: "")}"))
                    }
                    launchOrFallback(context, intent, if (hasCoords) "https://neshan.org/maps/@$lat,$lng,16z" else "https://neshan.org/maps/search/${Uri.encode(address ?: "")}")
                }
            )
        )

        // 2. Balad
        options.add(
            NavigationAppOption(
                name = context.getString(R.string.nav_app_balad),
                iconRes = R.drawable.ic_map_pin,
                onSelect = {
                    val intent = if (hasCoords) {
                        Intent(Intent.ACTION_VIEW, Uri.parse("balad:location?latitude=$lat&longitude=$lng")).apply {
                            setPackage("ir.balad.navigation")
                        }
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://balad.ir/search?query=${Uri.encode(address ?: "")}"))
                    }
                    launchOrFallback(context, intent, if (hasCoords) "https://balad.ir/location?latitude=$lat&longitude=$lng" else "https://balad.ir/search?query=${Uri.encode(address ?: "")}")
                }
            )
        )

        // 3. Google Maps
        options.add(
            NavigationAppOption(
                name = context.getString(R.string.nav_app_google_maps),
                iconRes = R.drawable.ic_map_pin,
                onSelect = {
                    val uri = if (hasCoords) {
                        Uri.parse("google.navigation:q=$lat,$lng")
                    } else {
                        Uri.parse("geo:0,0?q=${Uri.encode(address ?: "")}")
                    }
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    launchOrFallback(context, intent, if (hasCoords) "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng" else "https://www.google.com/maps/search/?api=1&query=${Uri.encode(address ?: "")}")
                }
            )
        )

        // 4. Waze
        options.add(
            NavigationAppOption(
                name = context.getString(R.string.nav_app_waze),
                iconRes = R.drawable.ic_map_pin,
                onSelect = {
                    val uri = if (hasCoords) {
                        Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
                    } else {
                        Uri.parse("waze://?q=${Uri.encode(address ?: "")}&navigate=yes")
                    }
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.waze")
                    }
                    launchOrFallback(context, intent, if (hasCoords) "https://waze.com/ul?ll=$lat,$lng&navigate=yes" else "https://waze.com/ul?q=${Uri.encode(address ?: "")}&navigate=yes")
                }
            )
        )

        // 5. System Map Chooser
        options.add(
            NavigationAppOption(
                name = context.getString(R.string.nav_app_system_default),
                iconRes = R.drawable.ic_map_pin,
                onSelect = {
                    val uri = if (hasCoords) {
                        Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(effectiveTitle)})")
                    } else {
                        Uri.parse("geo:0,0?q=${Uri.encode(address ?: "")}")
                    }
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.dialog_choose_nav_app)))
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.error_no_map_app_found, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        )

        val itemNames = options.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.dialog_choose_nav_app))
            .setItems(itemNames) { _, which ->
                options[which].onSelect()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun launchOrFallback(context: Context, intent: Intent, webFallbackUrl: String) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webFallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, R.string.error_no_map_app_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Interactive Location Picker using Leaflet / OpenStreetMap in an in-app Dialog.
     * Allows tapping/dragging a pin to select Latitude & Longitude.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun showLocationPicker(
        context: Context,
        initialLat: Double?,
        initialLng: Double?,
        onLocationSelected: (lat: Double, lng: Double) -> Unit
    ) {
        val lat = if (initialLat != null && initialLat != 0.0) initialLat else 32.6546
        val lng = if (initialLng != null && initialLng != 0.0) initialLng else 51.6680
        val hasInitial = initialLat != null && initialLng != null && initialLat != 0.0 && initialLng != 0.0

        var currentLat = lat
        var currentLng = lng

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_map_picker, null)
        val webView = view.findViewById<WebView>(R.id.mapWebView).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.blockNetworkLoads = false
            settings.blockNetworkImage = false
            settings.setGeolocationEnabled(true)
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    android.util.Log.e("MapHelper", "WebView Error: ${request?.url} -> ${error?.description}")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d("MapHelperJS", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                    return true
                }
            }
        }

        class AndroidBridge {
            @JavascriptInterface
            fun onLocationChanged(newLat: Double, newLng: Double) {
                currentLat = newLat
                currentLng = newLng
            }
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        val zoom = if (hasInitial) 15 else 12
        webView.loadUrl("file:///android_asset/leaflet/map_picker.html?lat=$lat&lng=$lng&zoom=$zoom")

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.title_pick_location_on_map))
            .setView(view)
            .setPositiveButton(R.string.action_confirm_location) { _, _ ->
                onLocationSelected(currentLat, currentLng)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            val metrics = context.resources.displayMetrics
            val widthPx = (metrics.widthPixels * 0.95).toInt()
            val heightPx = (metrics.heightPixels * 0.72).toInt().coerceAtLeast((450 * metrics.density).toInt())
            dialog.window?.setLayout(widthPx, heightPx)
            webView.postDelayed({
                webView.evaluateJavascript("if (window.fitMap) window.fitMap();", null)
            }, 300)
            webView.postDelayed({
                webView.evaluateJavascript("if (window.fitMap) window.fitMap();", null)
            }, 700)
        }

        dialog.show()
    }

    /**
     * Preview location on map in an in-app Dialog with a Navigate button.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun showMapPreview(context: Context, lat: Double, lng: Double, title: String? = null, address: String? = null) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_map_picker, null)
        val webView = view.findViewById<WebView>(R.id.mapWebView).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.blockNetworkLoads = false
            settings.blockNetworkImage = false
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    android.util.Log.e("MapHelper", "WebView Error: ${request?.url} -> ${error?.description}")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d("MapHelperJS", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                    return true
                }
            }
        }

        val encodedTitle = java.net.URLEncoder.encode(title ?: "", "UTF-8")
        webView.loadUrl("file:///android_asset/leaflet/map_preview.html?lat=$lat&lng=$lng&title=$encodedTitle")

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title ?: context.getString(R.string.title_view_location_on_map))
            .setMessage(address)
            .setView(view)
            .setPositiveButton(R.string.action_navigate) { _, _ ->
                openNavigation(context, lat, lng, title, address)
            }
            .setNegativeButton(R.string.action_close, null)
            .create()

        dialog.setOnShowListener {
            val metrics = context.resources.displayMetrics
            val widthPx = (metrics.widthPixels * 0.95).toInt()
            val heightPx = (metrics.heightPixels * 0.70).toInt().coerceAtLeast((420 * metrics.density).toInt())
            dialog.window?.setLayout(widthPx, heightPx)
            webView.postDelayed({
                webView.evaluateJavascript("if (window.fitMap) window.fitMap();", null)
            }, 300)
            webView.postDelayed({
                webView.evaluateJavascript("if (window.fitMap) window.fitMap();", null)
            }, 700)
        }

        dialog.show()
    }

    private data class NavigationAppOption(
        val name: String,
        val iconRes: Int,
        val onSelect: () -> Unit
    )
}
