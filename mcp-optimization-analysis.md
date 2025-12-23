# MCP Server Optimization for Android Project

## Current Configuration (11 servers)
1. ✅ Sequential Thinking - **KEEP**
2. ✅ Serena - **KEEP**
3. ✅ Context7 - **KEEP**
4. ✅ GitHub - **KEEP**
5. ✅ Memory Tool - **KEEP**
6. ✅ Exa Search - **KEEP**
7. ❌ Gmail - **DISABLE**
8. ❌ Google Super - **DISABLE**
9. 🤔 Google Sheets - **OPTIONAL** (disable for refactoring, enable for integration tests)
10. 🤔 Firestore MCP - **OPTIONAL** (keep if need programmatic testing)

## Detailed Analysis

### DEFINITELY DISABLE (2 servers)

#### 1. Gmail MCP ❌
**Reason:** No email functionality in Android app
- App doesn't send/receive emails
- Not used in development workflow
- Pure overhead with no benefit

**Impact:** None

---

#### 2. Google Super MCP ❌
**Reason:** Redundant and experimental
- Consolidates Gmail + Sheets (both not critical)
- Still in testing phase
- If we disable Gmail/Sheets, this is pointless

**Impact:** None

---

### OPTIONAL DISABLE (2 servers)

#### 3. Google Sheets MCP 🤔
**Keep if:** You want to programmatically verify webhook delivery during tests
```kotlin
// With Sheets MCP:
@Test
fun `verify event delivered to Sheet`() {
    sendNotification()
    Thread.sleep(2000)
    val rows = mcp_Google_Sheets_batch_get(spreadsheetId, "A1:Z100")
    assertThat(rows).contains("BNN_CAPTURE_SUCCESS")
}
```

**Disable if:** You're okay manually checking Google Sheets via browser
```kotlin
// Without Sheets MCP:
@Test
fun `verify HTTP POST returns 200`() {
    val response = sendToWebhook()
    assertThat(response.code).isEqualTo(200)
    // Then manually open Sheet to verify
}
```

**Recommendation:** **DISABLE for now** (refactoring phase doesn't need it)
- Can always re-enable later for integration testing
- Saves startup time during heavy refactoring work
- Use curl/Postman for endpoint testing

---

#### 4. Firestore MCP 🤔
**Keep if:** You want programmatic access to verify deliveries
```kotlin
// With Firestore MCP:
@Test
fun `verify event in Firestore`() {
    sendNotification()
    val docs = mcp_firestore_query_collection("events", filters)
    assertThat(docs).hasSize(1)
}
```

**Disable if:** Firebase Console is sufficient for verification
```kotlin
// Without Firestore MCP:
@Test
fun `verify HTTP POST to Firestore URL works`() {
    val response = sendToFirestoreEndpoint()
    assertThat(response.code).isEqualTo(200)
    // Then check Firebase Console manually
}
```

**Recommendation:** **KEEP for now** (might be useful for testing)
- Low overhead
- App sends to Firestore ingest URL
- Useful for debugging failed deliveries

---

## Recommended Configuration

### Option A: Aggressive (Minimal - 6 servers)
**Best for:** Pure refactoring work, no integration testing yet

```json
{
  "mcpServers": {
    "sequential-thinking": { ... },
    "serena": { ... },
    "context7-mcp": { ... },
    "github": { ... },
    "Memory Tool": { ... },
    "Exa Search": { ... }
  }
}
```

**Disabled:** Gmail, Google Super, Google Sheets, Firestore MCP

---

### Option B: Conservative (Moderate - 7 servers)
**Best for:** Refactoring + occasional integration testing

```json
{
  "mcpServers": {
    "sequential-thinking": { ... },
    "serena": { ... },
    "context7-mcp": { ... },
    "github": { ... },
    "Memory Tool": { ... },
    "Exa Search": { ... },
    "firestore-mcp": { ... }  // Keep for testing
  }
}
```

**Disabled:** Gmail, Google Super, Google Sheets

---

### Option C: Keep All (Current - 10 servers)
**Best for:** Not sure yet, want all options available

**Disabled:** None (except maybe Google Super as truly redundant)

---

## Performance Impact

| Configuration | Servers | Startup Time | Tool Noise | Recommendation |
|---------------|---------|--------------|------------|----------------|
| Aggressive (A) | 6 | Fastest | Minimal | ⭐ **For refactoring** |
| Conservative (B) | 7 | Fast | Low | ⭐ **Balanced** |
| Current (C) | 10 | Slower | High | For later phases |

---

## Recommendation: Go with Option B (Conservative)

### Why:
1. **Definitely remove:** Gmail, Google Super (zero value)
2. **Disable Google Sheets:** Can re-enable later for integration tests
3. **Keep Firestore:** Low overhead, might be useful
4. **Keep all 6 core servers:** Essential for refactoring

### Benefits:
- ✅ Faster Cursor startup
- ✅ Less tool noise in autocomplete
- ✅ Still have Firestore for testing if needed
- ✅ Can re-enable Sheets later with 1-minute config change

### What You Keep:
- Sequential Thinking (complex planning)
- Serena (code navigation/refactoring)
- Context7 (Android/Kotlin docs)
- GitHub (version control)
- Memory (pattern storage)
- Exa (research/debugging)
- Firestore (delivery testing)

### What You Lose:
- Nothing important for refactoring work
- Gmail automation (not needed)
- Browser testing (not a web app)
- Google Super (redundant)
- Google Sheets programmatic access (can re-enable anytime)

---

## Migration Path

1. **Now (Refactoring Phase):** Use Option B (7 servers)
2. **Later (Integration Testing):** Re-enable Google Sheets if needed
3. **Future:** If you add web dashboard, re-enable Browser MCP

---

## Command to Apply

```bash
# Backup current config
cp ~/.cursor/mcp.json ~/.cursor/mcp.json.backup

# Apply optimized config (I'll provide updated JSON)
```


