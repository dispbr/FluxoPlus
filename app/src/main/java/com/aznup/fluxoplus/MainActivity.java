package com.aznup.fluxoplus;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER = 1001;
    private static final String APP_VERSION = "1.0.5";
    private SharedPreferences prefs;
    private boolean biometricPromptOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("fluxoplus_native", MODE_PRIVATE);
        webView = findViewById(R.id.webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("if(typeof refreshNativeInfo==='function')refreshNativeInfo();", null);
                if (prefs.getBoolean("biometric_enabled", false) && !biometricPromptOpen) {
                    promptBiometric(false);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String getVersion() { return APP_VERSION; }

        @JavascriptInterface
        public boolean isBiometricEnabled() { return prefs.getBoolean("biometric_enabled", false); }

        @JavascriptInterface
        public void enableBiometric() { runOnUiThread(() -> promptBiometric(true)); }

        @JavascriptInterface
        public void requestBiometricLogin() { runOnUiThread(() -> promptBiometric(false)); }
    }

    private void promptBiometric(boolean enabling) {
        if (biometricPromptOpen) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            if (enabling) sendBiometricEnabled(false, "Biometria requer Android 9 ou superior.");
            return;
        }

        try {
            biometricPromptOpen = true;
            CancellationSignal signal = new CancellationSignal();

            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle(enabling ? "Ativar biometria" : "Entrar no Fluxo+")
                    .setSubtitle(enabling ? "Confirme sua biometria para ativar" : "Use sua biometria cadastrada no aparelho")
                    .setNegativeButton("Usar PIN", getMainExecutor(), (dialog, which) -> {
                        biometricPromptOpen = false;
                        if (enabling) sendBiometricEnabled(false, "Ativação cancelada.");
                    })
                    .build();

            prompt.authenticate(signal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    biometricPromptOpen = false;
                    if (enabling) {
                        prefs.edit().putBoolean("biometric_enabled", true).apply();
                        sendBiometricEnabled(true, "Biometria ativada.");
                    } else {
                        webView.evaluateJavascript("if(typeof window.onBiometricLogin==='function')window.onBiometricLogin();", null);
                    }
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    biometricPromptOpen = false;
                    if (enabling) {
                        sendBiometricEnabled(false, errString == null ? "Não foi possível usar a biometria." : errString.toString());
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                }
            });
        } catch (SecurityException e) {
            biometricPromptOpen = false;
            prefs.edit().putBoolean("biometric_enabled", false).apply();
            if (enabling) sendBiometricEnabled(false, "Permissão de biometria indisponível.");
        } catch (Exception e) {
            biometricPromptOpen = false;
            if (enabling) sendBiometricEnabled(false, "Biometria indisponível neste aparelho.");
        }
    }

    private void sendBiometricEnabled(boolean ok, String message) {
        String safe = message == null ? "" : message.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        webView.evaluateJavascript("if(typeof window.onBiometricEnabled==='function')window.onBiometricEnabled(" + ok + ",'" + safe + "');", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
