package com.aznup.fluxoplus;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER = 1001;
    private static final int PDF_CHOOSER = 2002;
    private static final String APP_VERSION = "1.1.2";
    private SharedPreferences prefs;
    private boolean biometricPromptOpen = false;
    private String pendingPdfBank = "nubank";
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PDFBoxResourceLoader.init(getApplicationContext());
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
                injectLoginFix();
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

    private void injectLoginFix() {
        String js = "(function(){" +
                "function msg(t,c){var e=document.getElementById('loginMsg');if(e){e.textContent=t||'';e.style.color=c||'#b42318';e.style.fontWeight='700';e.style.minHeight='20px';}}" +
                "window.enter=function(){try{var p=document.getElementById('pin');var v=p?p.value.trim():'';if(!v||v.length<4){msg('Digite seu PIN com pelo menos 4 caracteres.');return;}var saved=localStorage.getItem('fluxo_pin');if(!saved){localStorage.setItem('fluxo_pin',v);msg('PIN criado com sucesso.','#067647');}else if(saved!==v){msg('PIN incorreto. Tente novamente.');if(p){p.value='';p.focus();}return;}var l=document.getElementById('login');if(l)l.classList.add('hidden');if(typeof render==='function')render();}catch(ex){msg('Erro no login: '+(ex&&ex.message?ex.message:'falha desconhecida'));}};" +
                "var p=document.getElementById('pin');if(p&&!p.dataset.loginFix){p.dataset.loginFix='1';p.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();window.enter();}});}" +
                "window.onBiometricError=function(t){msg(t||'Não foi possível autenticar pela biometria. Use o PIN para entrar.');};" +
                "window.onBiometricLogin=function(){msg('Biometria reconhecida. Entrando...','#067647');setTimeout(function(){var l=document.getElementById('login');if(l)l.classList.add('hidden');if(typeof render==='function')render();},100);};" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public class AndroidBridge {
        @JavascriptInterface public String getVersion() { return APP_VERSION; }
        @JavascriptInterface public boolean isBiometricEnabled() { return prefs.getBoolean("biometric_enabled", false); }
        @JavascriptInterface public void enableBiometric() { runOnUiThread(() -> promptBiometric(true)); }
        @JavascriptInterface public void requestBiometricLogin() { runOnUiThread(() -> promptBiometric(false)); }
        @JavascriptInterface public void pickCreditCardPdf(String bank) { runOnUiThread(() -> openPdfPicker(bank)); }
    }

    private void openPdfPicker(String bank) {
        pendingPdfBank = (bank == null || bank.trim().isEmpty()) ? "outro" : bank.trim();
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            startActivityForResult(intent, PDF_CHOOSER);
        } catch (Exception e) {
            sendPdfError("Não foi possível abrir o seletor de PDF.");
        }
    }

    private void readPdf(Uri uri, String bank) {
        pdfExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri);
                 PDDocument document = PDDocument.load(input)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String text = stripper.getText(document);
                String name = getDisplayName(uri);
                String js = "if(typeof window.onPdfText==='function')window.onPdfText(" +
                        JSONObject.quote(bank) + "," + JSONObject.quote(text) + "," + JSONObject.quote(name) + ");";
                runOnUiThread(() -> webView.evaluateJavascript(js, null));
            } catch (Exception e) {
                runOnUiThread(() -> sendPdfError("Não foi possível ler este PDF. Verifique se a fatura não está protegida por senha."));
            }
        });
    }

    private String getDisplayName(Uri uri) {
        String name = "fatura.pdf";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name == null ? "fatura.pdf" : name;
    }

    private void sendPdfError(String message) {
        String js = "if(typeof window.onPdfError==='function')window.onPdfError(" + JSONObject.quote(message) + ");";
        webView.evaluateJavascript(js, null);
    }

    private void promptBiometric(boolean enabling) {
        if (biometricPromptOpen) {
            if (!enabling) sendBiometricLoginError("A autenticação biométrica já está aberta.");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            String msg = "Biometria requer Android 9 ou superior.";
            if (enabling) sendBiometricEnabled(false, msg); else sendBiometricLoginError(msg + " Use o PIN para entrar.");
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
                        else sendBiometricLoginError("Autenticação cancelada. Use o PIN para entrar.");
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
                    String msg = (errString == null || errString.length() == 0) ? "Não foi possível usar a biometria." : errString.toString();
                    if (enabling) sendBiometricEnabled(false, msg); else sendBiometricLoginError(msg + " Use o PIN para entrar.");
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    if (!enabling) sendBiometricLoginError("Biometria não reconhecida. Tente novamente ou use o PIN.");
                }
            });
        } catch (SecurityException e) {
            biometricPromptOpen = false;
            prefs.edit().putBoolean("biometric_enabled", false).apply();
            String msg = "Permissão de biometria indisponível.";
            if (enabling) sendBiometricEnabled(false, msg); else sendBiometricLoginError(msg + " Use o PIN para entrar.");
        } catch (Exception e) {
            biometricPromptOpen = false;
            String msg = "Biometria indisponível neste aparelho.";
            if (enabling) sendBiometricEnabled(false, msg); else sendBiometricLoginError(msg + " Use o PIN para entrar.");
        }
    }

    private void sendBiometricEnabled(boolean ok, String message) {
        String safe = message == null ? "" : message.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        webView.evaluateJavascript("if(typeof window.onBiometricEnabled==='function')window.onBiometricEnabled(" + ok + ",'" + safe + "');", null);
    }

    private void sendBiometricLoginError(String message) {
        String safe = message == null ? "" : message.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        webView.evaluateJavascript("if(typeof window.onBiometricError==='function')window.onBiometricError('" + safe + "');", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PDF_CHOOSER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (Exception ignored) {}
                readPdf(uri, pendingPdfBank);
            } else {
                sendPdfError("Seleção de PDF cancelada.");
            }
            return;
        }
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        pdfExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
