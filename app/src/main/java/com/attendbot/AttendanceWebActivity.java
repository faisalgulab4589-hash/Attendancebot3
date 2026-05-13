package com.attendbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AttendanceWebActivity:
 * App ke andar full WebView — koi external browser nahi.
 * Agar macro saved hai → replay karo
 * Agar nahi   → direct JS se auto-fill karo
 * Location popup → auto-allow
 * CHECK IN / CHECK OUT → JS se click
 * Sab smooth, koi loop nahi, koi bाhar jane nahi
 */
public class AttendanceWebActivity extends Activity {

    static final String TAG = "AttendWebAct";
    WebView webView;
    Handler handler;
    SharedPreferences prefs;
    String mode;
    boolean hasMacro;
    boolean taskDone = false;

    // Macro replay state
    JSONArray macroEvents;
    int macroIndex = 0;

    TextView tvTopStatus;
    ProgressBar topProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("AttendBot", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        mode     = getIntent().getStringExtra("mode");
        hasMacro = getIntent().getBooleanExtra("hasMacro", false);
        if (mode == null) mode = "checkin";

        // Load macro if available
        if (hasMacro) {
            String key = mode.equals("checkin") ? "macro_checkin" : "macro_checkout";
            try { macroEvents = new JSONArray(prefs.getString(key, "[]")); }
            catch (Exception e) { macroEvents = new JSONArray(); hasMacro = false; }
        }

        buildUI();
    }

    // ── UI ──────────────────────────────────────────────────────────────────
    void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Top status bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(0xFF1a7a4a);
        topBar.setPadding(16, 40, 16, 10);

        String label = mode.equals("checkin") ? "✅ CHECK IN" : "🚪 CHECK OUT";
        String subLabel = hasMacro ? "Macro چل رہی ہے..." : "خودکار لاگ ان ہو رہا ہے...";

        tvTopStatus = new TextView(this);
        tvTopStatus.setText(label + " — " + subLabel);
        tvTopStatus.setTextColor(Color.WHITE);
        tvTopStatus.setTextSize(14);
        tvTopStatus.setTypeface(null, android.graphics.Typeface.BOLD);

        topProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topProgress.setIndeterminate(true);
        LinearLayout.LayoutParams pbP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 6);
        pbP.topMargin = 8;
        topProgress.setLayoutParams(pbP);

        topBar.addView(tvTopStatus);
        topBar.addView(topProgress);

        // WebView
        webView = new WebView(this);
        LinearLayout.LayoutParams wvP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(wvP);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            boolean firstLoad = true;

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url);
                if (taskDone) return;

                // Inject location auto-allow JS every page
                injectLocationAllow(view);

                // Wait for page to fully render
                if (firstLoad) {
                    firstLoad = false;
                    handler.postDelayed(() -> {
                        if (hasMacro && macroEvents.length() > 0) {
                            macroIndex = 0;
                            replayNextStep();
                        } else {
                            autoFillLogin();
                        }
                    }, 2500);
                }
                // After login redirect — check if we're on dashboard
                else {
                    handler.postDelayed(() -> {
                        if (!taskDone) checkAndClickAttendance(view);
                    }, 2000);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req,
                                        WebResourceError err) {
                if (req.isForMainFrame()) {
                    Log.e(TAG, "WebView error: " + err.getDescription());
                    handler.postDelayed(() -> showError("صفحہ لوڈ نہیں ہوا: " + err.getDescription()), 500);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // AUTO-ALLOW location every single time it's asked
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
                Log.d(TAG, "Location permission → auto-allow: " + origin);
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, "JS: " + cm.message());
                return true;
            }
        });

        root.addView(topBar);
        root.addView(webView);
        setContentView(root);

        // Load the URL — ONCE only
        String url = prefs.getString("url", "https://lms.gims.tech/");
        webView.loadUrl(url);
        setStatus("ویب سائٹ لوڈ ہو رہی ہے...");
    }

    // ── Inject JS to auto-allow location in page dialogs ───────────────────
    void injectLocationAllow(WebView view) {
        String js =
            "if(navigator.permissions){" +
            "  navigator.permissions.query({name:'geolocation'}).then(function(r){" +
            "    console.log('geo-status:'+r.state);" +
            "  });" +
            "}";
        view.evaluateJavascript(js, null);
    }

    // ── AUTO FILL: Direct JS login (no macro) ───────────────────────────────
    void autoFillLogin() {
        setStatus("لاگ ان فارم بھرا جا رہا ہے...");

        String email    = prefs.getString("email", "");
        String password = prefs.getString("password", "");
        String esc_email = escJs(email);
        String esc_pass  = escJs(password);

        String js =
            "(function(){" +
            // --- Find email field ---
            "  var ef=document.querySelector('input[type=email],input[name*=email],input[id*=email],input[placeholder*=email],input[placeholder*=Email]');" +
            "  if(!ef){var ins=document.querySelectorAll('input');for(var i=0;i<ins.length;i++){var p=(ins[i].placeholder||'').toLowerCase();var n=(ins[i].name||'').toLowerCase();if(p.includes('email')||n.includes('email')||p.includes('user')||n.includes('user')){ef=ins[i];break;}}}" +
            // --- Find password field ---
            "  var pf=document.querySelector('input[type=password]');" +
            // --- Find role select ---
            "  var rs=document.querySelector('select[name*=role],select[id*=role],select');" +
            // --- Fill email ---
            "  function fillVal(el,val){" +
            "    el.focus();" +
            "    var nv=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');" +
            "    if(nv)nv.set.call(el,val); else el.value=val;" +
            "    el.dispatchEvent(new Event('input',{bubbles:true}));" +
            "    el.dispatchEvent(new Event('change',{bubbles:true}));" +
            "    el.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true}));" +
            "  }" +
            "  var res={};" +
            "  if(ef){fillVal(ef,'" + esc_email + "');res.email='ok';}else{res.email='notfound';}" +
            "  if(pf){fillVal(pf,'" + esc_pass  + "');res.pass='ok';}else{res.pass='notfound';}" +
            // --- Select Staff in role dropdown ---
            "  if(rs){" +
            "    for(var j=0;j<rs.options.length;j++){" +
            "      if(rs.options[j].text.toLowerCase().includes('staff')||rs.options[j].value.toLowerCase().includes('staff')){" +
            "        rs.selectedIndex=j;" +
            "        rs.dispatchEvent(new Event('change',{bubbles:true}));" +
            "        res.role='staff-selected';break;" +
            "      }" +
            "    }" +
            "    if(!res.role)res.role='staff-notfound';" +
            "  }else{res.role='select-notfound';}" +
            "  return JSON.stringify(res);" +
            "})()";

        webView.evaluateJavascript(js, result -> {
            Log.d(TAG, "fillLogin: " + result);
            setStatus("Login button دبایا جا رہا ہے...");
            handler.postDelayed(() -> clickLoginBtn(), 1000);
        });
    }

    void clickLoginBtn() {
        String js =
            "(function(){" +
            "  var btns=document.querySelectorAll('button,input[type=submit],input[type=button],[role=button],.btn,a.btn');" +
            "  for(var i=0;i<btns.length;i++){" +
            "    var t=(btns[i].textContent||btns[i].value||'').toLowerCase().trim();" +
            "    if(t.includes('login')||t.includes('sign in')||t.includes('log in')||t.includes('submit')){" +
            "      btns[i].click();return 'clicked:'+t;" +
            "    }" +
            "  }" +
            "  var f=document.querySelector('form');" +
            "  if(f){f.submit();return 'form-submit';}" +
            "  return 'not-found';" +
            "})()";

        webView.evaluateJavascript(js, result -> {
            Log.d(TAG, "loginBtn: " + result);
            setStatus("لاگ ان ہو رہا ہے...");
            // Page will reload → onPageFinished → checkAndClickAttendance
        });
    }

    // ── After login: find CHECK IN or CHECK OUT ──────────────────────────────
    void checkAndClickAttendance(WebView view) {
        if (taskDone) return;
        String keyword  = mode.equals("checkin") ? "check in"  : "check out";
        String keyword2 = mode.equals("checkin") ? "checkin"   : "checkout";
        String keyword3 = mode.equals("checkin") ? "check-in"  : "check-out";

        setStatus((mode.equals("checkin") ? "CHECK IN" : "CHECK OUT") + " button تلاش ہو رہا ہے...");

        String js =
            "(function(){" +
            "  var all=document.querySelectorAll('button,a,input[type=button],input[type=submit],[role=button],[class*=btn],[class*=check]');" +
            "  for(var i=0;i<all.length;i++){" +
            "    var t=(all[i].textContent||all[i].value||all[i].innerText||'').toLowerCase().replace(/\\s+/g,' ').trim();" +
            "    if(t.includes('" + keyword + "')||t.includes('" + keyword2 + "')||t.includes('" + keyword3 + "')){" +
            "      all[i].scrollIntoView({block:'center'});" +
            "      all[i].click();" +
            "      return 'found-clicked:'+t;" +
            "    }" +
            "  }" +
            // Also try by class names common in attendance systems
            "  var byClass=document.querySelectorAll('[class*=checkin],[class*=checkout],[class*=attend],[id*=checkin],[id*=checkout]');" +
            "  if(byClass.length>0){byClass[0].scrollIntoView();byClass[0].click();return 'class-clicked';}" +
            "  return 'not-found:url='+window.location.href;" +
            "})()";

        view.evaluateJavascript(js, result -> {
            Log.d(TAG, "attend btn: " + result);
            if (result != null && result.contains("clicked")) {
                taskDone = true;
                onSuccess();
            } else {
                // Retry once after 3 more seconds
                handler.postDelayed(() -> retryAttendance(), 3000);
            }
        });
    }

    void retryAttendance() {
        if (taskDone) return;
        String keyword  = mode.equals("checkin") ? "check in"  : "check out";
        String keyword2 = mode.equals("checkin") ? "checkin"   : "checkout";

        String js =
            "(function(){" +
            "  var all=document.querySelectorAll('*');" +
            "  for(var i=0;i<all.length;i++){" +
            "    var t=(all[i].textContent||'').toLowerCase().trim();" +
            "    var tag=all[i].tagName.toLowerCase();" +
            "    if((tag==='button'||tag==='a'||all[i].onclick!=null)" +
            "       &&(t==='" + keyword + "'||t==='" + keyword2 + "')){" +
            "      all[i].click();return 'retry-clicked:'+t;" +
            "    }" +
            "  }" +
            "  return 'retry-not-found:body='+document.body.innerText.substring(0,200);" +
            "})()";

        webView.evaluateJavascript(js, result -> {
            Log.d(TAG, "retry: " + result);
            if (result != null && result.contains("clicked")) {
                taskDone = true;
                onSuccess();
            } else {
                // Show what's on screen so user can see
                setStatus("Button نہیں ملا — manual کریں یا macro record کریں");
                topProgress.setIndeterminate(false);
                topProgress.setProgress(0);
            }
        });
    }

    // ── MACRO REPLAY ─────────────────────────────────────────────────────────
    void replayNextStep() {
        if (taskDone || macroIndex >= macroEvents.length()) {
            onSuccess();
            return;
        }
        try {
            JSONObject ev = macroEvents.getJSONObject(macroIndex);
            String type     = ev.optString("type", "");
            String selector = ev.optString("selector", "");
            String xpath    = ev.optString("xpath", "");
            String value    = ev.optString("value", "");
            String text     = ev.optString("text", "");
            String optTxt   = ev.optString("optionText", "");

            // Substitute credentials
            if (value.equals("__PASSWORD__"))       value = prefs.getString("password", "");
            if (value.equals(prefs.getString("email",""))) value = prefs.getString("email","");

            setStatus("Step " + (macroIndex+1) + "/" + macroEvents.length() + ": " + type);
            String js = buildStepJS(type, selector, xpath, value, text, optTxt);

            final int thisIndex = macroIndex;
            webView.evaluateJavascript(js, result -> {
                Log.d(TAG, "macro[" + thisIndex + "] " + type + " → " + result);
                macroIndex++;
                int delay = type.equals("click") ? 1200 : 500;
                handler.postDelayed(this::replayNextStep, delay);
            });
        } catch (Exception e) {
            Log.e(TAG, "macro step err: " + e.getMessage());
            macroIndex++;
            handler.postDelayed(this::replayNextStep, 500);
        }
    }

    String buildStepJS(String type, String sel, String xpath, String val, String text, String optTxt) {
        String esel  = escJs(sel);
        String expth = escJs(xpath);
        String eval  = escJs(val);
        String etxt  = escJs(text);
        String eopt  = escJs(optTxt);

        String finder =
            "(function findEl(){" +
            "  var el=null;" +
            "  try{el=document.querySelector('" + esel + "');}catch(e){}" +
            "  if(el)return el;" +
            "  try{var xr=document.evaluate('" + expth + "',document,null,XPathResult.FIRST_ORDERED_NODE_TYPE,null);if(xr.singleNodeValue)return xr.singleNodeValue;}catch(e){}" +
            "  if('" + etxt + "'){var all=document.querySelectorAll('button,a,input[type=button],input[type=submit],[role=button]');for(var i=0;i<all.length;i++){var t=(all[i].textContent||all[i].value||'').trim();if(t.toLowerCase().includes('" + etxt.toLowerCase() + "'))return all[i];}}" +
            "  return null;" +
            "})()";

        switch (type) {
            case "click":
                return "(function(){var el="+finder+";if(!el)return 'nf:"+esel+"';el.scrollIntoView({block:'center'});el.click();['mousedown','mouseup','click'].forEach(function(et){el.dispatchEvent(new MouseEvent(et,{bubbles:true,cancelable:true}));});return 'ok:'+el.tagName;})()";
            case "type": case "input":
                return "(function(){var el="+finder+";if(!el)return 'nf:"+esel+"';el.focus();var nv=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');if(nv)nv.set.call(el,'"+eval+"');else el.value='"+eval+"';el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'typed';})()";
            case "select":
                return "(function(){var el="+finder+";if(!el)return 'nf';for(var i=0;i<el.options.length;i++){if(el.options[i].value==='"+eval+"'||el.options[i].text.toLowerCase().includes('"+eopt.toLowerCase()+"')){el.selectedIndex=i;el.dispatchEvent(new Event('change',{bubbles:true}));return 'sel:'+el.options[i].text;}}return 'opt-nf';})()";
            default:
                return "'skip:" + type + "'";
        }
    }

    // ── Success ───────────────────────────────────────────────────────────────
    void onSuccess() {
        taskDone = true;
        String now   = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date());
        String label = mode.equals("checkin") ? "Check In ✅" : "Check Out 🚪";

        prefs.edit().putString("lastAttendance", now + " (" + label + ")").apply();
        String history = prefs.getString("history", "");
        history += (history.isEmpty() ? "" : "|") + now + " (" + label + ")";
        prefs.edit().putString("history", history).apply();

        // Re-schedule tomorrow's alarm
        if (prefs.getBoolean("alarmEnabled", false)) {
            ScheduleHelper.scheduleAlarms(this);
        }

        runOnUiThread(() -> {
            topProgress.setIndeterminate(false);
            topProgress.setMax(1);
            topProgress.setProgress(1);
            setStatus(label + " مکمل! وقت: " + now);
            topProgress.setBackgroundColor(0xFF1a7a4a);

            handler.postDelayed(() -> {
                new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setMessage("حاضری کامیابی سے درج ہوگئی!\n⏰ " + now)
                    .setPositiveButton("ٹھیک ہے", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
            }, 1000);
        });
    }

    void showError(String msg) {
        runOnUiThread(() -> {
            topProgress.setIndeterminate(false);
            setStatus("خرابی: " + msg);
            new AlertDialog.Builder(this)
                .setTitle("خرابی")
                .setMessage(msg + "\n\nانٹرنیٹ چیک کریں یا دوبارہ کوشش کریں۔")
                .setPositiveButton("دوبارہ", (d, w) -> {
                    taskDone = false;
                    webView.reload();
                })
                .setNegativeButton("بند کریں", (d, w) -> finish())
                .show();
        });
    }

    void setStatus(String msg) {
        runOnUiThread(() -> tvTopStatus.setText(msg));
    }

    String escJs(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","");
    }

    @Override
    public void onBackPressed() {
        if (!taskDone) {
            new AlertDialog.Builder(this)
                .setTitle("بند کریں؟")
                .setMessage("حاضری ابھی مکمل نہیں ہوئی۔ کیا واقعی بند کریں؟")
                .setPositiveButton("ہاں", (d, w) -> finish())
                .setNegativeButton("نہیں", null)
                .show();
        } else finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) { webView.destroy(); webView = null; }
    }
}
