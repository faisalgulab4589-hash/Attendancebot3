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
 * ReplayActivity:
 * - Loads the recorded macro (JSONArray of events)
 * - Opens lms.gims.tech in a WebView
 * - Replays each event using JavaScript injection:
 *   - click → finds element by selector/xpath/text, clicks it
 *   - type  → finds input, sets value
 *   - select → finds select, sets selectedIndex
 * - Password fields: replaces '__PASSWORD__' with real saved password
 * - Location popup: auto-allowed via WebChromeClient
 * - On completion: closes and notifies
 */
public class ReplayActivity extends Activity {

    static final String TAG = "ReplayActivity";
    WebView webView;
    Handler handler;
    SharedPreferences prefs;
    String replayMode;
    JSONArray events;
    int currentEventIndex = 0;
    TextView tvStatus, tvProgress;
    ProgressBar progressBar;
    String savedEmail, savedPassword;
    boolean replayDone = false;
    int totalEvents = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("AttendBot", MODE_PRIVATE);
        replayMode = getIntent().getStringExtra("mode");
        if (replayMode == null) replayMode = "checkin";

        savedEmail = prefs.getString("email", "");
        savedPassword = prefs.getString("password", "");

        handler = new Handler(Looper.getMainLooper());

        // Load macro
        String key = replayMode.equals("checkin") ? "macro_checkin" : "macro_checkout";
        String macroJson = prefs.getString(key, "");

        if (macroJson.isEmpty()) {
            Toast.makeText(this, "پہلے Recording کریں!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            events = new JSONArray(macroJson);
            totalEvents = events.length();
        } catch (Exception e) {
            Toast.makeText(this, "Macro خراب ہے، دوبارہ record کریں", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUI();
    }

    void setupUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(0xFF1a7a4a);
        topBar.setPadding(16, 48, 16, 16);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("▶  Replaying — " +
            (replayMode.equals("checkin") ? "CHECK IN" : "CHECK OUT"));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        tvStatus = new TextView(this);
        tvStatus.setText("شروع ہو رہا ہے...");
        tvStatus.setTextColor(0xFFaaffc8);
        tvStatus.setTextSize(11);

        tvProgress = new TextView(this);
        tvProgress.setText("0 / " + totalEvents);
        tvProgress.setTextColor(0xFFffffff);
        tvProgress.setTextSize(11);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(totalEvents);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 8);
        pbParams.topMargin = 8;
        progressBar.setLayoutParams(pbParams);

        topBar.addView(tvTitle);
        topBar.addView(tvStatus);
        topBar.addView(tvProgress);
        topBar.addView(progressBar);

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
                Log.d(TAG, "Page loaded: " + url);
                tvStatus.setText("✓ Page لوڈ — steps چل رہے ہیں...");
                // Wait for page to settle, then replay pending events for this URL
                handler.postDelayed(() -> replayNextEvent(), 2000);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
                // Auto-allow location every time
                callback.invoke(origin, true, false);
            }
        });

        root.addView(topBar);
        root.addView(webView);
        setContentView(root);

        // Load LMS URL
        String url = prefs.getString("url", "https://lms.gims.tech/");
        webView.loadUrl(url);
    }

    // ─── Main replay engine ─────────────────────────────────────────────────
    void replayNextEvent() {
        if (replayDone) return;

        if (currentEventIndex >= totalEvents) {
            onReplayComplete();
            return;
        }

        try {
            JSONObject ev = events.getJSONObject(currentEventIndex);
            String type = ev.optString("type", "");
            String selector = ev.optString("selector", "");
            String xpath = ev.optString("xpath", "");
            String value = ev.optString("value", "");
            String text = ev.optString("text", "");
            String tag = ev.optString("tag", "");
            String optionText = ev.optString("optionText", "");

            // Replace placeholders with real credentials
            if (value.equals("__PASSWORD__")) value = savedPassword;
            // If value looks like email pattern or matches saved email, use saved email
            if (tag.equals("input") && (value.contains("@") || value.equals(savedEmail))) {
                value = savedEmail;
            }

            updateProgress(currentEventIndex + 1, type, selector);

            String js = buildReplayJS(type, selector, xpath, value, text, tag, optionText);

            final String finalValue = value;
            webView.evaluateJavascript(js, result -> {
                Log.d(TAG, "Event " + currentEventIndex + " (" + type + "): " + result);

                currentEventIndex++;
                // Delay between events: click needs more time, typing less
                int delay = 800;
                if (type.equals("click")) delay = 1200;
                else if (type.equals("select")) delay = 800;
                else if (type.equals("type") || type.equals("input")) delay = 400;

                handler.postDelayed(this::replayNextEvent, delay);
            });

        } catch (Exception e) {
            Log.e(TAG, "Replay event error: " + e.getMessage());
            currentEventIndex++;
            handler.postDelayed(this::replayNextEvent, 500);
        }
    }

    // ─── Build JavaScript for each event type ───────────────────────────────
    String buildReplayJS(String type, String selector, String xpath,
                         String value, String text, String tag, String optionText) {

        String escapedValue = escapeJs(value);
        String escapedText = escapeJs(text);
        String escapedSelector = escapeJs(selector);
        String escapedXpath = escapeJs(xpath);
        String escapedOptionText = escapeJs(optionText);

        // Universal element finder (tries selector, then xpath, then text content)
        String finder =
            "(function findEl(){" +
            "  var el=null;" +
            // Try CSS selector
            "  try{el=document.querySelector('" + escapedSelector + "');}catch(e){}" +
            "  if(el) return el;" +
            // Try XPath
            "  try{" +
            "    var xr=document.evaluate('" + escapedXpath + "',document,null," +
            "      XPathResult.FIRST_ORDERED_NODE_TYPE,null);" +
            "    if(xr.singleNodeValue) return xr.singleNodeValue;" +
            "  }catch(e){}" +
            // Try by text content (for buttons/links)
            "  if('" + escapedText + "'){" +
            "    var all=document.querySelectorAll('button,a,input[type=\"button\"],input[type=\"submit\"],[role=\"button\"],.btn');" +
            "    for(var i=0;i<all.length;i++){" +
            "      var t=(all[i].textContent||all[i].value||'').trim();" +
            "      if(t.toLowerCase().includes('" + escapedText.toLowerCase() + "')) return all[i];" +
            "    }" +
            "  }" +
            "  return null;" +
            "})()";

        switch (type) {
            case "click":
                return "(function(){" +
                    "  var el=" + finder + ";" +
                    "  if(!el) return 'notfound:selector=" + escapedSelector + "';" +
                    "  el.scrollIntoView({block:'center'});" +
                    "  el.focus();" +
                    "  el.click();" +
                    // Dispatch full click event sequence
                    "  ['mousedown','mouseup','click'].forEach(function(et){" +
                    "    el.dispatchEvent(new MouseEvent(et,{bubbles:true,cancelable:true}));" +
                    "  });" +
                    "  return 'clicked:'+el.tagName+':'+((el.textContent||'').trim().substring(0,30));" +
                    "})()";

            case "type":
            case "input":
                return "(function(){" +
                    "  var el=" + finder + ";" +
                    "  if(!el) return 'notfound:selector=" + escapedSelector + "';" +
                    "  el.focus();" +
                    "  el.value='" + escapedValue + "';" +
                    "  el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "  el.dispatchEvent(new Event('change',{bubbles:true}));" +
                    // For React/Angular forms
                    "  var nativeInputValueSetter=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');" +
                    "  if(nativeInputValueSetter){nativeInputValueSetter.set.call(el,'" + escapedValue + "');}" +
                    "  el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "  return 'typed:'+el.tagName;" +
                    "})()";

            case "select":
                return "(function(){" +
                    "  var el=" + finder + ";" +
                    "  if(!el) return 'notfound:select';" +
                    "  var opts=el.options;" +
                    "  for(var i=0;i<opts.length;i++){" +
                    "    if(opts[i].value==='" + escapedValue + "' || " +
                    "       opts[i].text.toLowerCase().includes('" + escapedOptionText.toLowerCase() + "')){" +
                    "      el.selectedIndex=i;" +
                    "      el.dispatchEvent(new Event('change',{bubbles:true}));" +
                    "      return 'selected:'+opts[i].text;" +
                    "    }" +
                    "  }" +
                    "  return 'select-option-not-found:value=" + escapedValue + "';" +
                    "})()";

            default:
                return "'skipped:" + type + "'";
        }
    }

    String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\"", "\\\"");
    }

    void updateProgress(int current, String type, String selector) {
        runOnUiThread(() -> {
            progressBar.setProgress(current);
            tvProgress.setText(current + " / " + totalEvents);
            tvStatus.setText("→ " + type + ": " +
                (selector.length() > 35 ? selector.substring(0, 35) + "..." : selector));
        });
    }

    void onReplayComplete() {
        replayDone = true;
        String modeText = replayMode.equals("checkin") ? "CHECK IN ✅" : "CHECK OUT 🚪";

        runOnUiThread(() -> {
            tvStatus.setText("مکمل! " + modeText);
            progressBar.setProgress(totalEvents);

            // Record in history
            String now = new java.text.SimpleDateFormat("dd MMM, HH:mm",
                java.util.Locale.getDefault()).format(new java.util.Date());
            String label = replayMode.equals("checkin") ? "Check In" : "Check Out";
            prefs.edit().putString("lastAttendance", now + " (" + label + ")").apply();
            String history = prefs.getString("history", "");
            history = history + (history.isEmpty() ? "" : "|") + now + " (" + label + " - Macro)";
            prefs.edit().putString("history", history).apply();

            // Show success and close
            handler.postDelayed(() -> {
                new AlertDialog.Builder(this)
                    .setTitle(modeText + " ہو گیا!")
                    .setMessage("وقت: " + now + "\n\nBot نے macro replay مکمل کر لی۔")
                    .setPositiveButton("ٹھیک ہے", (d, w) -> {
                        finish();
                    })
                    .setCancelable(false)
                    .show();
            }, 1500);
        });
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("Replay روکیں؟")
            .setPositiveButton("ہاں", (d, w) -> finish())
            .setNegativeButton("نہیں", null)
            .show();
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
