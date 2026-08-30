package com.aznup.fluxoplus;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER = 1001;
    private int bottomInsetPx = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        webView.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            bottomInsetPx = insets.getSystemWindowInsetBottom();
            // Mantém o conteúdo abaixo da barra de status.
            // A barra de navegação inferior é tratada dentro do HTML com o inset real do aparelho.
            v.setPadding(0, top, 0, 0);
            applyWebMetrics();
            return insets;
        });
        webView.requestApplyInsets();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyWebMetrics();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void applyWebMetrics() {
        if (webView == null) return;
        final int inset = bottomInsetPx;
        final String version = BuildConfig.VERSION_NAME;
        webView.post(() -> webView.evaluateJavascript(
            "document.documentElement.style.setProperty('--android-bottom-inset','" + inset + "px');" +
            "var n=document.querySelector('nav');if(n){n.style.bottom='calc(" + inset + "px + 8px)';n.style.left='10px';n.style.right='10px';n.style.borderRadius='18px';n.style.boxShadow='0 4px 18px #0002';}" +
            "var f=document.querySelector('.fab');if(f){f.style.bottom='calc(" + inset + "px + 94px)';}" +
            "var m=document.querySelector('main');if(m){m.style.paddingBottom='calc(" + inset + "px + 130px)';}" +
            "var h=document.querySelector('header');var e=document.getElementById('appVersion');" +
            "if(h&&!e){e=document.createElement('span');e.id='appVersion';e.style.cssText='position:absolute;right:14px;top:18px;background:#ffffff22;border:1px solid #ffffff33;border-radius:999px;padding:6px 10px;font-size:12px;font-weight:800;color:#fff;white-space:nowrap';h.appendChild(e);}" +
            "if(e){e.textContent='Versão " + version + "';}",
            null
        ));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
