package com.attendbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * RecordActivity:
 * - Opens lms.gims.tech inside a full WebView
 * - Injects JavaScript that records ALL user interactions:
 *   clicks (with XPath + CSS selector + text), input changes, scrolls
 * - When user finishes (presses DONE), saves the recorded steps as JSON
 * - These steps are later replayed by ReplayActivity
 *
 * Recording captures: { type, selector, xpath, value, text, x, y, timestamp }
 */
public class RecordActivity extends Activity {

    static final String TAG = "RecordActivity";
    WebView webView;
    Handler handler;
    SharedPreferences prefs;
    String recordMode; // "checkin" or "checkout"
    TextView tvStatus;
    Button btnDone, btnCancel;
    boolean recording = false;

    // JS injected into every page to capture events
    static final String RECORDER_JS =
        "if(!window.__attendbot_recorder){" +
        "  window.__attendbot_recorder = true;" +
        "  window.__attendbot_events = window.__attendbot_events || [];" +
        "  window.__attendbot_startTime = Date.now();" +

        // Helper: get best CSS selector for an element
        "  function getBestSelector(el){" +
        "    if(!el||el===document.body) return 'body';" +
        "    if(el.id) return '#'+el.id;" +
        "    var cls=Array.from(el.classList||[]).filter(function(c){return c.length>0&&!/^[0-9]/.test(c)}).join('.');" +
        "    var tag=el.tagName.toLowerCase();" +
        "    if(cls) return tag+'.'+cls;" +
        // Try name
        "    if(el.name) return tag+'[name=\"'+el.name+'\"]';" +
        // Try placeholder
        "    if(el.placeholder) return tag+'[placeholder*=\"'+el.placeholder.substring(0,20)+'\"]';" +
        "    var parent=el.parentElement;" +
        "    if(parent){" +
        "      var idx=Array.from(parent.children).indexOf(el);" +
        "      return getBestSelector(parent)+' > '+tag+':nth-child('+(idx+1)+')';" +
        "    }" +
        "    return tag;" +
        "  }" +

        // Helper: get XPath
        "  function getXPath(el){" +
        "    if(el.id) return '//*[@id=\"'+el.id+'\"]';" +
        "    if(el===document.body) return '/html/body';" +
        "    var idx=0,sib=el.previousSibling;" +
        "    while(sib){if(sib.nodeType===1&&sib.tagName===el.tagName)idx++;sib=sib.previousSibling;}" +
        "    return getXPath(el.parentNode)+'/'+el.tagName.toLowerCase()+'['+(idx+1)+']';" +
        "  }" +

        // Helper: record event
        "  function recordEvent(type,el,extra){" +
        "    var ev={" +
        "      type:type," +
        "      timestamp:Date.now()-window.__attendbot_startTime," +
        "      selector:getBestSelector(el)," +
        "      xpath:getXPath(el)," +
        "      tag:(el.tagName||'').toLowerCase()," +
        "      text:(el.textContent||el.innerText||el.value||'').trim().substring(0,100)," +
        "      value:(el.value||'')," +
        "      href:(el.href||'')," +
        "      url:window.location.href" +
        "    };" +
        "    if(extra) Object.assign(ev,extra);" +
        "    window.__attendbot_events.push(ev);" +
        "    console.log('ATTENDBOT_EVENT:'+JSON.stringify(ev));" +
        "  }" +

        // Click listener (capture phase so we get it before anything)
        "  document.addEventListener('click',function(e){" +
        "    var el=e.target;" +
        "    recordEvent('click',el,{x:e.clientX,y:e.clientY});" +
        "  },true);" +

        // Input change listener
        "  document.addEventListener('change',function(e){" +
        "    var el=e.target;" +
        "    if(el.type==='password'){" +
        "      recordEvent('input',el,{value:'__PASSWORD__',inputType:el.type});" +
        "    } else {" +
        "      recordEvent('input',el,{value:el.value,inputType:el.type});" +
        "    }" +
        "  },true);" +

        // Input event (for live typing)
        "  document.addEventListener('input',function(e){" +
        "    var el=e.target;" +
        "    if(el.tagName==='INPUT'||el.tagName==='TEXTAREA'){" +
        "      if(el.type==='password'){" +
        "        recordEvent('type',el,{value:'__PASSWORD__',inputType:el.type});" +
        "      } else {" +
        "        recordEvent('type',el,{value:el.value,inputType:el.type});" +
        "      }" +
        "    }" +
        "  },true);" +

        // Select change
        "  document.addEventListener('change',function(e){" +
        "    var el=e.target;" +
        "    if(el.tagName==='SELECT'){" +
        "      var opt=el.options[el.selectedIndex];" +
        "      recordEvent('select',el,{value:el.value,optionText:opt?opt.text:''});" +
        "    }" +
        "  },true);" +

        "  console.log('ATTENDBOT_RECORDER_READY');" +
        "}";

    // JS to get all recorded events as JSON string
    static final String GET_EVENTS_JS =
        "(function(){" +
        "  return JSON.stringify(window.__attendbot_events||[]);" +
        "})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("AttendBot", MODE_PRIVATE);
        recordMode = getIntent().getStringExtra("mode");
        if (recordMode == null) recordMode = "checkin";

        handler = new Handler(Looper.getMainLooper());
        setupUI();
    }

    void setupUI() {
        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF1a7a4a);
        topBar.setPadding(16, 48, 16, 16);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout topInfo = new LinearLayout(this);
        topInfo.setOrientation(LinearLayout.VERTICAL);
        topInfo.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🔴  Recording — " +
            (recordMode.equals("checkin") ? "CHECK IN" : "CHECK OUT"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        tvStatus = new TextView(this);
        tvStatus.setText("لاگ ان کریں → CHECK " +
            (recordMode.equals("checkin") ? "IN" : "OUT") + " دبائیں → DONE");
        tvStatus.setTextColor(0xFFaaffc8);
        tvStatus.setTextSize(11);

        topInfo.addView(tvTitle);
        topInfo.addView(tvStatus);

        btnDone = new Button(this);
        btnDone.setText("✓ DONE");
        btnDone.setTextColor(0xFF1a7a4a);
        btnDone.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        doneParams.setMarginStart(12);
        btnDone.setLayoutParams(doneParams);
        btnDone.setOnClickListener(v -> finishRecording());

        btnCancel = new Button(this);
        btnCancel.setText("✕");
        btnCancel.setTextColor(0xFFffaaaa);
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        btnCancel.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Recording رد کریں؟")
                .setMessage("یہ recording محفوظ نہیں ہوگی")
                .setPositiveButton("ہاں", (d, w) -> finish())
                .setNegativeButton("نہیں", null)
                .show();
        });

        topBar.addView(topInfo);
        topBar.addView(btnDone);
        topBar.addView(btnCancel);

        // WebView
        webView = new WebView(this);
        LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(wvParams);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Inject recorder JS on every page load
                view.evaluateJavascript(RECORDER_JS, null);
                tvStatus.setText("✓ Recording چل رہی ہے — " + shortenUrl(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                String msg = cm.message();
                if (msg.startsWith("ATTENDBOT_EVENT:")) {
                    // Event logged — we'll collect all at end
                    Log.d(TAG, msg);
                }
                return true;
            }
        });

        root.addView(topBar);
        root.addView(webView);
        setContentView(root);

        // Load LMS
        String url = prefs.getString("url", "https://lms.gims.tech/");
        webView.loadUrl(url);
        recording = true;
    }

    void finishRecording() {
        tvStatus.setText("Events محفوظ ہو رہے ہیں...");
        btnDone.setEnabled(false);

        // Get all recorded events from WebView JS
        webView.evaluateJavascript(GET_EVENTS_JS, eventsJson -> {
            handler.post(() -> {
                try {
                    if (eventsJson == null || eventsJson.equals("null") || eventsJson.equals("\"[]\"")) {
                        Toast.makeText(this, "کوئی action record نہیں ہوا", Toast.LENGTH_SHORT).show();
                        btnDone.setEnabled(true);
                        return;
                    }

                    // eventsJson comes as a JSON string (double-encoded since evaluateJavascript wraps in quotes)
                    // Strip outer quotes if needed
                    String cleanJson = eventsJson;
                    if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                        cleanJson = cleanJson.substring(1, cleanJson.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .replace("\\n", "\n");
                    }

                    // Validate JSON
                    JSONArray arr = new JSONArray(cleanJson);

                    // Filter: remove duplicate consecutive 'type' events (keep last per element)
                    JSONArray filtered = filterEvents(arr);

                    // Save to prefs
                    String key = recordMode.equals("checkin") ? "macro_checkin" : "macro_checkout";
                    prefs.edit().putString(key, filtered.toString()).apply();

                    int count = filtered.length();
                    showSaveSuccess(count);

                } catch (Exception e) {
                    Log.e(TAG, "Parse error: " + e.getMessage() + " raw=" + eventsJson);
                    // Try saving raw anyway
                    String key = recordMode.equals("checkin") ? "macro_checkin" : "macro_checkout";
                    prefs.edit().putString(key, eventsJson).apply();
                    Toast.makeText(this, "Recording محفوظ ہوگئی (raw)", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    JSONArray filterEvents(JSONArray arr) throws Exception {
        // Remove redundant 'type' events — keep only the final value per selector
        java.util.LinkedHashMap<String, JSONObject> lastType = new java.util.LinkedHashMap<>();
        JSONArray result = new JSONArray();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject ev = arr.getJSONObject(i);
            String type = ev.optString("type", "");
            if (type.equals("type")) {
                String sel = ev.optString("selector", "");
                lastType.put(sel, ev);
            } else {
                // Flush any pending type events before this non-type event
                for (JSONObject te : lastType.values()) result.put(te);
                lastType.clear();
                result.put(ev);
            }
        }
        // Flush remaining
        for (JSONObject te : lastType.values()) result.put(te);

        return result;
    }

    void showSaveSuccess(int count) {
        String modeText = recordMode.equals("checkin") ? "CHECK IN" : "CHECK OUT";
        new AlertDialog.Builder(this)
            .setTitle("✅  Recording محفوظ!")
            .setMessage(modeText + " کے " + count + " steps record ہوئے۔\n\n" +
                "اب Bot ہر بار یہی steps خودکار follow کرے گا۔")
            .setPositiveButton("ٹھیک ہے", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    String shortenUrl(String url) {
        if (url.length() > 40) return url.substring(0, 40) + "...";
        return url;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Recording بند کریں؟")
                .setMessage("کیا آپ recording ختم کرنا چاہتے ہیں؟ محفوظ نہیں ہوگی۔")
                .setPositiveButton("ہاں", (d, w) -> finish())
                .setNegativeButton("نہیں", null)
                .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }
}
