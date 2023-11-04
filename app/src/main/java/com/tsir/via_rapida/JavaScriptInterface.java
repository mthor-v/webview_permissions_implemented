package com.tsir.via_rapida;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class JavaScriptInterface {
    private final Context context;

    public JavaScriptInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void getBase64FromBlobData(String base64Data) throws IOException {
        convertBase64StringToPdfAndStoreIt(base64Data);
    }
    public static String getBase64StringFromBlobUrl(String blobUrl) {
        if(blobUrl.startsWith("blob")){
            return "javascript: var xhr = new XMLHttpRequest();" +
                    "xhr.open('GET', '"+ blobUrl +"', true);" +
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
                    "xhr.send();";
        }
        return "javascript: console.log('It is not a Blob URL');";
    }
    private void convertBase64StringToPdfAndStoreIt(String base64) throws IOException {
        //Log.e("BASE 64", base64);
        final int notificationId = 1;
        File dwldsPath;
        byte[] pdfAsBytes;
        String fileType;
        String fileName = "Generic";
        String currentDateTime = new SimpleDateFormat("dd-MM-yyy HH:mm", Locale.US).format(new Date())
                .replaceAll(":","_")
                .replaceAll(" ","_")
                .replaceAll(",","");
        try {
            dwldsPath = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS) + "/".concat(fileName).concat(currentDateTime).concat("_.pdf"));
            pdfAsBytes = Base64.decode(base64.replaceFirst("^data:application/pdf;base64,", ""), 0);
            fileType = "pdf";
        }catch (IllegalArgumentException e){
            dwldsPath = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS) + "/".concat(fileName).concat(currentDateTime).concat("_.xls"));
            pdfAsBytes = Base64.decode(base64.replaceFirst("^data:application/xls;base64,", ""), 0);
            fileType = "xls";
        }

        try (FileOutputStream fos = new FileOutputStream(dwldsPath)) {
            fos.write(pdfAsBytes);
            fos.flush();
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }

        if (dwldsPath.exists()) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            Uri apkURI = FileProvider.getUriForFile(context,context.getApplicationContext().getPackageName() + ".provider", dwldsPath);
            if(fileType.equals("pdf")){
                intent.setDataAndType(apkURI, MimeTypeMap.getSingleton().getMimeTypeFromExtension("pdf"));
            } else {
                intent.setDataAndType(apkURI, MimeTypeMap.getSingleton().getMimeTypeFromExtension("xls"));
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


            PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String CHANNEL_ID = "MYCHANNEL";
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel notificationChannel= new NotificationChannel(CHANNEL_ID,"name", NotificationManager.IMPORTANCE_LOW);
            Notification notification = new Notification.Builder(context,CHANNEL_ID)
                    .setContentText("You have got something new!")
                    .setContentTitle("Report downloaded")
                    .setContentIntent(pendingIntent)
                    .setChannelId(CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.sym_action_chat)
                    .build();
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
                notificationManager.notify(notificationId, notification);
            }

        }
        Toast.makeText(context, "Reporte descargado con éxito!", Toast.LENGTH_LONG).show();
    }
}
