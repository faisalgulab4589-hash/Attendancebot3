package com.attendbot;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    SharedPreferences prefs;

    // Views
    TextView tvStatus, tvLastAttendance, tvNextIn, tvNextOut;
    TextView tvCheckinMacro, tvCheckoutMacro;
    EditText etUrl, etEmail, etPassword;
    Button btnRecordIn, btnRecordOut, btnPlayIn, btnPlayOut;
    Button btnSave, btnEnableAlarm, btnDisableAlarm;
    Switch swAlarm;
    LinearLayout llHistory;

    // Time state  (stored as 12h + am/pm)
    int inHour=8, inMin=30; boolean inPm=false;
    int outHour=5, outMin=0; boolean outPm=true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("AttendBot", MODE_PRIVATE);
        buildUI();
        loadSettings();
        updateUI();
        requestPerms();
        checkBattery();
    }

    // ── Build UI programmatically (no XML dependency issues) ────────────────
    void buildUI() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF5FDF8);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0,0,0,40);
        sv.addView(root);

        // ── Header ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF1a7a4a);
        header.setPadding(px(16), px(36), px(16), px(16));

        TextView appTitle = tv("📅  AttendBot", 20, 0xFFFFFFFF, true);
        tvStatus = tv("○ خودکار حاضری بند ہے", 12, 0xFFaaffc8, false);
        tvLastAttendance = tv("آخری حاضری: ابھی تک نہیں", 11, 0xFFcceedd, false);
        tvNextIn  = tv("CHECK IN: --:--", 11, 0xFFcceedd, false);
        tvNextOut = tv("CHECK OUT: --:--", 11, 0xFFcceedd, false);

        header.addView(appTitle);
        header.addView(tvStatus);
        header.addView(tvLastAttendance);
        header.addView(tvNextIn);
        header.addView(tvNextOut);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(14), px(14), px(14), px(14));
        root.addView(body);

        // ── URL ──
        body.addView(sectionTitle("ویب سائٹ URL"));
        LinearLayout urlCard = card();
        etUrl = editText("https://lms.gims.tech/", false);
        urlCard.addView(etUrl);
        body.addView(urlCard);

        // ── Credentials ──
        body.addView(sectionTitle("Email اور Password"));
        LinearLayout credCard = card();
        credCard.addView(tv("Email Address", 11, 0xFF555555, false));
        etEmail = editText("yourname@example.com", false);
        etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | android.text.InputType.TYPE_CLASS_TEXT);
        credCard.addView(etEmail);
        credCard.addView(vspace(8));
        credCard.addView(tv("Password", 11, 0xFF555555, false));
        etPassword = editText("••••••••", true);
        credCard.addView(etPassword);
        credCard.addView(vspace(6));
        credCard.addView(tv("● یہ معلومات صرف آپ کے device پر محفوظ رہتی ہیں", 10, 0xFF888888, false));
        body.addView(credCard);

        // ── Macro Recording ──
        body.addView(sectionTitle("Macro Recording (ایک بار record کریں)"));
        LinearLayout macroCard = card();

        // CHECK IN macro
        LinearLayout inRow = hRow();
        LinearLayout inInfo = new LinearLayout(this);
        inInfo.setOrientation(LinearLayout.VERTICAL);
        inInfo.setLayoutParams(wt(1f));
        tvCheckinMacro = tv("⚠️  ابھی تک record نہیں", 11, 0xFFe65100, false);
        inInfo.addView(tv("CHECK IN Macro", 13, 0xFF222222, true));
        inInfo.addView(tvCheckinMacro);
        inRow.addView(inInfo);
        btnRecordIn = smallBtn("🔴 Record", 0xFF1a7a4a);
        btnPlayIn   = smallBtn("▶ چلائیں", 0xFF0277BD);
        inRow.addView(btnRecordIn);
        inRow.addView(hspace(6));
        inRow.addView(btnPlayIn);
        macroCard.addView(inRow);
        macroCard.addView(divider());

        // CHECK OUT macro
        LinearLayout outRow = hRow();
        LinearLayout outInfo = new LinearLayout(this);
        outInfo.setOrientation(LinearLayout.VERTICAL);
        outInfo.setLayoutParams(wt(1f));
        tvCheckoutMacro = tv("⚠️  ابھی تک record نہیں", 11, 0xFFe65100, false);
        outInfo.addView(tv("CHECK OUT Macro", 13, 0xFF222222, true));
        outInfo.addView(tvCheckoutMacro);
        outRow.addView(outInfo);
        btnRecordOut = smallBtn("🔴 Record", 0xFF1a7a4a);
        btnPlayOut   = smallBtn("▶ چلائیں", 0xFF0277BD);
        outRow.addView(btnRecordOut);
        outRow.addView(hspace(6));
        outRow.addView(btnPlayOut);
        macroCard.addView(outRow);
        body.addView(macroCard);

        // ── Time ──
        body.addView(sectionTitle("خودکار وقت (AM/PM)"));
        LinearLayout timeCard = card();

        // CHECK IN time
        LinearLayout inTimeRow = hRow();
        inTimeRow.addView(tv("CHECK IN وقت:", 13, 0xFF333333, false));
        inTimeRow.setLayoutParams(fullW());
        Button btnInTime = new Button(this);
        btnInTime.setId(R.id.btnInTime);
        btnInTime.setBackgroundColor(0xFF1a7a4a);
        btnInTime.setTextColor(0xFFFFFFFF);
        btnInTime.setPadding(px(12),px(8),px(12),px(8));
        btnInTime.setLayoutParams(wt(1f));
        inTimeRow.addView(btnInTime);
        timeCard.addView(inTimeRow);
        timeCard.addView(vspace(8));

        // CHECK OUT time
        LinearLayout outTimeRow = hRow();
        outTimeRow.addView(tv("CHECK OUT وقت:", 13, 0xFF333333, false));
        outTimeRow.setLayoutParams(fullW());
        Button btnOutTime = new Button(this);
        btnOutTime.setId(R.id.btnOutTime);
        btnOutTime.setBackgroundColor(0xFFB71C1C);
        btnOutTime.setTextColor(0xFFFFFFFF);
        btnOutTime.setPadding(px(12),px(8),px(12),px(8));
        btnOutTime.setLayoutParams(wt(1f));
        outTimeRow.addView(btnOutTime);
        timeCard.addView(outTimeRow);
        timeCard.addView(vspace(6));
        timeCard.addView(tv("● مقررہ وقت پر bot خودکار حاضری لگائے گا", 10, 0xFF666666, false));
        body.addView(timeCard);
        updateTimeBtns(btnInTime, btnOutTime);

        // Time picker click
        btnInTime.setOnClickListener(v -> showAmPmTimePicker(true, btnInTime, btnOutTime));
        btnOutTime.setOnClickListener(v -> showAmPmTimePicker(false, btnInTime, btnOutTime));

        // ── Alarm toggle ──
        body.addView(sectionTitle("خودکار حاضری"));
        LinearLayout alarmCard = card();
        LinearLayout alarmRow = hRow();
        LinearLayout alarmInfo = new LinearLayout(this);
        alarmInfo.setOrientation(LinearLayout.VERTICAL);
        alarmInfo.setLayoutParams(wt(1f));
        alarmInfo.addView(tv("خودکار Bot", 14, 0xFF222222, true));
        alarmInfo.addView(tv("مقررہ وقت پر CHECK IN/OUT خودکار ہوگا", 11, 0xFF666666, false));
        swAlarm = new Switch(this);
        swAlarm.setTextColor(0xFF1a7a4a);
        alarmRow.addView(alarmInfo);
        alarmRow.addView(swAlarm);
        alarmCard.addView(alarmRow);
        alarmCard.addView(vspace(6));
        alarmCard.addView(tv("● Internet بند ہو تو bot خودکار on کرے گا", 10, 0xFF666666, false));
        alarmCard.addView(tv("● Flight mode on ہو تو پہلے off کرے گا", 10, 0xFF666666, false));
        body.addView(alarmCard);

        // ── Save button ──
        body.addView(vspace(10));
        btnSave = bigBtn("ترتیبات محفوظ کریں", 0xFF2E7D32);
        body.addView(btnSave);
        body.addView(vspace(6));

        // ── Manual buttons ──
        body.addView(sectionTitle("دستی حاضری"));
        LinearLayout manualRow = hRow();
        btnPlayIn  = bigBtn2("✅ CHECK IN", 0xFF1a7a4a);
        btnPlayOut = bigBtn2("🚪 CHECK OUT", 0xFFB71C1C);
        btnPlayIn.setLayoutParams(wt(1f));
        btnPlayOut.setLayoutParams(wt(1f));
        manualRow.addView(btnPlayIn);
        manualRow.addView(hspace(8));
        manualRow.addView(btnPlayOut);
        body.addView(manualRow);

        // ── History ──
        body.addView(vspace(12));
        body.addView(sectionTitle("حاضری کی تاریخ"));
        llHistory = new LinearLayout(this);
        llHistory.setOrientation(LinearLayout.VERTICAL);
        llHistory.setBackgroundColor(0xFFFFFFFF);
        llHistory.setPadding(px(14),px(10),px(14),px(10));
        body.addView(llHistory);

        setContentView(sv);

        // ── Listeners ──
        btnRecordIn.setOnClickListener(v  -> startRecord("checkin"));
        btnRecordOut.setOnClickListener(v -> startRecord("checkout"));
        btnPlayIn.setOnClickListener(v    -> manualRun("checkin"));
        btnPlayOut.setOnClickListener(v   -> manualRun("checkout"));
        btnSave.setOnClickListener(v      -> saveSettings());
        swAlarm.setOnCheckedChangeListener((b, checked) -> {
            saveSettings();
            if (checked) {
                if (!hasMacro("checkin") || !hasMacro("checkout")) {
                    Toast.makeText(this, "پہلے CHECK IN اور CHECK OUT macro record کریں", Toast.LENGTH_LONG).show();
                    swAlarm.setChecked(false); return;
                }
                ScheduleHelper.scheduleAlarms(this);
                prefs.edit().putBoolean("alarmEnabled", true).apply();
                Toast.makeText(this, "خودکار حاضری فعال ✓", Toast.LENGTH_SHORT).show();
            } else {
                ScheduleHelper.cancelAlarms(this);
                prefs.edit().putBoolean("alarmEnabled", false).apply();
                Toast.makeText(this, "خودکار حاضری بند", Toast.LENGTH_SHORT).show();
            }
            updateUI();
        });
    }

    // ── AM/PM Time Picker ───────────────────────────────────────────────────
    void showAmPmTimePicker(boolean isIn, Button btnIn, Button btnOut) {
        String[] hours = {"12","1","2","3","4","5","6","7","8","9","10","11"};
        String[] mins  = {"00","05","10","15","20","25","30","35","40","45","50","55"};
        String[] ampm  = {"AM","PM"};

        View dlgView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(px(20), px(20), px(20), px(10));

        ll.addView(tv(isIn ? "CHECK IN وقت" : "CHECK OUT وقت", 16, 0xFF1a7a4a, true));
        ll.addView(vspace(12));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);

        NumberPicker npH = new NumberPicker(this);
        npH.setMinValue(0); npH.setMaxValue(11);
        npH.setDisplayedValues(hours);
        npH.setValue(isIn ? hourIndex(inHour) : hourIndex(outHour));

        TextView colon = tv(":", 24, 0xFF333333, true);
        colon.setPadding(px(8),0,px(8),0);

        NumberPicker npM = new NumberPicker(this);
        npM.setMinValue(0); npM.setMaxValue(11);
        npM.setDisplayedValues(mins);
        npM.setValue(isIn ? inMin/5 : outMin/5);

        NumberPicker npAP = new NumberPicker(this);
        npAP.setMinValue(0); npAP.setMaxValue(1);
        npAP.setDisplayedValues(ampm);
        npAP.setValue(isIn ? (inPm?1:0) : (outPm?1:0));
        LinearLayout.LayoutParams apP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        apP.setMarginStart(px(12));
        npAP.setLayoutParams(apP);

        row.addView(npH);
        row.addView(colon);
        row.addView(npM);
        row.addView(npAP);
        ll.addView(row);

        new AlertDialog.Builder(this)
            .setView(ll)
            .setPositiveButton("ٹھیک ہے", (d, w) -> {
                int h = Integer.parseInt(hours[npH.getValue()]);
                int m = Integer.parseInt(mins[npM.getValue()]);
                boolean pm = npAP.getValue() == 1;
                if (isIn) { inHour=h; inMin=m; inPm=pm; }
                else      { outHour=h; outMin=m; outPm=pm; }
                saveTimeToPrefs();
                updateTimeBtns(
                    (Button)findViewById(R.id.btnInTime),
                    (Button)findViewById(R.id.btnOutTime));
                if (prefs.getBoolean("alarmEnabled", false)) {
                    ScheduleHelper.scheduleAlarms(this);
                    updateUI();
                }
            })
            .setNegativeButton("رد کریں", null)
            .show();
    }

    int hourIndex(int h) {
        // 12h → index: 12→0, 1→1 ... 11→11
        return (h == 12) ? 0 : h;
    }

    void updateTimeBtns(Button btnIn, Button btnOut) {
        if (btnIn  != null) btnIn.setText(fmt12(inHour,  inMin,  inPm));
        if (btnOut != null) btnOut.setText(fmt12(outHour, outMin, outPm));
    }

    String fmt12(int h, int m, boolean pm) {
        return String.format("%d:%02d %s", h==0?12:h, m, pm?"PM":"AM");
    }

    // ── Record / Play ────────────────────────────────────────────────────────
    void startRecord(String mode) {
        saveSettings();
        new AlertDialog.Builder(this)
            .setTitle("🔴 " + (mode.equals("checkin")?"CHECK IN":"CHECK OUT") + " Record")
            .setMessage(
                "اگلی screen میں website کھلے گی۔\n\n" +
                "آپ نے یہ کرنا ہے:\n" +
                "① Email درج کریں\n② Password درج کریں\n" +
                "③ Role میں Staff منتخب کریں\n④ Login کریں\n" +
                "⑤ Location Allow کریں\n" +
                (mode.equals("checkin")?"⑥ CHECK IN دبائیں":"⑥ CHECK OUT دبائیں") +
                "\n\nپھر اوپر ✓ DONE دبائیں۔")
            .setPositiveButton("شروع", (d,w) -> {
                Intent i = new Intent(this, RecordActivity.class);
                i.putExtra("mode", mode);
                startActivityForResult(i, mode.equals("checkin")?101:102);
            })
            .setNegativeButton("رد", null)
            .show();
    }

    void manualRun(String mode) {
        saveSettings();
        // Launch AttendanceService which handles internet, then opens WebView
        Intent si = new Intent(this, AttendanceService.class);
        si.putExtra("mode", mode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si);
        else startService(si);
    }

    boolean hasMacro(String mode) {
        return !prefs.getString(mode.equals("checkin")?"macro_checkin":"macro_checkout","").isEmpty();
    }

    // ── Settings ─────────────────────────────────────────────────────────────
    void saveSettings() {
        prefs.edit()
            .putString("url",       etUrl.getText().toString().trim())
            .putString("email",     etEmail.getText().toString().trim())
            .putString("password",  etPassword.getText().toString())
            .apply();
        saveTimeToPrefs();
        Toast.makeText(this, "محفوظ ✓", Toast.LENGTH_SHORT).show();
    }

    void saveTimeToPrefs() {
        prefs.edit()
            .putInt("inHour", inHour).putInt("inMin", inMin).putBoolean("inPm", inPm)
            .putInt("outHour",outHour).putInt("outMin",outMin).putBoolean("outPm",outPm)
            .apply();
    }

    void loadSettings() {
        etUrl.setText(prefs.getString("url","https://lms.gims.tech/"));
        etEmail.setText(prefs.getString("email",""));
        etPassword.setText(prefs.getString("password",""));
        inHour  = prefs.getInt("inHour", 8);  inMin  = prefs.getInt("inMin", 30);  inPm  = prefs.getBoolean("inPm", false);
        outHour = prefs.getInt("outHour",5);  outMin = prefs.getInt("outMin",0);   outPm = prefs.getBoolean("outPm",true);
        swAlarm.setChecked(prefs.getBoolean("alarmEnabled",false));
    }

    void updateUI() {
        boolean alarm   = prefs.getBoolean("alarmEnabled", false);
        boolean hasIn   = hasMacro("checkin");
        boolean hasOut  = hasMacro("checkout");

        tvStatus.setText(alarm ? "● خودکار حاضری فعال ✓" : "○ خودکار حاضری بند");
        tvStatus.setTextColor(alarm ? 0xFF00ff88 : 0xFFaaaaaa);
        tvLastAttendance.setText("آخری: " + prefs.getString("lastAttendance","ابھی تک نہیں"));
        tvNextIn.setText ("CHECK IN:  " + fmt12(inHour,  inMin,  inPm));
        tvNextOut.setText("CHECK OUT: " + fmt12(outHour, outMin, outPm));

        if (tvCheckinMacro  != null) { tvCheckinMacro.setText(hasIn  ?"✅ Macro محفوظ":"⚠️ ابھی record نہیں");  tvCheckinMacro.setTextColor(hasIn  ?0xFF1a7a4a:0xFFe65100); }
        if (tvCheckoutMacro != null) { tvCheckoutMacro.setText(hasOut?"✅ Macro محفوظ":"⚠️ ابھی record نہیں"); tvCheckoutMacro.setTextColor(hasOut?0xFF1a7a4a:0xFFe65100); }

        loadHistory();
    }

    void loadHistory() {
        if (llHistory == null) return;
        llHistory.removeAllViews();
        String h = prefs.getString("history","");
        if (h.isEmpty()) { llHistory.addView(tv("ابھی تک کوئی حاضری نہیں", 12, 0xFF888888, false)); return; }
        String[] entries = h.split("\\|");
        for (int i = entries.length-1; i >= Math.max(0, entries.length-7); i--) {
            TextView t = tv("✓  "+entries[i], 12, 0xFF333333, false);
            t.setPadding(0, px(6), 0, px(6));
            llHistory.addView(t);
            View dv = new View(this);
            dv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            dv.setBackgroundColor(0xFFd0eadb);
            llHistory.addView(dv);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req,res,data);
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        updateUI();
    }

    // ── Permissions & Battery ───────────────────────────────────────────────
    void requestPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    void checkBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                    .setTitle("بیٹری سیور")
                    .setMessage("Bot کو وقت پر چلانے کے لیے Battery Optimization سے نکالیں۔")
                    .setPositiveButton("سیٹنگز", (d,w) -> {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:"+getPackageName()));
                        startActivity(i);
                    })
                    .setNegativeButton("بعد میں", null).show();
            }
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────
    int px(int dp) { return (int)(dp * getResources().getDisplayMetrics().density); }

    TextView tv(String text, int spSize, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(spSize); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    EditText editText(String hint, boolean password) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setTextSize(14); et.setTextColor(0xFF222222);
        et.setBackground(null);
        if (password) et.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return et;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(0xFFFFFFFF);
        c.setPadding(px(14), px(12), px(14), px(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, px(10));
        c.setLayoutParams(lp);
        return c;
    }

    LinearLayout hRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setLayoutParams(fullW());
        return r;
    }

    TextView sectionTitle(String t) {
        TextView tv = tv(t, 11, 0xFF1a7a4a, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(4), 0, px(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    Button bigBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(0xFFFFFFFF); b.setTextSize(14);
        b.setBackgroundColor(color);
        b.setLayoutParams(fullW());
        return b;
    }

    Button bigBtn2(String text, int color) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(0xFFFFFFFF); b.setTextSize(14);
        b.setBackgroundColor(color);
        return b;
    }

    Button smallBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(0xFFFFFFFF); b.setTextSize(11);
        b.setBackgroundColor(color);
        b.setPadding(px(8),px(4),px(8),px(4));
        return b;
    }

    View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, px(8), 0, px(8));
        v.setLayoutParams(lp);
        v.setBackgroundColor(0xFFd0eadb);
        return v;
    }

    View vspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(dp)));
        return v;
    }

    View hspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(dp), LinearLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    LinearLayout.LayoutParams fullW() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams wt(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }
}
