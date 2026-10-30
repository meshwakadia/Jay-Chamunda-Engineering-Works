package com.jaychamunda.bill;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private File writeCache(String b64, String name) throws Exception {
        File dir = new File(getCacheDir(), "bills");
        dir.mkdirs();
        File f = new File(dir, name);
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(Base64.decode(b64, Base64.DEFAULT)); }
        return f;
    }

    private void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show()); }

    class Bridge {
        @JavascriptInterface
        public void savePdf(String b64, String name) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JCEW Bills");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(data); }
                    toast("Saved in Downloads/JCEW Bills");
                } else {
                    File f = writeCache(b64, name);
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, "com.jaychamunda.bill.files", f);
                    Intent i = new Intent(Intent.ACTION_SEND).setType("application/pdf")
                            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Save bill"));
                }
            } catch (Exception e) { toast("Could not save PDF"); }
        }

        @JavascriptInterface
        public void shareWhatsApp(String b64, String name, String phone, String msg) {
            try {
                File f = writeCache(b64, name);
                Uri uri = FileProvider.getUriForFile(MainActivity.this, "com.jaychamunda.bill.files", f);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.putExtra(Intent.EXTRA_TEXT, msg);
                if (phone != null && phone.length() >= 10) i.putExtra("jid", phone + "@s.whatsapp.net");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String pkg = installed("com.whatsapp") ? "com.whatsapp" : installed("com.whatsapp.w4b") ? "com.whatsapp.w4b" : null;
                if (pkg != null) { i.setPackage(pkg); startActivity(i); }
                else startActivity(Intent.createChooser(i, "Send bill"));
            } catch (Exception e) { toast("Could not share PDF"); }
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                pm.print("Jay Chamunda Bill", web.createPrintDocumentAdapter("Jay Chamunda Bill"),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
            });
        }
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }
}
