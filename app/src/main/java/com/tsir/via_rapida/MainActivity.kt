package com.tsir.via_rapida

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.setWebContentsDebuggingEnabled
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewClientCompat
import com.tsir.via_rapida.databinding.ActivityMainBinding

/**
 * Create a WebView with permission settings for
 * native location, camera and storage access.
 *
 * @author Miguel Torres Velandia
 * @property Thomas Instruments S.A
 * @since 20/10/2023
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * Handler to load an external web page and then receive a result of this web activity.
     * It will be used in the WebChromeClient
     */
    private val fileChooserLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.let { data ->
            WebChromeClient.FileChooserParams.parseResult(RESULT_OK, data)?.let { uris ->
                webChromeClientFilePathCallback?.onReceiveValue(uris)
                webChromeClientFilePathCallback = null
                return@registerForActivityResult
            }
        }
        webChromeClientFilePathCallback?.onReceiveValue(null)
        webChromeClientFilePathCallback = null
    }

    private var webChromeClientFilePathCallback: ValueCallback<Array<Uri>>? = null

    private var cameraPermission: PermissionRequest? = null
    private var storagePermission: PermissionRequest? = null

    /**
     * Request permission ids - these values can be any unique
     * integer, and are used to identity permission request callbacks
     */
    private val requestCameraPermission = 100001
    private val requestFineLocationPermission = 100002
    private val requestExternalStoragePermission = 100003
    private var geolocationOrigin: String? = null
    private var geolocationCallback: GeolocationPermissions.Callback? = null

    private val webappUrl = "https://www.example.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mainWebView: WebView = binding.webViewContainer
        setWebContentsDebuggingEnabled(true)

        setWebChromeClient(mainWebView)
        setWebViewClient(mainWebView)
        setDownloadManager(mainWebView)
        webViewSettingsConfig(mainWebView)

        // create a bridge between javascript and activity
        mainWebView.addJavascriptInterface(JavaScriptInterface(applicationContext), "Android")
        // mainWebView.addJavascriptInterface(JSInterface(this),"Android")

    }

    /**
     * Manages the interaction between application's JS elements and WebView
     */
    private fun setWebChromeClient(webView: WebView){
        webView.webChromeClient = object : WebChromeClient() {
            // handle the file selection request by an input element
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                webChromeClientFilePathCallback?.let {
                    it.onReceiveValue(null)
                    webChromeClientFilePathCallback = null
                }
                webChromeClientFilePathCallback = filePathCallback
                fileChooserParams?.createIntent()?.let { intent ->
                    try {
                        fileChooserLauncher.launch(intent)
                    }catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, getString(R.string.no_open), Toast.LENGTH_LONG)
                            .show()
                        return false
                    }
                }
                return true
            }

            // Convenience method to expose console.log output.
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("WebView", consoleMessage.message())
                return true
            }

            // Handles permission requests created in the WebView
            override fun onPermissionRequest(request: PermissionRequest?) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    requestCameraPermission
                )
                cameraPermission = request
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val perm = Manifest.permission.ACCESS_FINE_LOCATION
                if(ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    perm
                ) == PackageManager.PERMISSION_GRANTED
                ){
                    callback?.invoke(origin, true, false)
                }else{
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity,
                            perm
                        )
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(perm),
                            requestFineLocationPermission
                        )
                        // hold onto the origin and callback for
                        // permissions results callback
                        geolocationOrigin = origin
                        geolocationCallback = callback
                    }
                }
            }
        }
    }

    /**
     * Controller to handle specific events and behaviors related
     * to web browsing within the application.
     */
    private fun setWebViewClient(webView: WebView){
        val webClient = MyWebViewClient()
        webView.webViewClient = webClient
    }

    private fun setDownloadManager(webView: WebView){
        webView.setDownloadListener { url, _, _, _, _->
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (!hasPermissions(permissions)) {
                requestPermissions(permissions)
            } else {
                webView.loadUrl(JavaScriptInterface.getBase64StringFromBlobUrl(url))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun webViewSettingsConfig(webView: WebView){
        webView.apply {
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.defaultTextEncodingName = "utf-8"
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.pluginState = WebSettings.PluginState.ON

            loadUrl(webappUrl)
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions(permissions: Array<String>) {
        if (permissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }) {
            val explanationDialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_title))
                .setMessage(getString(R.string.permission_message))
                .setPositiveButton(getString(R.string.accept)) { _, _ ->
                    ActivityCompat.requestPermissions(this, permissions, requestExternalStoragePermission)
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                    Log.e("Alert",getString(R.string.cancel_permission_text))
                }
                .create()
            explanationDialog.show()
        }
        ActivityCompat.requestPermissions(this, permissions, requestExternalStoragePermission)
    }

    // Handle the results of the requested permissions.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            requestFineLocationPermission -> {
                var allow = false
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // Permission accepted
                    allow = true
                }
                geolocationCallback?.invoke(geolocationOrigin, allow, false)
            }
            requestCameraPermission -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    cameraPermission?.grant(cameraPermission?.resources)
                }else{
                    cameraPermission?.deny()
                }
            }
            requestExternalStoragePermission -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    storagePermission?.grant(storagePermission?.resources)
                }else{
                    storagePermission?.deny()
                }
            }
        }
    }

    private inner class MyWebViewClient : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url = request.url.toString()
            return if (url.endsWith(".csv") || url.endsWith(".pdf") || url.endsWith(".xls")) {
                // Open download on external window
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                true
            }else{
                view.loadUrl(url)
                true
            }
        }
    }

    // Listens for verification results that are returned from JavaScript
    fun onVerificationResults(success: Boolean, results: String) {
        Log.d("VerificationListener", success.toString())
        Log.d("VerificationListener", results)
    }
}