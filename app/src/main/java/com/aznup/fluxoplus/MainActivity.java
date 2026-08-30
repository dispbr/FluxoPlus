package com.aznup.fluxoplus;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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
    private static final String APP_VERSION = "1.1.5";
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
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
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                super.onPageFinished(view,url);
                injectRecovery();
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params){
                if(fileCallback!=null) fileCallback.onReceiveValue(null);
                fileCallback=callback;
                try{ startActivityForResult(params.createIntent(),FILE_CHOOSER); return true; }
                catch(Exception e){ fileCallback=null; return false; }
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void injectRecovery(){
        String js="(function(){try{"+
        "var V='1.1.5';var av=document.getElementById('appVersion');if(av)av.textContent='Versão '+V;var sv=document.getElementById('settingsVersion');if(sv)sv.textContent=V;"+
        "function safeArray(k){try{var x=JSON.parse(localStorage.getItem(k)||'[]');return Array.isArray(x)?x:[];}catch(e){return [];}}"+
        "window.bills=safeArray('fluxo_bills');window.expenses=safeArray('fluxo_expenses');"+
        "window.showView=function(id,btn){try{document.querySelectorAll('.view').forEach(function(v){v.classList.remove('active');});var v=document.getElementById(id);if(v)v.classList.add('active');document.querySelectorAll('nav button').forEach(function(b){b.classList.remove('active');});if(btn)btn.classList.add('active');var h=document.getElementById('headerSub');if(h)h.textContent=({home:'Visão do mês',bills:'Contas mensais',imports:'Faturas e extratos',settings:'Configurações'})[id]||'Fluxo+';}catch(e){}};"+
        "var nav=document.querySelectorAll('nav button');var ids=['home','bills','imports','settings'];nav.forEach(function(b,i){b.onclick=function(ev){ev.preventDefault();window.showView(ids[i],b);};});"+
        "function msg(t,c){var e=document.getElementById('loginMsg');if(e){e.textContent=t||'';e.style.color=c||'#b42318';e.style.fontWeight='700';}}"+
        "window.enter=function(){try{var p=document.getElementById('pin'),v=p?p.value.trim():'';if(v.length<4){msg('Digite seu PIN com pelo menos 4 caracteres.');return;}var saved=localStorage.getItem('fluxo_pin');if(saved&&saved!==v){msg('PIN incorreto.');if(p){p.value='';p.focus();}return;}if(!saved)localStorage.setItem('fluxo_pin',v);var l=document.getElementById('login');if(l)l.classList.add('hidden');try{if(typeof render==='function')render();}catch(e){} }catch(e){msg('Erro ao acessar. Seus dados não foram apagados.');}};"+
        "var pin=document.getElementById('pin');if(pin)pin.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();window.enter();}});"+
        "var bio=document.getElementById('bioStatus');if(bio)bio.textContent='Biometria temporariamente desativada no modo de recuperação.';"+
        "window.enableBiometric=function(){var b=document.getElementById('bioStatus');if(b)b.textContent='Biometria temporariamente desativada nesta versão de recuperação.';};"+
        "window.pickPdf=function(){var p=document.getElementById('pdfStatus');if(p)p.textContent='Leitor de PDF temporariamente desativado nesta versão de recuperação.';};"+
        "try{if(typeof render==='function')render();}catch(e){}"+
        "}catch(e){}})();";
        webView.evaluateJavascript(js,null);
    }

    public class AndroidBridge {
        @JavascriptInterface public String getVersion(){return APP_VERSION;}
        @JavascriptInterface public boolean isBiometricEnabled(){return false;}
        @JavascriptInterface public void enableBiometric(){}
        @JavascriptInterface public void requestBiometricLogin(){}
        @JavascriptInterface public void pickCreditCardPdf(String bank){}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==FILE_CHOOSER&&fileCallback!=null){
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode,data));
            fileCallback=null;
        }
    }
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}