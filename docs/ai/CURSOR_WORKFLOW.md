## Cursor Workflow (AlertsToSheets)

**Purpose:** This is the **canonical, repo-local** workflow for working in Cursor on this project: how we manage MCP servers, how we test, and how we avoid leaking secrets.

---

## MCP Configuration (Global)

- **Global MCP config file**: `C:\Users\ynotf\.cursor\mcp.json`
- **Global credential vault (do not commit)**: `C:\Users\ynotf\Dropbox\.mcp\global-credentials.yaml`

### Rules (Non‑Negotiable)

- **Never paste secrets** (API keys, tokens, service account JSON, etc.) into:
  - chat
  - git-tracked files
  - issues/PRs
- In repo docs/config examples, use **placeholders** only (e.g., `SMITHERY_API_KEY_HERE`).
- Treat any secret that appears in chat history as **compromised** → rotate/revoke.

### MCP Server Types

- **HTTP MCP servers** (hosted): typically require a URL ending in `/mcp` and **often** require:
  - `?api_key=SMITHERY_API_KEY_HERE` query param
  - `Authorization: <PROVIDER_TOKEN_HERE>` header (provider-specific)
- **Local/stdio MCP servers**: run locally and usually do **not** require Smithery keys.

### Restart Requirement

After editing `C:\Users\ynotf\.cursor\mcp.json`:
- **Restart Cursor** to reload MCP servers.

---

## Installing MCP Servers (Smithery CLI)

### Install (adds server for Cursor)

Run in PowerShell (any folder is fine):

```powershell
npx -y @smithery/cli@latest install <package-name> --client cursor
```

Examples:

```powershell
# Playwright MCP (browser automation)
npx -y @smithery/cli@latest install @microsoft/playwright-mcp --client cursor

# Firecrawl MCP (web extraction) - if used
npx -y @smithery/cli@latest install @mendableai/mcp-server-firecrawl --client cursor
```

### If Smithery prompts for an API key

- Put keys in `C:\Users\ynotf\Dropbox\.mcp\global-credentials.yaml`
- In `mcp.json`, use placeholders in examples and paste real secrets **only locally**.

---

## Playwright MCP (When to Use)

Use Playwright MCP when it provides leverage over manual browser work, for example:
- verifying a web endpoint returns expected JSON
- regression-checking a dashboard or hosted tool UI
- extracting structured data from web pages for debugging

Typical workflow:

1. `browser_navigate` to the page
2. `browser_snapshot` to get stable element references
3. `browser_click` / `browser_type` interactions
4. `browser_console_messages` to check errors
5. `browser_take_screenshot` for visual evidence (optional)

---

## MagicMCP (When to Use)

MagicMCP is useful when we’re building **React UI components** (e.g., `/ui` work):
- generate a component scaffold/snippet quickly
- refine an existing component’s layout/styling
- fetch logos (`/logo ...`) when assets aren’t in the repo

For AlertsToSheets (Android-first), MagicMCP is usually **not** needed unless we add a web dashboard.

---

## Repo Safety Checklist (Before Restart / Before Push)

- `git status` is clean (or changes are intentional)
- No secrets were added to tracked files
- If we changed docs/config in-repo, update `docs/ai/STATE.md` per project rules
- Push branch: `fix/wiring-sources-endpoints`

