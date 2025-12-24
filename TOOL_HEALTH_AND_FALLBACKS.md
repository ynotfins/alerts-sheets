# TOOL HEALTH & FALLBACK STRATEGIES

**Purpose:** Detect tool failures early and provide fallback paths to maintain productivity.

**Last Updated:** December 23, 2025

---

## 🔍 **TOOL HEALTH CHECKLIST**

Run this at the **start of each coding session**:

### **1️⃣ Firebase CLI**

```powershell
firebase --version
firebase projects:list
```

**Expected:**
- Version: 14.x or higher
- Current project: `alerts-sheets-bb09c (current)`

**If FAIL:**
- Run: `npm install -g firebase-tools`
- Run: `firebase login`

---

### **2️⃣ Node.js / npm / TypeScript**

```powershell
node -v
npm -v
cd functions && npm run build
```

**Expected:**
- Node: v20+ or v22+
- npm: 10.x or 11.x
- TypeScript: Clean build (no errors)

**If FAIL:**
- Install Node 22 LTS: https://nodejs.org/
- Run: `cd functions && npm install`

---

### **3️⃣ Android Build Tools**

```powershell
cd android
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:assembleRelease --no-daemon
```

**Expected:**
- Both: `BUILD SUCCESSFUL`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

**If FAIL:**
- Check `build.gradle` syntax
- Run: `./gradlew clean`
- Check JDK version: `./gradlew --version` (should be 17)

---

### **4️⃣ Firebase Auth**

```powershell
firebase functions:list
```

**Expected:**
- 7 functions listed (ingest, enrichAlert, enrichProperty, health, etc.)
- All in `us-central1`

**If FAIL:**
- Run: `firebase login` (opens browser)
- Check project: `firebase use alerts-sheets-bb09c`

---

### **5️⃣ MCP Servers (Cursor)**

**Check:** Open Cursor Settings → MCP

**Expected Active:**
- Context7 (docs)
- GitHub (repo management)
- Memory/Mem0 (context persistence)
- Exa Search (web/code search)
- Firestore (DB operations)
- Serena (code intelligence - if Kotlin indexing works)

**If FAIL:**
- Restart Cursor
- Check `.cursor/mcp.json` syntax
- Check MCP server logs in Cursor Output panel

---

### **6️⃣ GitHub Copilot (Optional)**

**Check:** Type a comment in code editor, wait 1 second

**Expected:**
- Gray ghost suggestion appears

**If FAIL (non-blocking):**
- Cursor Settings → Search "Copilot" → Verify enabled
- Sign in with GitHub account
- Fallback: Use Cursor Agent (this session) + grep

---

### **7️⃣ PowerShell (Windows)**

```powershell
Get-Command Select-String
Get-Command where.exe
```

**Expected:**
- Both commands found

**If FAIL (unlikely):**
- Use Command Prompt with `findstr` instead of `Select-String`

---

## 🛠️ **FALLBACK STRATEGIES**

### **If GitHub Copilot Not Working:**

**Primary Fallback: Cursor Agent + Grep**

```
✅ Use Cursor Agent (this session) for code generation
✅ Use grep/Select-String for code navigation
✅ Use existing patterns from codebase as templates
```

**Example:**
```powershell
# Find existing coroutine patterns
Select-String -Path "android\app\src\**\*.kt" -Pattern "scope\.launch" -Context 2,2

# Find existing HTTP POST patterns
Select-String -Path "android\app\src\**\*.kt" -Pattern "httpClient\.post" -Context 3,3
```

---

### **If Serena MCP Not Indexing Kotlin:**

**Primary Fallback: PowerShell Search + Manual Navigation**

```
✅ Use Select-String for symbol search
✅ Use find_file for locating classes
✅ Use grep for call chain analysis
```

**Example:**
```powershell
# Find all references to DataPipeline
Select-String -Path "android\app\src\**\*.kt" -Pattern "DataPipeline" -Context 1,1

# Find all HTTP POST calls
Select-String -Path "android\app\src\**\*.kt" -Pattern "\.post\(" -Context 2,2
```

---

### **If Firebase CLI Not Working:**

**Primary Fallback: Firebase Console + Manual Deployment**

```
✅ Firebase Console: https://console.firebase.google.com/project/alerts-sheets-bb09c
✅ Manually upload functions via Console
✅ Use Firestore Console for rules/data inspection
```

---

### **If Android Build Fails:**

**Primary Fallback: Android Studio**

```
✅ Open project in Android Studio
✅ File → Sync Project with Gradle Files
✅ Build → Rebuild Project
✅ Check Build Output for specific errors
```

---

### **If Firestore Console Shows No Data:**

**This is EXPECTED if no documents exist yet!**

**Verification Methods:**

**Method 1: Call /ingest endpoint**
```powershell
$headers = @{
    "Authorization" = "Bearer <FIREBASE_ID_TOKEN>"
    "Content-Type" = "application/json"
}

$body = @{
    uuid = [guid]::NewGuid().ToString()
    sourceId = "test-source"
    payload = '{"test": true}'
    timestamp = (Get-Date).ToString("o")
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://us-central1-alerts-sheets-bb09c.cloudfunctions.net/ingest" -Method POST -Headers $headers -Body $body
```

**Method 2: Use IngestTestActivity (debug APK)**
```
1. Install debug APK
2. Open "Test Harness" card
3. Tap "Happy Path"
4. Check Firestore Console: /alerts should appear
```

**Method 3: Check Cloud Function logs**
```powershell
firebase functions:log --only ingest --limit 50
firebase functions:log --only enrichAlert --limit 50
```

---

## 📋 **SESSION START CHECKLIST**

Copy this into every work session:

```
Session Date: _______________
Project: AlertsToSheets

Pre-Flight Checks:
[ ] Firebase CLI logged in (firebase projects:list)
[ ] Node/npm working (node -v && npm -v)
[ ] Android builds pass (./gradlew :app:assembleDebug)
[ ] Functions build pass (cd functions && npm run build)
[ ] MCP servers active (check Cursor Settings → MCP)
[ ] Copilot working (type comment, wait for suggestion)
[ ] PowerShell Select-String working (Get-Command Select-String)

Fallback Plan (if needed):
[ ] Copilot OFF → Use Cursor Agent + grep
[ ] Serena OFF → Use Select-String for navigation
[ ] Firebase CLI issue → Use Firebase Console
[ ] Build issue → Open Android Studio

Ready to Code: YES / NO
```

---

## 🚨 **TOOL FAILURE DETECTION PATTERNS**

### **Firebase CLI: "Error: Not logged in"**

**Symptom:**
```
Error: Not logged in. Please run 'firebase login' to authenticate.
```

**Fix:**
```powershell
firebase login
# Opens browser, sign in with ynotfins@gmail.com
```

---

### **Android Build: "BuildConfig not found"**

**Symptom:**
```
Unresolved reference: BuildConfig
```

**Fix:**
```gradle
// In android/app/build.gradle
buildFeatures {
    buildConfig true  // ✅ Must be enabled
}
```

---

### **Firestore Rules: "Permission denied"**

**Symptom:**
```
FirebaseFirestoreException: PERMISSION_DENIED: Missing or insufficient permissions
```

**Fix:**
```powershell
# Check deployed rules
firebase firestore:rules
# Deploy latest rules
firebase deploy --only firestore:rules
```

---

### **Cloud Functions: "Function not found"**

**Symptom:**
```
Error: No function matches given --only filters
```

**Fix:**
```powershell
# Check functions are exported in index.ts
# Verify syntax:
cd functions && npm run build
# Deploy all:
firebase deploy --only functions
```

---

### **Kotlin Compilation: "Type mismatch"**

**Symptom:**
```
Type mismatch: inferred type is X but Y was expected
```

**Fix:**
1. Check imports (missing or wrong package)
2. Check nullable types (`String` vs `String?`)
3. Check generic types (`List<String>` vs `List<*>`)
4. Run `./gradlew clean` and rebuild

---

### **MCP Server: "Tool not found"**

**Symptom:**
```
Error: Tool 'mcp_xxx_tool_name' not found
```

**Fix:**
1. Check MCP server is enabled in Cursor Settings
2. Restart Cursor
3. Check MCP server logs in Output panel
4. Fallback to non-MCP tools (grep, Firebase CLI)

---

## ✅ **VERIFICATION MATRIX**

| Tool | Check Command | Expected Output | Fallback |
|------|---------------|-----------------|----------|
| Firebase CLI | `firebase --version` | `14.x.x` | Firebase Console |
| Node.js | `node -v` | `v22.x.x` | N/A (required) |
| npm | `npm -v` | `11.x.x` | N/A (required) |
| TypeScript | `cd functions && npm run build` | Clean build | Fix syntax |
| Android Debug | `./gradlew :app:assembleDebug` | `BUILD SUCCESSFUL` | Android Studio |
| Android Release | `./gradlew :app:assembleRelease` | `BUILD SUCCESSFUL` | Android Studio |
| Firestore | `firebase firestore:get /config/featureFlags` | Document data | Console |
| Functions | `firebase functions:list` | 7 functions listed | Console |
| MCP Servers | Check Cursor Settings → MCP | Active status | grep + CLI |
| Copilot | Type comment in editor | Ghost suggestion | Cursor Agent |
| PowerShell | `Get-Command Select-String` | CommandInfo | `findstr` |

---

## 📝 **POST-FAILURE RECOVERY**

### **After Fixing a Tool:**

1. ✅ Re-run health checklist (above)
2. ✅ Verify fix with simple test:
   - Firebase: `firebase projects:list`
   - Android: `./gradlew :app:tasks`
   - Functions: `npm run build`
3. ✅ Document what was broken and how it was fixed
4. ✅ Update this document if new pattern discovered

---

## 🎯 **SUMMARY**

**Key Principles:**
1. ✅ **Verify before starting** (run health checks)
2. ✅ **Detect failures early** (watch for error patterns)
3. ✅ **Always have a fallback** (don't get blocked)
4. ✅ **Document recovery** (help future sessions)

**Tool Priorities:**
- **P0:** Firebase CLI, Node/npm, Android Gradle (REQUIRED)
- **P1:** TypeScript, Firestore, Cloud Functions (REQUIRED for deployment)
- **P2:** MCP servers (NICE TO HAVE, has fallbacks)
- **P3:** Copilot (OPTIONAL, Cursor Agent is primary)

**Fallback Hierarchy:**
```
MCP Tool → grep/Select-String → Manual inspection
Copilot → Cursor Agent → Manual coding
Firebase CLI → Firebase Console → Direct API
```

---

**End of Tool Health & Fallback Documentation**

