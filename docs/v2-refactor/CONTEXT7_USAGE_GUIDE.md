# Context7 Configuration Guide for AlertsToSheets

**Date:** Dec 19, 2025  
**Purpose:** Configure Context7 for Android/Kotlin development best practices

---

## 📚 **WHAT IS CONTEXT7?**

Context7 is a real-time bridge to **4,000+ open-source libraries** that provides:
- ✅ Latest library documentation
- ✅ Up-to-date code examples
- ✅ Best practices from source repositories
- ✅ Version-specific API references

**Key Benefit:** No more outdated AI suggestions based on old training data!

---

## ✅ **CURRENT SETUP STATUS**

### **MCP Configuration** ✅
Your `mcp.json` is correctly configured:

```json
{
  "context7-mcp": {
    "type": "http",
    "url": "https://server.smithery.ai/@upstash/context7-mcp/mcp?api_key=2fdec5a2-cde8-4678-8995-96087f120c87",
    "headers": {}
  }
}
```

**Location:** `c:\Users\ynotf\.cursor\mcp.json`  
**Status:** ✅ Active and ready to use

---

## 🎯 **HOW TO USE CONTEXT7**

### **Method 1: Explicit Query (Manual)**

When asking for code help, prefix with library ID:

```
"Show me how to use Kotlin coroutines properly"
use library /kotlin/kotlinx.coroutines

"Best practices for OkHttp3 network requests"
use library /square/okhttp

"How to implement Android WorkManager"
use library /android/android-workmanager
```

### **Method 2: Auto-Invoke (Recommended)**

Add this rule to Cursor so Context7 is **automatically used**:

**Location:** `Cursor Settings → Rules`

```
Always use context7 when I need:
- Code generation
- Library setup or configuration
- API documentation
- Best practices for any framework or library

Automatically invoke Context7 MCP tools without me explicitly asking.
This applies to Android, Kotlin, Jetpack libraries, coroutines, networking, 
database operations, and any third-party libraries.
```

---

## 📖 **RELEVANT LIBRARIES FOR ALERTSTOsheets**

### **Android Core Libraries**

| Library | Context7 ID | Purpose |
|---------|-------------|---------|
| **Kotlin Coroutines** | `/kotlin/kotlinx.coroutines` | Async operations |
| **Android Lifecycle** | `/android/androidx.lifecycle` | Lifecycle management |
| **Android Room** | `/android/androidx.room` | Database (future migration) |
| **Android WorkManager** | `/android/android-workmanager` | Background jobs |
| **Jetpack Compose** | `/android/androidx.compose` | Modern UI (future) |

### **Networking Libraries**

| Library | Context7 ID | Purpose |
|---------|-------------|---------|
| **OkHttp** | `/square/okhttp` | HTTP client (we use this!) |
| **Retrofit** | `/square/retrofit` | Type-safe HTTP (future?) |
| **Ktor** | `/kotlin/ktor` | Alternative HTTP client |

### **JSON/Data Libraries**

| Library | Context7 ID | Purpose |
|---------|-------------|---------|
| **Gson** | `/google/gson` | JSON parsing (we use this!) |
| **Moshi** | `/square/moshi` | Modern JSON parser |
| **Kotlin Serialization** | `/kotlin/kotlinx.serialization` | Native Kotlin JSON |

### **Dependency Injection**

| Library | Context7 ID | Purpose |
|---------|-------------|---------|
| **Hilt** | `/android/hilt` | DI for Android |
| **Dagger** | `/google/dagger` | Advanced DI |
| **Koin** | `/insert-koin/koin` | Simple DI |

---

## 🔧 **CONTEXT7 API USAGE**

### **Retrieve Documentation (Direct API)**

**Endpoint:** `https://api.context7.com/docs`

**Example Request:**

```bash
curl -X POST https://api.context7.com/docs \
  -H "CONTEXT7_API_KEY: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "library": "square/okhttp",
    "topic": "async requests",
    "version": "latest"
  }'
```

**Response:**
```json
{
  "library": "square/okhttp",
  "version": "4.12.0",
  "documentation": "...",
  "code_examples": ["..."],
  "best_practices": ["..."]
}
```

### **Resolve Library ID**

**Endpoint:** `https://api.context7.com/resolve`

```bash
curl -X POST https://api.context7.com/resolve \
  -H "CONTEXT7_API_KEY: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "kotlin coroutines"
  }'
```

**Response:**
```json
{
  "library_id": "kotlin/kotlinx.coroutines",
  "name": "Kotlin Coroutines",
  "description": "Library support for Kotlin coroutines"
}
```

---

## 💡 **USAGE EXAMPLES FOR OUR PROJECT**

### **Example 1: Validate Coroutine Usage**

**Query:**
```
use library /kotlin/kotlinx.coroutines

Review our DataPipeline.kt coroutine implementation:
- Are we using SupervisorJob correctly?
- Is Dispatchers.IO appropriate for network/disk I/O?
- Any issues with structured concurrency?
```

**Expected Response:**
Context7 will fetch the latest coroutines documentation and validate our implementation.

---

### **Example 2: OkHttp Best Practices**

**Query:**
```
use library /square/okhttp

We're using OkHttp for HTTP POST requests in HttpClient.kt.
What are the best practices for:
- Connection pooling
- Timeout configuration
- Error handling
- Request retries
```

---

### **Example 3: Room Database Migration**

**Query:**
```
use library /android/androidx.room

We're currently using JSON file storage. Show me:
1. How to migrate to Room Database
2. Best practices for repositories with Room
3. How to handle migrations
4. LiveData vs Flow for observations
```

---

## 🎓 **BEST PRACTICES**

### **When to Use Context7:**

✅ **Before implementing new features**
```
"use library /android/androidx.lifecycle
How should I implement ViewModel in Android?"
```

✅ **When debugging issues**
```
"use library /kotlin/kotlinx.coroutines
Why is my coroutine not cancelling properly?"
```

✅ **For API validation**
```
"use library /square/okhttp
Is this the correct way to handle OkHttp responses?"
```

✅ **During code review**
```
"use library /google/gson
Review this JSON parsing code for best practices"
```

### **Security Best Practices:**

1. ✅ **Never commit API keys** - Already in `.gitignore`
2. ✅ **Use environment variables** - API key in `mcp.json`
3. ✅ **Rotate keys regularly** - Via Context7 dashboard
4. ✅ **Monitor usage** - Check Context7 dashboard for limits

---

## 📊 **CONTEXT7 RATE LIMITS**

### **With API Key (Your Current Plan):**

| Tier | Requests/Month | Cost |
|------|----------------|------|
| **Free** | 500 | $0 |
| **Pro** | 10,000 | $7/month |
| **Enterprise** | Custom | Custom |

**Your Current Status:** Free tier (500 requests/month)

**Monitor Usage:**
- Dashboard: https://context7.com/dashboard
- API Keys: https://context7.com/dashboard/api-keys
- Usage Stats: https://context7.com/dashboard/usage

---

## 🚀 **RECOMMENDED WORKFLOW**

### **For New Features (V3+):**

1. **Before coding:**
   ```
   use library /relevant-library-id
   "What are the best practices for [feature]?"
   ```

2. **During coding:**
   ```
   use library /relevant-library-id
   "Show me an example of [specific implementation]"
   ```

3. **After coding:**
   ```
   use library /relevant-library-id
   "Review this implementation for best practices"
   ```

4. **For debugging:**
   ```
   use library /relevant-library-id
   "Why is [error message] happening?"
   ```

---

## 📝 **ADDING CUSTOM LIBRARIES (Advanced)**

If you create your own library and want it indexed by Context7:

1. **Create `context7.json` in repo root:**

```json
{
  "name": "alerts-sheets",
  "version": "2.0.0",
  "description": "Android notification/SMS monitoring",
  "exclude": [
    "node_modules",
    "build",
    ".gradle"
  ],
  "entrypoint": "android/app/src/main/java",
  "rules": [
    "This library implements God Mode permissions for Android",
    "Per-source auto-clean is a key feature",
    "Use SourceManager for managing notification sources"
  ]
}
```

2. **Submit to Context7:**
   - Dashboard → Libraries → Add Library
   - Or API: `POST https://api.context7.com/libraries`

---

## ✅ **CURRENT CONFIGURATION SUMMARY**

**Status:**
- ✅ Context7 MCP configured in Cursor
- ✅ API key set up
- ✅ Smithery integration active
- ⚠️ Auto-invoke rule not yet added (recommended)
- ⚠️ Not actively querying during development yet

**Recommendation:**
1. Add auto-invoke rule to Cursor settings
2. Use Context7 for all v3 development
3. Query before implementing new features
4. Validate existing code against latest docs

---

## 📚 **DOCUMENTATION LINKS**

- [Context7 Installation](https://context7.com/docs/installation)
- [Usage Guide](https://context7.com/docs/usage)
- [API Reference](https://context7.com/docs/api-reference)
- [Adding Libraries](https://context7.com/docs/adding-libraries)
- [Dashboard](https://context7.com/dashboard)

---

## 🎯 **ACTION ITEMS**

**Immediate:**
1. ✅ Context7 configured (DONE)
2. 🟡 Add auto-invoke rule to Cursor
3. 🟡 Test with a query: `use library /kotlin/kotlinx.coroutines "validate our DataPipeline"`

**For V3 Development:**
1. Query Context7 before implementing features
2. Use for architecture validation
3. Check best practices for new libraries
4. Validate against latest API versions

---

**Status:** ✅ **READY TO USE**  
**Next:** Add auto-invoke rule and start querying!


