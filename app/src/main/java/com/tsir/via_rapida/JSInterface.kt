package com.tsir.via_rapida

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Connect JS with WebView
 *
 * @author Miguel Torres Velandia
 * @since 20/10/2023
 */
class JSInterface(private val context: Context) {

    @JavascriptInterface
    @Throws(IOException::class)
    fun getBase64FromBlobData(base64Data: String) {
        convertBase64StringToPdfAndStoreIt(base64Data)
    }

    fun getBase64StringFromBlobUrl(blobUrl: String): String {
        return if (blobUrl.startsWith("blob")) {
            "javascript: var xhr = new XMLHttpRequest();" +
                    "xhr.open('GET', '" + blobUrl + "', true);" +
                    "xhr.setRequestHeader('Content-type','application/pdf');" +
                    "xhr.responseType = 'blob';" +
                    "xhr.onload = function(e) {" +
                    "    if (this.status == 200) {" +
                    "        var blobPdf = this.response;" +
                    "        var reader = new FileReader();" +
                    "        reader.readAsDataURL(blobPdf);" +
                    "        reader.onloadend = function() {" +
                    "            base64data = reader.result;" +
                    "            Android.getBase64FromBlobData(base64data);" +
                    "        }" +
                    "    }" +
                    "};" +
                    "xhr.send();"
        } else "javascript: console.log('It is not a Blob URL');"
    }

    @Throws(IOException::class)
    private fun convertBase64StringToPdfAndStoreIt(base64: String) {
        //Log.e("BASE 64", base64);
        val notificationId = 1
        var dwldsPath: File
        var pdfAsBytes: ByteArray?
        var fileType: String
        val fileName = "ThomasReports"
        val currentDateTime = SimpleDateFormat("dd-MM-yyy HH:mm", Locale.US).format(
            Date()
        )
            .replace(":".toRegex(), "_")
            .replace(" ".toRegex(), "_")
            .replace(",".toRegex(), "")
        try {
            dwldsPath = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ).toString() + "/" + fileName + currentDateTime + "_.pdf")
            pdfAsBytes =
                Base64.decode(base64.replaceFirst("^data:application/pdf;base64,".toRegex(), ""), 0)
            fileType = "pdf"
        } catch (e: IllegalArgumentException) {
            dwldsPath = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ).toString() + "/" + fileName + currentDateTime + "_.xls")
            pdfAsBytes =
                Base64.decode(base64.replaceFirst("^data:application/xls;base64,".toRegex(), ""), 0)
            fileType = "xls"
        }
        try {
            FileOutputStream(dwldsPath).use { fos ->
                fos.write(pdfAsBytes)
                fos.flush()
            }
        } catch (e: IOException) {
            println(e.localizedMessage)
        }
        if (dwldsPath.exists()) {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            val apkURI = FileProvider.getUriForFile(context,
                context.applicationContext.packageName + ".provider",dwldsPath)
            if (fileType == "pdf") {
                intent.setDataAndType(apkURI,
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension("pdf"))
            } else {
                intent.setDataAndType(apkURI,
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension("xls"))
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val pendingIntent = PendingIntent.getActivity(context,1,intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val channelId = "MYCHANNEL"
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannel =
                NotificationChannel(channelId, "name", NotificationManager.IMPORTANCE_LOW)
            val notification = Notification.Builder(context, channelId)
                .setContentText("You have got something new!")
                .setContentTitle("Report downloaded")
                .setContentIntent(pendingIntent)
                .setChannelId(channelId)
                .setSmallIcon(R.drawable.sym_action_chat)
                .build()
            notificationManager.createNotificationChannel(notificationChannel)
            notificationManager.notify(notificationId, notification)
        }
        Toast.makeText(context, "Reporte descargado con éxito!", Toast.LENGTH_LONG).show()
    }

}
