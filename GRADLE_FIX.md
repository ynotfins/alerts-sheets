# Gradle Language Server - Setup & Fix

**⚠️ NOTE: This issue is specific to laptop development environment**  
**Context:** Gradle Language Server initialization hanging in Cursor IDE on Windows laptop

**Issue:** "Initializing Gradle Language Server" not completing  
**Root Cause:** JAVA_HOME not set in Windows environment  
**Affects:** Local laptop development only (not Android Studio, not CI/CD)

---

## 🖥️ Laptop-Specific Context

This Gradle issue occurs specifically when:
- Working in **Cursor/VS Code** on Windows laptop
- Opening the `android/` directory for Kotlin development
- Gradle Language Server extension trying to initialize

**Does NOT affect:**
- Building APKs (Gradle wrapper works fine: `.\gradlew.bat`)
- Android Studio (has its own JDK bundled)
- CI/CD pipelines (use containerized environments)
- Other team members (if they have JAVA_HOME set)

**Why laptop-specific:**
- Fresh Windows install or clean environment
- JAVA_HOME environment variable not configured
- Cursor needs system-level Java to run Gradle Language Server
- Android's Gradle wrapper works independently (doesn't need JAVA_HOME)

---

## ✅ Quick Fix for Gradle Language Server

### Step 1: Find Your Java Installation

```powershell
# Check if Java is installed
where java

# OR check in common locations:
# C:\Program Files\Java\jdk-17.0.x
# C:\Program Files\Android\Android Studio\jbr
# C:\Users\{user}\AppData\Local\Android\Sdk\jre
```

### Step 2: Set JAVA_HOME (Windows)

**Option A: Use Android Studio's Embedded JDK (Recommended)**
```powershell
# Set JAVA_HOME to Android Studio's JDK
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"

# Add to PATH
setx PATH "%PATH%;%JAVA_HOME%\bin"
```

**Option B: Use Standalone JDK 17**
```powershell
# If you have JDK 17 installed separately
setx JAVA_HOME "C:\Program Files\Java\jdk-17"
setx PATH "%PATH%;%JAVA_HOME%\bin"
```

### Step 3: Verify Java

```powershell
# Close and reopen PowerShell, then:
java -version

# Should output: java version "17.x.x"
```

### Step 4: Restart Cursor/VS Code

After setting JAVA_HOME:
1. **Close Cursor completely**
2. **Reopen Cursor**
3. **Open your project**
4. Gradle Language Server should now initialize successfully

---

## 🔧 Alternative: Use Gradle Wrapper (No JAVA_HOME needed)

The project already has Gradle Wrapper, which bundles Java. You can use it directly:

```powershell
cd D:\Github\alerts-sheets\alerts-sheets\android

# Use gradlew.bat (wrapper script)
.\gradlew.bat tasks
```

The Gradle Wrapper will download and use the correct Java version automatically.

---

## 📋 For Cursor Gradle Extension

If you're using the "Gradle for Java" extension in Cursor:

1. **Open Cursor Settings** (Ctrl + ,)
2. Search for **"Java Home"**
3. Set **"Java: Configuration: Runtimes"**:
   ```json
   {
     "java.configuration.runtimes": [
       {
         "name": "JavaSE-17",
         "path": "C:\\Program Files\\Android\\Android Studio\\jbr",
         "default": true
       }
     ]
   }
   ```

4. **Reload Window**: Ctrl+Shift+P → "Developer: Reload Window"

---

## 🎯 Expected Behavior After Fix

Once Java is configured:
- ✅ Gradle Language Server initializes successfully
- ✅ Syntax highlighting in `.gradle` files
- ✅ Auto-completion for dependencies
- ✅ Gradle task runner appears
- ✅ Build errors show inline

---

## 🔍 Troubleshooting

### Gradle still not working:
```powershell
# 1. Check JAVA_HOME is set correctly
echo %JAVA_HOME%

# 2. Check Java version
java -version

# 3. Stop all Gradle daemons
cd android
.\gradlew.bat --stop

# 4. Clean and rebuild
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
```

### Gradle initializes but hangs:
- Check Windows Defender isn't blocking Gradle
- Add exclusion: `D:\Github\alerts-sheets\alerts-sheets\android\.gradle`
- Add exclusion: `C:\Users\{user}\.gradle`

---

## 📝 Permanent Solution

Add this to your **PowerShell Profile** to persist JAVA_HOME:

```powershell
# Edit profile
notepad $PROFILE

# Add this line:
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:PATH;$env:JAVA_HOME\bin"
```

---

**After setting JAVA_HOME and restarting Cursor, Gradle Language Server should work!** ✅

