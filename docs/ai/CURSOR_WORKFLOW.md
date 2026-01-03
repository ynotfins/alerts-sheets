## Cursor Workflow (AlertsToSheets)

This repo is **Android (Kotlin) + Firebase Functions (TypeScript)**. Keep work modular (**ui / domain / data / utils**) and do not introduce secrets into git.

### Tool Strategy (default order)
- **Complex / multi-step work**: use **Sequential Thinking** first (scope, risks, checkpoints).
- **Code navigation / edits**: use **Serena** first (`find_symbol`, `find_referencing_symbols`, `replace_*`).
- **Library docs**: use **Context7** (Android/Kotlin/Firebase/etc.).
- **Web research**: use **Exa** only when Context7 isn’t enough.
- **Commits/PRs**: use **GitHub MCP** only for remote file ops (local git via CLI).
- **Long-term preferences**: **Memory MCP** (cite memories when used).

### MagicMCP (UI Components / Logos)
- **What it’s for**: generating/refining **React UI components** (21st.dev) and fetching **logos**.
- **When it’s allowed**:
  - Only when the task is explicitly about **web UI components** (or the user asks for `/ui`, `/21`, `/logo`).
  - Not a substitute for Android XML/Kotlin UI work.
- **Fallback if tool fails**:
  - Say it failed explicitly, then implement the UI manually in the codebase using existing project patterns.

### Playwright MCP (Web Automation / Testing)
- **What it’s for**: automated **web** navigation, interaction, screenshots, and console/network checks.
- **When it’s allowed**:
  - Testing **web pages** (docs, admin consoles, dashboards) and validating web flows after changes.
  - **Not** for driving native Android UI (use ADB + on-device testing).
- **Fallback if tool fails**:
  - Say it failed explicitly, then use the built-in Browser MCP tools (if available) or manual steps + CLI verification (curl/adb/logcat).

### Tool Failure Policy (never silent)
If a required tool is degraded/unreachable:
- **Announce FAIL/WARN** and what is impacted.
- **Switch to an approved fallback** (e.g., Serena indexing → pattern search + targeted reads/edits; web testing → Browser MCP/manual).
- **Provide exact restore steps** (restart Cursor/MCP server, fix config, reindex, etc.).

