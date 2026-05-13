# AttendBot - APK بنانے کا طریقہ
## (بغیر کسی technical knowledge کے)

---

## 🟢 طریقہ نمبر 1: GitHub Actions (سب سے آسان - FREE)

### قدم 1: GitHub account بنائیں
- https://github.com پر جائیں
- Sign Up کریں (free)

### قدم 2: New Repository بنائیں
- اوپر "+" بٹن دبائیں → "New repository"
- Name: AttendBot
- Public منتخب کریں
- "Create repository" دبائیں

### قدم 3: فائلیں upload کریں
- "uploading an existing file" پر کلک کریں
- AttendBot.zip کی تمام فائلیں drag کریں
- "Commit changes" دبائیں

### قدم 4: GitHub Actions workflow بنائیں
Repository میں یہ فائل بنائیں:
`.github/workflows/build.yml`

اس میں یہ لکھیں:
```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build APK
        run: |
          chmod +x gradlew
          ./gradlew assembleDebug
      - uses: actions/upload-artifact@v3
        with:
          name: AttendBot-APK
          path: app/build/outputs/apk/debug/app-debug.apk
```

### قدم 5: APK download کریں
- Actions tab → Build APK → Artifacts سے APK download کریں

---

## 🟡 طریقہ نمبر 2: Android Studio (PC پر)

### قدم 1: Download کریں
- https://developer.android.com/studio سے Android Studio download کریں

### قدم 2: Project کھولیں
- File → Open → AttendBot folder منتخب کریں

### قدم 3: APK بنائیں
- Build → Build Bundle(s)/APK(s) → Build APK(s)
- APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Mobile پر Install کریں

### قدم 1: Unknown Sources allow کریں
- Settings → Security → Unknown Sources → ON کریں
- (یا Settings → Apps → Install unknown apps)

### قدم 2: APK transfer کریں
- WhatsApp/Email/USB سے mobile پر بھیجیں

### قدم 3: Install کریں
- APK فائل tap کریں → Install

---

## ⚙️ App استعمال کریں

1. App کھولیں
2. URL داخل کریں (آپ کی attendance website)
3. TIME IN: 08:30, TIME OUT: 09:00 (یا جو چاہیں)
4. "ترتیبات محفوظ کریں" دبائیں
5. "Bot شروع کریں" (سبز بٹن) دبائیں
6. بس! اب app خودکار ہر روز کام کرے گی

---

## ⚠️ اہم نوٹ - Flight Mode

Android 10+ میں flight mode خودکار بند کرنا ممکن نہیں
(Android security restriction)

حل: انٹرنیٹ ہمیشہ on رکھیں یا
وقت سے 5 منٹ پہلے خود on کریں
