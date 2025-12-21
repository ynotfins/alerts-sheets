# MCP Servers Installation & Configuration Guide

**Date:** December 18, 2025  
**Project:** alerts-sheets (Android/Kotlin)

---

## ✅ Successfully Installed MCP Servers

### 1. **mcp-sqlite** (SQLite Database MCP)
- **Status:** ✅ Installed globally via npm (v1.0.7)
- **Purpose:** Query your `alert_sheets_queue.db` database directly from Cursor
- **Location:** `npm global packages`

### 2. **Android MCP Server** (ADB Control)
- **Status:** ✅ Installed with Python dependencies
- **Purpose:** Control Android devices via ADB from Cursor
- **Location:** `C:\Users\ynotf\android-mcp-server\`
- **Python Version:** 3.13.9

---

## 📝 How to Add MCP Servers to Cursor

### Method 1: Via Cursor Settings UI (Recommended)

1. **Open Cursor Settings:**
   - Press `Ctrl + ,` (Windows)
   - Or go to File → Settings

2. **Navigate to MCP Servers:**
   - Search for "MCP" in settings
   - Go to Extensions → Model Context Protocol

3. **Add SQLite MCP Server:**
   ```json
   {
     "mcpServers": {
       "sqlite": {
         "command": "npx",
         "args": [
           "-y",
           "mcp-sqlite",
           "--db-path",
           "D:\\Github\\alerts-sheets\\alerts-sheets\\android\\app\\databases\\alert_sheets_queue.db"
         ]
       }
     }
   }
   ```

4. **Add Android MCP Server:**
   ```json
   {
     "mcpServers": {
       "android": {
         "command": "python",
         "args": [
           "C:\\Users\\ynotf\\android-mcp-server\\server.py"
         ],
         "env": {
           "ANDROID_DEVICE_SERIAL": "auto"
         }
       }
     }
   }
   ```

### Method 2: Manual Configuration File

If Cursor uses a configuration file (check `%APPDATA%\Cursor\User\` or workspace `.cursor/` folder):

**Full Configuration:**
```json
{
  "mcpServers": {
    "memory-bank": {
      "command": "npx",
      "args": ["-y", "mcp-memory-bank"]
    },
    "sqlite": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-sqlite",
        "--db-path",
        "D:\\Github\\alerts-sheets\\alerts-sheets\\android\\app\\databases\\alert_sheets_queue.db"
      ]
    },
    "android": {
      "command": "python",
      "args": [
        "C:\\Users\\ynotf\\android-mcp-server\\server.py"
      ],
      "env": {
        "ANDROID_DEVICE_SERIAL": "auto"
      }
    }
  }
}
```

---

## 🎯 Usage Examples

### Using SQLite MCP Server

Once configured, you can ask Cursor:

```
"Show me all pending requests in the queue database"
"How many requests have retryCount > 5?"
"List the most recent 10 queue entries"
```

Cursor will query your SQLite database directly!

### Using Android MCP Server

Once configured, you can ask Cursor:

```
"Take a screenshot of my Android device"
"Install the latest APK build"
"Run adb logcat and show me BNN notifications"
"List all installed packages"
```

---

## 🔧 Additional Setup Steps

### For Android MCP Server:

1. **Enable USB Debugging on your Android device:**
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back → Developer Options → Enable USB Debugging

2. **Connect device and verify ADB:**
   ```powershell
   adb devices
   ```
   You should see your device listed.

3. **Test Android MCP Server:**
   ```powershell
   cd C:\Users\ynotf\android-mcp-server
   python server.py
   ```

### For SQLite MCP:

The database path is correct once your app creates it:
```
D:\Github\alerts-sheets\alerts-sheets\android\app\databases\alert_sheets_queue.db
```

**Note:** This path only exists on device/emulator. To access:
```powershell
adb pull /data/data/com.example.alertsheets/databases/alert_sheets_queue.db D:\Github\alerts-sheets\alerts-sheets\android\app\databases\
```

---

## 🚫 Disabled MCP Servers (As Requested)

You disabled these and they are NOT included in the configuration:
- ❌ Firecrawl
- ❌ Context7
- ❌ Docker
- ❌ Postgres

---

## 📊 MCP Server Status Summary

| MCP Server | Status | Purpose | Location |
|------------|--------|---------|----------|
| **memory-bank** | ✅ Keep | Project documentation/context | npm global |
| **mcp-sqlite** | ✅ **NEW** | Query queue database | npm global |
| **android-mcp** | ✅ **NEW** | Control Android device via ADB | `C:\Users\ynotf\android-mcp-server\` |
| **cursor-browser-extension** | ⚠️ Optional | Test Apps Script in browser | Built-in |
| **firecrawl** | ❌ Disabled | Web scraping (not needed) | - |
| **context7** | ❌ Disabled | Context fetching (not needed) | - |
| **docker** | ❌ Disabled | Docker management (not needed) | - |
| **postgres** | ❌ Disabled | PostgreSQL (you use SQLite) | - |

---

## 🔍 Verifying Installation

### Test npm packages:
```powershell
npm list -g mcp-sqlite
```

### Test Python packages:
```powershell
python -c "import mcp; print('MCP installed')"
python -c "import adb_shell; print('ADB Shell installed')"
```

### Test Android MCP Server:
```powershell
cd C:\Users\ynotf\android-mcp-server
python server.py
```
You should see: "MCP server running..."

---

## 🚀 Next Steps

1. **Restart Cursor** after adding MCP server configurations
2. **Test SQLite queries:**
   - Ask Cursor: "Show me the queue database schema"
3. **Test Android control:**
   - Connect your device via USB
   - Ask Cursor: "Take a screenshot of my Android device"
4. **Create queries:**
   - "Find all failed requests in queue"
   - "Show me notifications sent in last hour"

---

## 🆘 Troubleshooting

### SQLite MCP not working:
- Verify database path exists (pull from device first)
- Check `mcp-sqlite` is installed: `npm list -g mcp-sqlite`

### Android MCP not working:
- Verify ADB is in PATH: `adb version`
- Check device connected: `adb devices`
- Verify Python packages: `pip list | findstr mcp`

### Cursor not recognizing MCP servers:
- Restart Cursor completely
- Check Cursor version supports MCP (v0.40+)
- Look for MCP settings in: Settings → Extensions → Model Context Protocol

---

## 📚 Additional Resources

- **mcp-sqlite docs:** https://www.npmjs.com/package/mcp-sqlite
- **Android MCP Server:** https://github.com/minhalvp/android-mcp-server
- **Cursor MCP docs:** Check Cursor documentation for latest MCP integration guide

---

**Installation completed successfully! ✅**
**Date:** December 18, 2025


