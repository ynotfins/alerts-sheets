# Complete MCP Tool Inventory
**Last Updated:** December 23, 2025  
**Total MCP Servers Configured:** 10  
**Active MCP Servers:** 7 (after UI toggle disable)  
**Total Tools Available:** 70+

---

## 📊 MCP SERVERS OVERVIEW

| # | Server | Status | Tools Count | Primary Use |
|---|--------|--------|-------------|-------------|
| 1 | Sequential Thinking | ✅ Active | 1 | Complex problem planning & analysis |
| 2 | Serena | ⏸️ Not Yet Active | 20+ | Symbolic code navigation & editing (requires Cursor restart) |
| 3 | Context7 | ✅ Active | 2 | Live library/framework documentation |
| 4 | GitHub | ✅ Active | 30+ | Repository management, PRs, issues |
| 5 | Memory Tool (Mem0) | ✅ Active | 10 | Context storage & retrieval |
| 6 | Exa Search | ✅ Active | 2 | Real-time web + code search |
| 7 | Firestore MCP | ✅ Active | 20+ | Firestore database operations |
| 8 | Google Sheets | ❌ Disabled | 30+ | Spreadsheet automation (toggled off via UI) |
| 9 | Gmail | ❌ Disabled | 15+ | Email automation (toggled off via UI) |
| 10 | Google Super | ❌ Disabled | TBD | Multi-service Google (toggled off via UI) |

**Note:** Browser MCP was never configured, not counted in total.

---

## 🧩 1. SEQUENTIAL THINKING MCP

### Tools:
- **`sequential_thinking`** - Structured reasoning for complex problems

### Use Cases:
- Multi-step refactoring planning
- Architecture decision analysis
- Debugging hypothesis generation
- Migration strategy planning
- Security threat modeling
- Performance optimization analysis

### Example:
```
Task: Refactor monolithic Android app to Clean Architecture
↓
Sequential Thinking Output:
1. Analyze current dependencies
2. Identify bounded contexts
3. Plan migration phases
4. Risk assessment per phase
5. Rollback strategies
6. Testing checkpoints
```

---

## 🔧 2. SERENA MCP (Code Intelligence)

### Tools:
- **`find_symbol`** - Search for symbols (functions, classes, variables) by name/pattern
- **`get_symbols_overview`** - Get hierarchical symbol structure without reading full file
- **`find_referencing_symbols`** - Find all code that references a symbol
- **`replace_symbol_body`** - Replace entire symbol definition
- **`replace_content`** - Regex-based replacements within files
- **`insert_after_symbol`** - Insert code after a symbol
- **`insert_before_symbol`** - Insert code before a symbol
- **`search_for_pattern`** - Fast pattern search across codebase

### Use Cases:
- Navigate large codebases surgically
- Understand call chains and dependencies
- Refactor functions/classes precisely
- Find all usages before breaking changes
- Make targeted regex-based edits

### Example:
```kotlin
// Find all usages of deprecated function
find_symbol(name_path="oldFunction")
find_referencing_symbols(name_path="oldFunction")
// Replace all call sites
replace_symbol_body(name_path="oldFunction", new_body="...")
```

---

## 📚 3. CONTEXT7 MCP (Documentation)

### Tools:
- **`resolve-library-id`** - Convert library name to Context7 ID
- **`get-library-docs`** - Fetch up-to-date documentation

### Use Cases:
- Get latest API docs for any library
- Best practices for frameworks
- Migration guides (e.g., Kotlin 1.x → 2.x)
- Error message explanations
- Code examples for APIs

### Example:
```
User: "How do I use Kotlin Coroutines Flow?"
↓
resolve-library-id(libraryName="Kotlin Coroutines")
get-library-docs(context7CompatibleLibraryID="/kotlinx/coroutines", topic="Flow")
→ Returns 5000 tokens of Flow documentation + examples
```

---

## 🐙 4. GITHUB MCP (Version Control)

### Tools (20+):
- **Repository Management:**
  - `create_repository`
  - `fork_repository`
  - `search_repositories`
  - `get_file_contents`
  - `create_branch`
  
- **File Operations:**
  - `create_or_update_file` (single file)
  - `push_files` (multi-file atomic commit)
  
- **Issues & PRs:**
  - `create_issue`
  - `list_issues`
  - `get_issue`
  - `update_issue`
  - `add_issue_comment`
  - `create_pull_request`
  - `get_pull_request`
  - `list_pull_requests`
  - `merge_pull_request`
  - `create_pull_request_review`
  - `get_pull_request_files`
  - `get_pull_request_status`
  
- **Search:**
  - `search_code`
  - `search_issues`
  - `search_users`
  
- **Commits:**
  - `list_commits`

### Use Cases:
- Create/update files in remote repos
- Manage issues and pull requests
- Search code across GitHub
- Automated releases
- Code reviews

### Example:
```
// Multi-file commit
push_files(
  owner="user",
  repo="alerts-sheets",
  branch="main",
  files=[
    {path: "README.md", content: "..."},
    {path: "src/main.kt", content: "..."}
  ],
  message="feat: Add new feature"
)
```

---

## 🧠 5. MEM0 MEMORY MCP (Context Persistence)

### Tools:
- **`add_memory`** - Store new memory
- **`search_memories`** - Semantic search across memories
- **`get_memories`** - Paginated memory retrieval
- **`get_memory`** - Fetch single memory by ID
- **`update_memory`** - Update existing memory
- **`delete_memory`** - Delete single memory
- **`delete_all_memories`** - Clear all memories for entity
- **`list_entities`** - List users/agents/apps with stored memories

### Use Cases:
- Store user preferences
- Cache architecture decisions
- Remember project conventions
- Track repeated issues and solutions
- Store reasoning patterns from Sequential Thinking

### Example:
```
// Store refactoring pattern
add_memory(
  text="When refactoring Android activities, always: 1) Extract business logic to ViewModels, 2) Use Hilt for DI, 3) Test ViewModels with JUnit",
  user_id="me"
)

// Later retrieve
search_memories(query="Android refactoring best practices")
→ Returns stored pattern with [[memory:12345]]
```

---

## 🔍 6. EXA SEARCH MCP (Web Intelligence)

### Tools:
- **`web_search_exa`** - Real-time web search
- **`get_code_context_exa`** - Search for code examples/docs (1K-50K tokens)

### Use Cases:
- Find latest library updates
- Search for code examples
- Troubleshoot with current web knowledge
- Research production patterns
- Find tutorials and blogs

### Example:
```
// Find React Server Components examples
get_code_context_exa(
  query="Next.js 15 Server Components streaming examples",
  tokensNum=10000
)
→ Returns curated code examples from web
```

---

## 📊 7. GOOGLE SHEETS MCP (Data Operations)

### Tools (30+):
- **Discovery:**
  - `search_spreadsheets`
  - `list_tables`
  - `get_table_schema`
  - `get_sheet_names`
  
- **Reading:**
  - `batch_get` (read ranges)
  - `get_spreadsheet_info`
  - `lookup_spreadsheet_row`
  - `query_table` (SQL SELECT)
  - `execute_sql` (full SQL support)
  
- **Writing:**
  - `batch_update` (write ranges)
  - `spreadsheets_values_append`
  - `create_google_sheet1`
  - `sheet_from_json`
  
- **Manipulation:**
  - `create_spreadsheet_row`
  - `create_spreadsheet_column`
  - `delete_dimension`
  - `insert_dimension`
  - `clear_values`
  - `aggregate_column_data`
  
- **Formatting:**
  - `format_cell`
  - `update_sheet_properties`
  - `update_spreadsheet_properties`
  
- **Visualization:**
  - `create_chart`
  
- **Advanced:**
  - `set_basic_filter`
  - `search_developer_metadata`

### Use Cases:
- Automated reporting
- Data analysis with SQL
- Dashboard creation
- Batch data operations
- Integration with other services

### Example:
```sql
-- Query Sheets like a database
execute_sql(
  spreadsheet_id="...",
  sql="SELECT product, SUM(sales) FROM Sales_Data WHERE region='HSR' GROUP BY product"
)
```

---

## 🚫 9. GMAIL MCP (Email Automation) - **DISABLED VIA UI TOGGLE**

### Status: ❌ **Not Active**
**Reason:** Android app has no email functionality. Disabled via Cursor Settings toggle.

### Tools (15+):
- `fetch_emails`, `send_email`, `create_email_draft`
- `add_label_to_email`, `create_label`, `list_labels`
- `get_attachment`, `get_contacts`, `get_profile`

### Re-enable if: You add email reporting features to app.

---

## 🌐 10. BROWSER MCP (Web Testing) - **NEVER CONFIGURED**

### Status: ❌ **Not Applicable**
**Reason:** This is a native Android app, not a web application. No browser testing needed.

### Would have provided (if configured):
- `browser_navigate`, `browser_snapshot`, `browser_click`
- `browser_console_messages`, `browser_take_screenshot`

### Use instead: Espresso for Android UI testing

---

## 🔥 11. FIRESTORE MCP (Database) - **ACTIVE**

### Tools (20+):
- **`firestore_list_collections`** - List available collections
- **`firestore_get_document`** - Read document
- **`firestore_query_collection`** - Query with filters  
- **`firestore_create_document`** - Create document
- **`firestore_update_document`** - Update document
- **`firestore_delete_document`** - Delete document
- **`firestore_batch_write`** - Atomic batch operations

### Use Cases:
- Verify alert delivery to Firestore ingest URL
- Query failed deliveries for debugging
- Test endpoint configuration

---

## 📊 12. GOOGLE SHEETS MCP (Data Operations) - **DISABLED VIA UI TOGGLE**

### Status: ❌ **Not Active**
**Reason:** App only sends data to Sheets via HTTP POST. Don't need read/manipulate during refactoring.

### Tools (30+):
- `execute_sql`, `batch_update`, `query_table`
- `create_google_sheet1`, `spreadsheets_values_append`
- `aggregate_column_data`, `create_chart`

### Re-enable if: Need to programmatically verify webhook delivery during integration tests.

---

## 🌟 13. GOOGLE SUPER MCP (Unified Google) - **DISABLED VIA UI TOGGLE**

### Status: ❌ **Not Active**  
**Reason:** Redundant with specialized MCPs. Still experimental.

### Would have provided: Unified Gmail + Sheets + Drive + Calendar access

### Keep disabled: Not needed for Android refactoring work.

---

## ⚡ TOOL SELECTION FLOWCHART

```
User Request
    ↓
Is it COMPLEX (>5 steps)?
    YES → Sequential Thinking MCP (plan first)
    NO → Continue
    ↓
Is it CODE-related?
    YES → Serena MCP (find_symbol, replace_content, etc.)
    NO → Continue
    ↓
Does it need LIBRARY DOCS?
    YES → Context7 MCP (auto-invoke)
    NO → Continue
    ↓
Does it need WEB RESEARCH?
    YES → Exa Search MCP (get_code_context_exa or web_search_exa)
    NO → Continue
    ↓
Does it need VERSION CONTROL?
    YES → GitHub MCP (push_files, create_pr, etc.)
    NO → Continue
    ↓
Does it need DATA OPERATIONS?
    YES → Google Sheets MCP (execute_sql, batch_update, etc.)
    NO → Continue
    ↓
Does it need EMAIL?
    YES → Gmail MCP (fetch_emails, create_draft, etc.)
    NO → Continue
    ↓
Does it need WEB TESTING?
    YES → Browser MCP (navigate, snapshot, click, etc.)
    NO → Continue
    ↓
Does it need DATABASE?
    YES → Firestore MCP (query_collection, write_document, etc.)
    NO → Continue
    ↓
Should it be REMEMBERED?
    YES → Mem0 Memory MCP (add_memory)
    NO → Done
```

---

## 🎯 OPTIMIZATION STRATEGIES

### 1. Parallel Execution
**Independent operations → Execute in parallel**
```
// ✅ GOOD: Parallel reads
read_file("file1.kt") + read_file("file2.kt") + read_file("file3.kt")

// ❌ BAD: Sequential reads
read_file("file1.kt") → wait → read_file("file2.kt") → wait → read_file("file3.kt")
```

### 2. Tool Hierarchy
**Use specialized tools before general ones**
```
✅ Serena search_for_pattern > grep
✅ Context7 get-library-docs > Exa web_search_exa
✅ Serena find_symbol > read_file (entire file)
```

### 3. Caching Strategy
**Store frequently used data in Memory MCP**
```
First time: Context7 → get docs → store in Memory
Next time: Memory search → instant retrieval
```

### 4. Batch Operations
**Combine multiple operations into single calls**
```
✅ GitHub push_files (10 files in 1 commit)
✅ Google Sheets batch_update (multiple ranges at once)
❌ 10 separate create_or_update_file calls
```

---

## 📈 USAGE STATISTICS (Typical Session)

| MCP Server | Avg Calls/Session | % of Total |
|------------|-------------------|------------|
| Serena | 20-50 | 40% |
| Context7 | 2-5 | 10% |
| Memory | 5-10 | 12% |
| GitHub | 3-8 | 8% |
| Sequential Thinking | 1-3 | 5% |
| Exa Search | 2-5 | 8% |
| Google Sheets | 0-10 | 7% |
| Gmail | 0-5 | 5% |
| Browser | 0-10 | 5% |
| Firestore | 0-5 | 3% |

---

## 🚀 FUTURE ENHANCEMENTS

### Planned MCP Servers:
- **AWS MCP** - S3, Lambda, DynamoDB operations
- **Terraform MCP** - Infrastructure as code
- **Docker MCP** - Container management
- **Kubernetes MCP** - Cluster operations
- **Linear MCP** - Project management
- **Slack MCP** - Team communication

### Potential Integrations:
- Sequential Thinking + Memory = Learning reasoning patterns
- Serena + GitHub = Automated refactoring PRs
- Browser + Sheets = Web scraping → Spreadsheet reports
- Gmail + Sheets = Email analytics dashboards

---

## 📚 QUICK REFERENCE

### Most Used Tool Combinations:

**1. Complex Refactoring**
```
Sequential Thinking → Serena find_symbol → Serena find_referencing_symbols → 
Serena replace_symbol_body → Memory add_memory
```

**2. Library Integration**
```
Context7 resolve-library-id → Context7 get-library-docs → 
Serena search_for_pattern → Serena replace_content
```

**3. Data Analysis**
```
Google Sheets search_spreadsheets → Google Sheets list_tables → 
Google Sheets execute_sql → Memory add_memory → Gmail create_email_draft
```

**4. Web Testing**
```
Browser navigate → Browser snapshot → Browser click → 
Browser console_messages → Memory add_memory
```

**5. Bug Investigation**
```
Sequential Thinking → Memory search_memories → Serena find_symbol → 
Context7 get-library-docs → Exa get_code_context_exa → Serena replace_content
```

---

**Total Tool Power:** 70+ specialized tools across 7 active MCP servers (10 configured)  
**Active Status:** 7 enabled, 3 disabled via Cursor UI toggle  
**Optimization Goal:** Right tool for right job, maximum parallelization, zero redundancy  
**Success Metric:** Fast, accurate, maintainable solutions with transparent reasoning  
**Note:** Serena MCP requires Cursor restart to become active



