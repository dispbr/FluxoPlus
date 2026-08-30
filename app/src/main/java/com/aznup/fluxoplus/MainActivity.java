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
    private static final int FILE_CHOOSER=1001, PDF_CHOOSER=2002;
    private static final String APP_VERSION="1.1.8";
    private SharedPreferences prefs;
    private boolean biometricPromptOpen=false, pdfBoxInitialized=false;
    private String pendingPdfBank="nubank";
    private final ExecutorService pdfExecutor=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_main);
        prefs=getSharedPreferences("fluxoplus_native",MODE_PRIVATE);webView=findViewById(R.id.webView);
        WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(),"AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);injectStableUi();}});
        webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;try{startActivityForResult(params.createIntent(),FILE_CHOOSER);return true;}catch(Exception e){fileCallback=null;return false;}}});
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void injectStableUi(){
        String js="(function(){try{"+
        "var V='1.1.8';var av=document.getElementById('appVersion');if(av)av.textContent='Versão '+V;var sv=document.getElementById('settingsVersion');if(sv)sv.textContent=V;"+
        "function safeArray(k){try{var x=JSON.parse(localStorage.getItem(k)||'[]');return Array.isArray(x)?x:[];}catch(e){return [];}}window.bills=safeArray('fluxo_bills');window.expenses=safeArray('fluxo_expenses');"+
        "window.showView=function(id,btn){try{document.querySelectorAll('.view').forEach(function(v){v.classList.remove('active');});var v=document.getElementById(id);if(v)v.classList.add('active');document.querySelectorAll('nav button').forEach(function(b){b.classList.remove('active');});if(btn)btn.classList.add('active');var h=document.getElementById('headerSub');if(h)h.textContent=({home:'Visão do mês',bills:'Contas mensais',imports:'Faturas e extratos',settings:'Configurações'})[id]||'Fluxo+';}catch(e){}};"+
        "var nav=document.querySelectorAll('nav button'),ids=['home','bills','imports','settings'];nav.forEach(function(b,i){b.onclick=function(ev){ev.preventDefault();window.showView(ids[i],b);};});"+
        "function msg(t,c){var e=document.getElementById('loginMsg');if(e){e.textContent=t||'';e.style.color=c||'#b42318';e.style.fontWeight='700';}}"+
        "window.enter=function(){try{var p=document.getElementById('pin'),v=p?p.value.trim():'';if(v.length<4){msg('Digite seu PIN com pelo menos 4 caracteres.');return;}var saved=localStorage.getItem('fluxo_pin');if(saved&&saved!==v){msg('PIN incorreto.');if(p){p.value='';p.focus();}return;}if(!saved)localStorage.setItem('fluxo_pin',v);var l=document.getElementById('login');if(l)l.classList.add('hidden');try{if(typeof render==='function')render();}catch(e){}}catch(e){msg('Erro ao acessar. Seus dados não foram apagados.');}};"+
        "var pin=document.getElementById('pin');if(pin&&!pin.dataset.fixed){pin.dataset.fixed='1';pin.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();window.enter();}});}"+
        "window.onBiometricEnabled=function(ok,text){var b=document.getElementById('bioStatus');if(b){b.textContent=text||'';b.style.color=ok?'#067647':'#b42318';}if(ok)setupBioButton();};window.onBiometricError=function(text){msg(text||'Não foi possível usar a biometria. Use o PIN.');};window.onBiometricLogin=function(){msg('Biometria reconhecida. Entrando...','#067647');var l=document.getElementById('login');if(l)l.classList.add('hidden');try{if(typeof render==='function')render();}catch(e){}};"+
        "window.enableBiometric=function(){try{if(window.AndroidBridge&&AndroidBridge.enableBiometric){var b=document.getElementById('bioStatus');if(b)b.textContent='Aguardando confirmação biométrica...';AndroidBridge.enableBiometric();}}catch(e){window.onBiometricEnabled(false,'Biometria indisponível.');}};"+
        "function setupBioButton(){try{if(!(window.AndroidBridge&&AndroidBridge.isBiometricEnabled&&AndroidBridge.isBiometricEnabled()))return;var box=document.querySelector('.loginbox');if(!box||document.getElementById('bioLoginBtn'))return;var bt=document.createElement('button');bt.id='bioLoginBtn';bt.className='btn secondary';bt.style.cssText='width:100%;margin-top:8px';bt.textContent='Entrar com biometria';bt.onclick=function(){try{AndroidBridge.requestBiometricLogin();}catch(e){window.onBiometricError('Biometria indisponível. Use o PIN.');}};var m=document.getElementById('loginMsg');box.insertBefore(bt,m);}catch(e){}}setupBioButton();"+
        "window.pickPdf=function(){try{var bank=document.getElementById('pdfBank'),p=document.getElementById('pdfStatus'),a=document.getElementById('pdfAnalysis');if(p)p.textContent='Abrindo seletor de PDF...';if(a)a.innerHTML='';if(window.AndroidBridge&&AndroidBridge.pickCreditCardPdf)AndroidBridge.pickCreditCardPdf(bank?bank.value:'outro');else if(p)p.textContent='Importação PDF indisponível.';}catch(e){var p=document.getElementById('pdfStatus');if(p)p.textContent='Não foi possível abrir o PDF.';}};"+
        "var card=document.querySelector('#settings .card');if(card&&!document.getElementById('restoreBackup')){var input=document.createElement('input');input.id='restoreBackup';input.type='file';input.accept='.json,application/json';input.style.display='none';var bt=document.createElement('button');bt.className='btn secondary';bt.style.cssText='width:100%;margin-bottom:8px';bt.textContent='Restaurar backup';bt.onclick=function(){input.click();};var st=document.createElement('div');st.id='restoreStatus';st.className='muted';st.style.marginBottom='10px';var danger=card.querySelector('.btn.danger');card.insertBefore(input,danger);card.insertBefore(bt,danger);card.insertBefore(st,danger);input.onchange=function(){var f=input.files&&input.files[0];if(!f)return;var r=new FileReader();r.onload=function(){try{var d=JSON.parse(r.result);var nb=Array.isArray(d.bills)?d.bills:(Array.isArray(d.contas)?d.contas:null);var ne=Array.isArray(d.expenses)?d.expenses:(Array.isArray(d.gastos)?d.gastos:[]);if(!nb)throw new Error();if(!confirm('Restaurar '+nb.length+' contas? Os dados atuais serão substituídos.'))return;localStorage.setItem('fluxo_bills',JSON.stringify(nb));localStorage.setItem('fluxo_expenses',JSON.stringify(ne));if(d.pin)localStorage.setItem('fluxo_pin',String(d.pin));window.bills=nb;window.expenses=ne;st.textContent='Backup restaurado com sucesso.';st.style.color='#067647';try{if(typeof render==='function')render();}catch(e){}}catch(e){st.textContent='Arquivo de backup inválido. Nenhum dado foi alterado.';st.style.color='#b42318';}};r.readAsText(f);};}"+
        "try{if(typeof render==='function')render();}catch(e){}"+
        "}catch(e){}})();";webView.evaluateJavascript(js,null);
    }

    public class AndroidBridge{
        @JavascriptInterface public String getVersion(){return APP_VERSION;}
        @JavascriptInterface public boolean isBiometricEnabled(){return prefs.getBoolean("biometric_enabled",false);}
        @JavascriptInterface public void enableBiometric(){runOnUiThread(()->promptBiometric(true));}
        @JavascriptInterface public void requestBiometricLogin(){runOnUiThread(()->promptBiometric(false));}
        @JavascriptInterface public void pickCreditCardPdf(String bank){runOnUiThread(()->openPdfPicker(bank));}
    }

    private synchronized void ensurePdfBox(){if(!pdfBoxInitialized){PDFBoxResourceLoader.init(getApplicationContext());pdfBoxInitialized=true;}}
    private void openPdfPicker(String bank){pendingPdfBank=(bank==null||bank.trim().isEmpty())?"outro":bank.trim();try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");startActivityForResult(i,PDF_CHOOSER);}catch(Exception e){sendPdfError("Não foi possível abrir o seletor de PDF.");}}
    private void readPdf(Uri uri,String bank){pdfExecutor.execute(()->{try{ensurePdfBox();try(InputStream in=getContentResolver().openInputStream(uri);PDDocument doc=PDDocument.load(in)){PDFTextStripper stripper=new PDFTextStripper();stripper.setSortByPosition(true);String text=stripper.getText(doc),name=getDisplayName(uri);String js="if(typeof window.onPdfText==='function')window.onPdfText("+JSONObject.quote(bank)+","+JSONObject.quote(text)+","+JSONObject.quote(name)+");";runOnUiThread(()->webView.evaluateJavascript(js,null));}}catch(Exception e){runOnUiThread(()->sendPdfError("Não foi possível ler este PDF. Verifique se a fatura não está protegida por senha."));}});}
    private String getDisplayName(Uri uri){String name="fatura.pdf";Cursor c=null;try{c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null&&c.moveToFirst()){int x=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(x>=0)name=c.getString(x);}}catch(Exception ignored){}finally{if(c!=null)c.close();}return name==null?"fatura.pdf":name;}
    private void sendPdfError(String m){webView.evaluateJavascript("if(typeof window.onPdfError==='function')window.onPdfError("+JSONObject.quote(m)+");",null);}

    private void promptBiometric(boolean enabling){if(biometricPromptOpen)return;if(Build.VERSION.SDK_INT<Build.VERSION_CODES.P){if(enabling)sendBioEnabled(false,"Biometria requer Android 9 ou superior.");else sendBioError("Biometria indisponível. Use o PIN.");return;}try{biometricPromptOpen=true;CancellationSignal signal=new CancellationSignal();BiometricPrompt prompt=new BiometricPrompt.Builder(this).setTitle(enabling?"Ativar biometria":"Entrar no Fluxo+").setSubtitle(enabling?"Confirme sua biometria para ativar":"Use a biometria cadastrada no aparelho").setNegativeButton("Usar PIN",getMainExecutor(),(d,w)->{biometricPromptOpen=false;if(enabling)sendBioEnabled(false,"Ativação cancelada.");else sendBioError("Autenticação cancelada. Use o PIN.");}).build();prompt.authenticate(signal,getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){super.onAuthenticationSucceeded(r);biometricPromptOpen=false;if(enabling){prefs.edit().putBoolean("biometric_enabled",true).apply();sendBioEnabled(true,"Biometria ativada com sucesso.");}else webView.evaluateJavascript("if(typeof window.onBiometricLogin==='function')window.onBiometricLogin();",null);}@Override public void onAuthenticationError(int code,CharSequence err){super.onAuthenticationError(code,err);biometricPromptOpen=false;String m=err==null?"Não foi possível usar a biometria.":err.toString();if(enabling)sendBioEnabled(false,m);else sendBioError(m+" Use o PIN.");}@Override public void onAuthenticationFailed(){super.onAuthenticationFailed();if(!enabling)sendBioError("Biometria não reconhecida. Tente novamente ou use o PIN.");}});}catch(Exception e){biometricPromptOpen=false;if(enabling)sendBioEnabled(false,"Biometria indisponível neste aparelho.");else sendBioError("Biometria indisponível. Use o PIN.");}}
    private void sendBioEnabled(boolean ok,String m){String safe=m==null?"":m.replace("\\","\\\\").replace("'","\\'").replace("\n"," ");webView.evaluateJavascript("if(typeof window.onBiometricEnabled==='function')window.onBiometricEnabled("+ok+",'"+safe+"');",null);}
    private void sendBioError(String m){String safe=m==null?"":m.replace("\\","\\\\").replace("'","\\'").replace("\n"," ");webView.evaluateJavascript("if(typeof window.onBiometricError==='function')window.onBiometricError('"+safe+"');",null);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==PDF_CHOOSER){if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}readPdf(uri,pendingPdfBank);}else sendPdfError("Seleção de PDF cancelada.");return;}if(requestCode==FILE_CHOOSER&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode,data));fileCallback=null;}}
    @Override protected void onDestroy(){pdfExecutor.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
