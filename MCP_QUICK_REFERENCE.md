# MCP Quick Reference Card 🚀

**MCP Quick Reference | Optimized Workflow (optional MCPs depend on your `mcp.json`)**

**Last Updated:** January 3, 2026  
**Status:** Gmail, Google Super, Google Sheets disabled via UI toggle (if toggled off)  
**Note:** Serena/MCP changes may require a Cursor restart---

## 🎯 WHEN TO USE EACH MCP

| Need | Use This MCP | Key Tool | Status |
|------|--------------|----------|--------|
| 🧩 Plan complex task | **Sequential Thinking** | `sequential_thinking` | ✅ Active |
| 🔧 Navigate code | **Serena** | `find_symbol` | ✅ Active (when configured) |
| 🔧 Edit code precisely | **Serena** | `replace_symbol_body` | ✅ Active (when configured) |
| 🔧 Find all usages | **Serena** | `find_referencing_symbols` | ✅ Active (when configured) |
| 📚 Get library docs | **Context7** | `get-library-docs` | ✅ Active |
| 🔍 Search web/code | **Exa** | `get_code_context_exa` | ✅ Active |
| 🐙 Commit code | **GitHub** | `push_files` | ✅ Active |
| 🐙 Create PR | **GitHub** | `create_pull_request` | ✅ Active |
| 🧠 Remember preference | **Memory** | `add_memory` | ✅ Active |
| 🧠 Recall past solution | **Memory** | `search_memories` | ✅ Active |
| 🔥 Query Firestore | **Firestore** | `query_collection` | ✅ Active |
| 🔥 Write to Firestore | **Firestore** | `create_document` | ✅ Active |
| 📊 Query spreadsheet | **Sheets** | `execute_sql` | ❌ Disabled |
| 📧 Send email | **Gmail** | `create_email_draft` | ❌ Disabled |
| 🧱 Generate/refine web UI component | **MagicMCP (21st.dev)** | `21st_magic_component_*` | ✅ If installed |
| 🌐 Web automation/testing | **Playwright MCP** | browser actions | ✅ If installed |

---

## ⚡ DECISION TREE

```
┌─────────────────────────┐
│   User Request Arrives  │
└────────────┬────────────┘
             │
             ▼
    ┌────────────────┐
    │ Is it COMPLEX? │  (>5 files/steps, architecture, refactoring)
    │  (Yes/No)      │
    └───┬────────┬───┘
        │        │
       YES      NO
        │        │
        ▼        └──────────────────────┐
┌───────────────────────┐               │
│ Sequential Thinking   │               │
│ (Plan + Analyze)      │               │
└───────────┬───────────┘               │
            │                           │
            └───────────┬───────────────┘
                        │
                        ▼
                ┌───────────────┐
                │ Code-related? │
                └───┬───────┬───┘
                    │       │
                   YES     NO
                    │       │
                    ▼       └──────────────────┐
            ┌─────────────┐                    │
            │   Serena    │                    │
            │ (find/edit) │                    │
            └──────┬──────┘                    │
                   │                           │
                   └──────────┬────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ Library/Docs?    │
                    └──┬───────────┬───┘
                       │           │
                      YES         NO
                       │           │
                       ▼           └──────────────┐
               ┌──────────────┐                   │
               │  Context7    │                   │
               │  (auto-docs) │                   │
               └──────┬───────┘                   │
                      │                           │
                      └──────────┬────────────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │ Web Research?   │
                        └──┬──────────┬───┘
                           │          │
                          YES        NO
                           │          │
                           ▼          └─────────────────┐
                   ┌──────────────┐                     │
                   │ Exa Search   │                     │
                   │ (web+code)   │                     │
                   └──────┬───────┘                     │
                          │                             │
                          └─────────┬───────────────────┘
                                    │
                                    ▼
                            ┌───────────────┐
                            │ Version Ctrl? │
                            └──┬────────┬───┘
                               │        │
                              YES      NO
                               │        │
                               ▼        └────────────────┐
                       ┌──────────────┐                  │
                       │   GitHub     │                  │
                       │ (commit/PR)  │                  │
                       └──────┬───────┘                  │
                              │                          │
                              └─────────┬────────────────┘
                                        │
                                        ▼
                                ┌───────────────┐
                                │ Data/Sheets?  │
                                └──┬────────┬───┘
                                   │        │
                                  YES      NO
                                   │        │
                                   ▼        └───────────────┐
                           ┌──────────────┐                 │
                           │ Google Sheets│                 │
                           │  (SQL/CRUD)  │                 │
                           └──────┬───────┘                 │
                                  │                         │
                                  └────────┬────────────────┘
                                           │
                                           ▼
                                   ┌───────────────┐
                                   │ Email/Gmail?  │
                                   └──┬────────┬───┘
                                      │        │
                                     YES      NO
                                      │        │
                                      ▼        └──────────┐
                              ┌──────────────┐            │
                              │    Gmail     │            │
                              │ (send/fetch) │            │
                              └──────┬───────┘            │
                                     │                    │
                                     └────────┬───────────┘
                                              │
                                              ▼
                                      ┌───────────────┐
                                      │ Web Testing?  │
                                      └──┬────────┬───┘
                                         │        │
                                        YES      NO
                                         │        │
                                         ▼        └─────────┐
                                 ┌──────────────┐           │
                                 │   Browser    │           │
                                 │ (test/scrape)│           │
                                 └──────┬───────┘           │
                                        │                   │
                                        └───────┬───────────┘
                                                │
                                                ▼
                                        ┌───────────────┐
                                        │ Firestore DB? │
                                        └──┬────────┬───┘
                                           │        │
                                          YES      NO
                                           │        │
                                           ▼        └────┐
                                   ┌──────────────┐      │
                                   │  Firestore   │      │
                                   │ (query/write)│      │
                                   └──────┬───────┘      │
                                          │              │
                                          └──────┬───────┘
                                                 │
                                                 ▼
                                         ┌───────────────┐
                                         │ Remember it?  │
                                         └──┬────────┬───┘
                                            │        │
                                           YES      NO
                                            │        │
                                            ▼        │
                                    ┌──────────────┐ │
                                    │    Memory    │ │
                                    │ (add/search) │ │
                                    └──────┬───────┘ │
                                           │         │
                                           └────┬────┘
                                                │
                                                ▼
                                            ┌───────┐
                                            │ DONE  │
                                            └───────┘
```

---

## 🔥 MOST COMMON WORKFLOWS

### 1. Simple Code Edit
```
Serena find_symbol → Serena replace_symbol_body → Done
```

### 2. Library Integration
```
Context7 resolve-library-id → Context7 get-library-docs → 
Serena search_for_pattern → Serena replace_content → Done
```

### 3. Complex Refactoring
```
Sequential Thinking (plan) → Memory search (past patterns) → 
Serena find_referencing_symbols (impact) → Serena replace_symbol_body → 
Memory add_memory (store pattern) → GitHub push_files → Done
```

### 4. Bug Investigation
```
Sequential Thinking (hypotheses) → Memory search (similar bugs) → 
Serena find_symbol → Context7 get-library-docs → 
Exa get_code_context_exa → Serena replace_content → Done
```

### 5. Web Feature Testing
```
Playwright (preferred) or Browser MCP:
navigate → snapshot → click/type → console_messages/network_requests → Done
```

### 6. Data Analysis + Report
```
Sheets search_spreadsheets → Sheets execute_sql → 
Sheets create_chart → Gmail create_email_draft → Done
```

---

## 🚫 ANTI-PATTERNS (AVOID!)

| ❌ DON'T | ✅ DO INSTEAD |
|---------|---------------|
| Read entire file | `Serena get_symbols_overview` |
| Use grep in terminal | `Serena search_for_pattern` |
| Guess API behavior | `Context7 get-library-docs` |
| Make 10 single-file commits | `GitHub push_files` (batch) |
| Navigate browser back/forth | `browser_tabs` (parallel) |
| Start complex refactor blind | `Sequential Thinking` first |
| Forget to cite memory | Always cite `[[memory:ID]]` |
| Skip browser error checks | `browser_console_messages` |
| Store new memory without search | `search_memories` first |

---

## 💡 PRO TIPS

### 1. Parallel Everything
```typescript
// ✅ GOOD: Parallel reads (3x faster)
await Promise.all([
  read_file("a.kt"),
  read_file("b.kt"),
  read_file("c.kt")
])

// ❌ BAD: Sequential (3x slower)
await read_file("a.kt")
await read_file("b.kt")
await read_file("c.kt")
```

### 2. Use Serena Depth Parameter
```typescript
// Shallow overview (fast)
find_symbol(name_path="Foo", depth=0, include_body=false)

// Include top-level methods
find_symbol(name_path="Foo", depth=1, include_body=false)

// Deep dive with bodies (slow, only when needed)
find_symbol(name_path="Foo", depth=2, include_body=true)
```

### 3. Context7 Topic Filtering
```typescript
// Generic (5000 tokens)
get-library-docs(id="/kotlinx/coroutines")

// Focused (faster, more relevant)
get-library-docs(id="/kotlinx/coroutines", topic="Flow operators")
```

### 4. Exa Token Budgeting
```typescript
// Quick lookup (1-2K tokens)
get_code_context_exa(query="React hooks", tokensNum=2000)

// Comprehensive research (10-50K tokens)
get_code_context_exa(query="Next.js 15 architecture", tokensNum=20000)
```

### 5. Memory Citation
```typescript
// ❌ BAD: No citation
"I'll use the -la flag you prefer"

// ✅ GOOD: With citation
"I'll use the -la flag [[memory:12345]] you prefer"
```

---

## 📊 COMPLEXITY HEURISTICS

| Task Complexity | Tools Needed | Start With |
|----------------|--------------|------------|
| Trivial (1 step) | 1 tool | Direct tool call |
| Simple (2-3 steps) | 2-3 tools | Most specific tool |
| Moderate (4-5 steps) | 3-5 tools | Memory search → tools |
| Complex (6-10 steps) | 5-10 tools | **Sequential Thinking** |
| Very Complex (>10 steps) | 10+ tools | **Sequential Thinking** + Memory |

---

## 🎯 CHECKLIST FOR EVERY TASK

### Before Starting:
- [ ] Is task complex (>5 steps)? → Sequential Thinking
- [ ] Search Memory for past solutions
- [ ] Library involved? → Context7
- [ ] Code navigation? → Serena (not full file read)

### During Execution:
- [ ] Parallel independent operations
- [ ] Use specialized tools (Serena > grep)
- [ ] Batch operations (GitHub push_files, Sheets batch_update)

### After Completion:
- [ ] Store learnings in Memory
- [ ] Cite memories used
- [ ] Commit with meaningful message
- [ ] Store reasoning pattern if complex

---

## 🧯 TOOL FAILURE POLICY (NO SILENT DEGRADATION)
If an MCP/tool is degraded or unavailable:
- **Say so explicitly** (PASS/WARN/FAIL + what’s impacted)
- **Switch to fallback immediately** (e.g., Serena indexing → pattern search + targeted reads/edits; Playwright → Browser MCP/manual)
- **Provide exact restore steps** (restart Cursor/MCP server, fix config, reindex, etc.)

---

## 🔗 QUICK LINKS

- **Full Tool Inventory:** `TOOLS_INVENTORY.md`
- **Optimization Rules:** `.cursorrules`
- **Change Summary:** `MCP_OPTIMIZATION_SUMMARY.md`
- **MCP Config:** `c:\Users\ynotf\.cursor\mcp.json`

---

## 📞 EMERGENCY TROUBLESHOOTING

| Issue | Solution |
|-------|----------|
| MCP not working | Restart Cursor |
| Tool not found | Check `mcp.json` config |
| Slow performance | Check for sequential calls (parallelize!) |
| Repeated mistakes | Use Sequential Thinking for planning |
| Forgotten preference | Search Memory, not ask again |
| Complex refactor failing | Stop → Sequential Thinking → Plan → Retry |

---

**Last Updated:** December 23, 2025  
**Version:** 2.0 (Sequential Thinking integrated)  
**Status:** ✅ Production-ready after Cursor restart



