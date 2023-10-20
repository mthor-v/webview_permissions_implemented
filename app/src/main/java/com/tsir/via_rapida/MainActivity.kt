package com.tsir.via_rapida

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebView.setWebContentsDebuggingEnabled
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tsir.via_rapida.databinding.ActivityMainBinding
/**
 * Create a WebView with permission settings for
 * native location, camera and storage access.
 *
 * @author Miguel Torres Velandia
 * @property Thomas Instruments S.A
 * @since 20/10/2023
 */
class MainActivity : AppCompatActivity(), VerificationListener {

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

    /**
     * Request permission ids - these values can be any unique
     * integer, and are used to identity permission request callbacks
     */
    private val REQUEST_CAMERA_PERMISSION = 100001
    private val REQUEST_FINE_LOCATION_PERMISSION = 100002
    private var geolocationOrigin: String? = null
    private var geolocationCallback: GeolocationPermissions.Callback? = null

    private val webappUrl = "https://www.example.com/#/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mainWebView: WebView = binding.webViewContainer
        setWebContentsDebuggingEnabled(true)

        /**
         * Manages the interaction between the application's JS elements and the WebView
         */
        mainWebView.webChromeClient = object : WebChromeClient() {
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
                    REQUEST_CAMERA_PERMISSION
                )
                cameraPermission = request
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val perm = Manifest.permission.ACCESS_FINE_LOCATION
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            perm
                        ) == PackageManager.PERMISSION_GRANTED
                ){
                    // we're on SDK < 23 OR user has already granted permission
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
                            REQUEST_FINE_LOCATION_PERMISSION
                        )
                        // hold onto the origin and callback for
                        // permissions results callback
                        geolocationOrigin = origin
                        geolocationCallback = callback
                    }
                }
            }
        }

        /**
         * Controller to handle specific events and behaviors related
         * to web browsing within the application.
         */
        mainWebView.webViewClient = object : WebViewClient() {
            // Avoid windows outside the webapp
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                println(getString(R.string.loading_error)+description)
            }
        }

        // create a bridge between the javascript and the activity
        mainWebView.addJavascriptInterface(JSInterface(this),"JSBridge")
        // Configure settings
        mainWebView.apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true

            loadUrl(webappUrl)
        }
    }

    // handle the results of permissions requested
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_FINE_LOCATION_PERMISSION -> {
                var allow = false
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // Permission accepted
                    allow = true
                }
                geolocationCallback?.let {
                    it.invoke(geolocationOrigin, allow, false)
                }
            }
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    cameraPermission?.grant(cameraPermission?.resources)
                }else{
                    cameraPermission?.deny()
                }
            }
        }
    }

    // listens for verification results that are return from javascript
    override fun onVerificationResults(success: Boolean, results: String) {
        // based on success / fail, implement the code you wish, ie
        // navigate to another fragment / activity
        Log.d("VerificationListener", success.toString())
        Log.d("VerificationListener", results)
    }
}