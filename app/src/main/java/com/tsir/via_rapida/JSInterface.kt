package com.tsir.via_rapida

import android.webkit.JavascriptInterface
/**
 * Connect JS with WebView (code by Android Docs)
 *
 * @author Miguel Torres Velandia
 * @since 20/10/2023
 */
class JSInterface(private val listener: VerificationListener) {

    @JavascriptInterface
    fun onVerifyResults(success: Boolean, results: String) {
        listener.onVerificationResults(success, results)
    }
}

// generic interface
interface VerificationListener {
    fun onVerificationResults(success: Boolean, results: String)
}