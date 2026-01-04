# parsing.md — Alert Payload Contracts & Sheet Parsing Rules

This repo ingests alerts from two sources with **different semantics** and **different parsing rules**:

- **BNN App alerts** (dominant volume). Pipe-delimited fields and stable incident IDs with update semantics.
- **SMS AdjustLeads fire alerts** (emoji + line-delimited). Stable incident IDs are extracted from the AdjustLeads URL.

This document is the **single source of truth** for:

1. The **JSON payload contract** sent from Android → Apps Script.
2. The **Apps Script parsing & merge** logic required to populate the Sheet.
3. The **Sheet schema** and invariants (what changes vs. what never changes).

---

## 0) Sheet Schema (Fixed)

The target Google Sheet has fixed headers (columns are 1-indexed):

| Col | Name | Meaning |
|---:|---|---|
| A | New/Update | Multi-line status history. New incidents create the first line; updates append a new line. |
| B | Timestamp | Multi-line timestamp history aligned with status. |
| C | Incident ID | **Primary key** for upsert (row merge). BNN uses `1825784` (**digits only, no `#`**). AdjustLeads uses `AL-294966`. |
| D | State | Stable for BNN incidents. For SMS, parsed from address line if available. **Must not change on updates.** |
| E | County | Stable for BNN incidents. For SMS, parsed from header line if available. **Must not change on updates.** |
| F | City | Stable for BNN incidents. For SMS, parsed from address line if available. **Must not change on updates.** |
| G | Address | Stable for BNN incidents. For SMS, parsed from address line if available. **Must not change on updates.** |
| H | Incident type | Multi-line incident type history. Append new type lines only when different. |
| I | Incident | Multi-line incident details history. Append on updates. |
| J | nfa-id | **Monotonic per-row ID**. Starts at `1000000`. Assigned once on **New incident only**, never changes on updates. |
| K | Original Full Notification | Multi-line raw payload history. For every event, append the full original message with a `From:` prefix where relevant. **No parsing** — store the raw full text. |
| L+ | FD Codes (repeated columns) | Each FD code occupies its own cell across the FD Codes region. Dedup per-incident. |

### Invariants

- **New incident → new row**.
- **Update → same row** (matched by **Column C**), append new lines in A/B/H/I/K.
- **Columns D/E/F/G never change on updates** (state/county/city/address).
- **FD Codes:** per-incident unique set, filtered and deduped (see BNN rules).
- **nfa-id**: assigned once per row on creation (only when inserting a previously unseen Incident ID); updates must reuse the existing nfa-id for that row.

---

## 1) Common Transport Contract (Android → Apps Script)

### Endpoint
Android delivers JSON to the Apps Script Web App URL (`/exec`) via HTTPS POST.

### Headers
- `Content-Type: application/json`

### JSON Requirements
- Must be valid JSON.
- Any embedded message text **may contain emojis and punctuation**; it must be JSON-escaped by the sender.
- Apps Script must treat message bodies as **untrusted input** and parse defensively.

### Minimal Top-Level Routing Fields
Apps Script should route by `source`.

- `source: "bnn"` (BNN App) **or** `source: "sms"` (SMS AdjustLeads).
- All other fields are source-specific.

---

## 2) BNN App Alerts

BNN alerts are delivered as **structured fields** (preferred), or as a raw pipe-delimited message where Apps Script extracts fields.

### 2.1) Preferred JSON Payload (BNN)

```json
{
  "source": "bnn",
  "status": "New Incident" | "Update" | "New Incident Update" | "U/D",
  "timestamp": "MM/dd/yyyy hh:mm:ss a",
  "incidentId": "#1825784",
  "state": "NJ",
  "county": "Atlantic",
  "city": "Atlantic City",
  "address": "31 Virginia Ave",
  "incidentType": "Fire Department Activity",
  "incidentDetails": "FD O/S with a discharged fire extinguisher...",
  "fdCodes": ["nj79", "njvx6"],
  "originalBody": "<full original BNN notification text>"
}
```

#### Field Notes
- `incidentId` is the **merge key**. It is **7 digits** (no `#` stored in the Sheet), currently starts with `1`, and will start with `2` in the future.
- `fdCodes` are lowercase tokens, often prefixed with state (`nj`, `ny`, `pa`).
- `originalBody` is stored in Column K (appended per update).

### 2.2) Raw BNN Message Format (if using `originalBody` parsing)

BNN bodies typically look like:

```
NJ | Atlantic | Atlantic City | 31 Virginia Ave | Fire Department Activity | ... | <C> BNN | BNNDESK/nj79/njvx6 | #1825784
```

**Rules:**
- `|` pipes separate fields.
- The incident ID appears at the end, prefixed with `#`.
- FD codes appear near the end, often inside the `<C>` segment.

#### NYC borough variant (BNN)
Some BNN rows omit a distinct City field and look like:

```
NY | Manhattan | Water Main Break | Pearl St & Water St | DEP on the scene | <C> BNN | BNNDESK | #1848577
```

For these:
- Sometimes the borough is in the **County** token, sometimes in the **City** token, and sometimes City is blank.
- If **any** token matches one of `{Manhattan, Brooklyn, Queens, Bronx, Staten Island}`, treat that token as **City**.
- Never infer `County = New York`; leave **County blank** when ambiguous (don’t guess).

### 2.3) BNN Parsing Rules (Apps Script)

**Incident ID extraction (required):**
- Extract the **last** `#` + 7 digits (future-proof for 1→2): `/#([12]\d{6})/` and take the last match.
- Store **digits only** (e.g., `1825784`) in Column C.

**BNN Incident ID invariants (do not relax):**
- BNN Incident ID must be derived only from the trailing `#([12]\d{6})` (**last match**).
- Apps Script must never accept or generate `APP-*` IDs for BNN incidents.
- If a row shows `APP-<13digit>` in Column C for what should be BNN: treat it as an Android routing/source-identity failure (e.g., event routed as generic app), not a Sheet schema issue.
- `APP-*` IDs may exist only for generic non-BNN app notifications (Apps Script fallback); they must not appear for BNN.

**New vs Update:**
- If `status` contains `Update`, `U/D`, or starts with `U/` → treat as Update.
- Otherwise treat as New Incident.

**Row merge:**
- Search Column C for exact match of `incidentId`.
- If not found → append a new row.
- If found → update that row by **appending** to multi-line fields.

**Multi-line append behavior (Update):**
- Column A: append `\n` + normalized status.
- Column B: append `\n` + timestamp.
- Column H: append `\n` + incident type **only if different/new**.
- Column I: append `\n` + incident details.
- Column K: append `\n` + original body (raw full text).

**Address fields immutability:**
- Columns D/E/F/G are written on New Incident only.
- On Update: **do not modify** D/E/F/G.

### 2.4) FD Codes Rules (BNN)

**Extraction sources:**
- Prefer `fdCodes` array from JSON.
- Otherwise parse from the `<C>` / tail segment where tokens are separated by `/` or `|`.

**Position variability (BNN):**
- FD codes may appear after `BNNDESK`, after `BNN`, alone (no `BNNDESK`), delimited by `/`, `|`, or spaces, or not at all (~20%).
- Never assume `BNNDESK` is present or that `fdCodes` exist.

**Normalization:**
- Lowercase.
- Trim.

**Filtering:** remove all occurrences of:
- `bnn`, `bnndesk`, `desk`, `bnn desk`, `bnn|bnndesk` artifacts.

**Dedup per incident:**
- Maintain a set of existing codes already present in that row’s FD Codes columns.
- Only write codes not already stored.

**Storage:**
- One FD code per cell across columns L+.

---

## 3) SMS AdjustLeads Alerts

These alerts arrive via Android SMS ingestion and use a **line-delimited emoji format**.

### 3.1) JSON Payload (SMS)

```json
{
  "source": "sms",
  "sender": "+15614193784",
  "message": "🔥 New Fire Alert in Bergen\n\n📍 2100 North Central Road, Fort Lee, NJ 07024-7558, USA\n🗺️ https://maps.google.com/?q=...\n📋 Structural Fire - First engine on scene...\nℹ️ https://www.adjustleads.com/app/alerts/294966",
  "time": "12/30/2025 12:30 PM",
  "timestamp": "12/30/2025 12:30:00 PM"
}
```

#### Field Notes
- `sender` may be missing for some app-notification sources; for SMS it should be a phone number string.
- `message` can contain emojis and must be JSON-escaped.
- `timestamp` is used for Sheet column B append.

### 3.2) SMS Parsing Target Fields
From the message we want:

- **Incident ID** (stable): derived from AdjustLeads URL.
- **County**: from the fire alert header line.
- **Address / City / State**: from the 📍 line.
- **Incident type / details**: from the 📋 line.
- **Original Full Notification**: full message appended into Column K.

### 3.3) SMS Incident ID Rules (critical)

**Primary ID source (required):**
- The AdjustLeads URL is always last and contains the ID at the end.
- Supported URL shapes:
  - `https://www.adjustleads.com/app/alerts/294966`
  - `https://adjustleads.com/app/alerts/294966`
  - `https://adjustleads.net/alerts/294966`

**Extraction:**
- Trim whitespace before extracting trailing digits (defensive; prevents future regex tightening bugs).
- Regex: `/https?:\/\/(?:www\.)?adjustleads\.(?:com|net)\/(?:app\/)?alerts\/(\d{6,})/i`
- Store as: `AL-294966` in Column C.

**Fallback (temporary, “works for a while”):**
- If URL not present, extract the **last 6-digit number** in the message:
  - Regex: `/\b(\d{6})\b(?!.*\d{6})/`
  - Store as `AL-<digits>`.

**Last resort (stable hash):**
- Compute MD5 of `(sender + first 50 chars of message)` and store as `AL-<shortHash>`.
- This prevents infinite new rows for the same repeating content.

### 3.4) SMS New vs Update
AdjustLeads SMS can include updates (format varies). For now:

- **If the parsed incident ID matches an existing row** (Column C), treat as **Update**.
- Otherwise, treat as **New Incident**.

### 3.5) SMS Field Extraction Rules

**County (🔥 line):**
- Example: `🔥 New Fire Alert in Morris County`
- Extract `Morris` from `in {County} County`.
- Regex: `/(?:in|at)\s+(.+?)\s+County/i`

**Address / City / State (📍 line):**
- Example: `📍 31 Grand Avenue, Cedar Knolls, NJ 07927-1506, USA`
- Strip the `📍` prefix.
- Split by commas.
  - `parts[0]` = street address
  - `parts[1]` = city (may include township wording)
  - `parts[2]` starts with state abbreviation; take first 2 chars as state.

**Incident type and details (📋 line):**
- Example: `📋 Residential Fire - Fire alarm activated at a residential location.`
- Strip the `📋` prefix.
- Split on ` - `.
  - left side → Column H
  - right side → Column I (details)
- If no delimiter exists, use `Fire Alert` as type and set details to the full line.

**Original (Column K):**
- Always append:
  - `From: <sender>\n<full message>`

### 3.6) SMS Update Append Rules
If row exists for the same `AL-xxxxxx`:

- Column A: append `\nSMS Update`
- Column B: append `\n<timestamp>`
- Column H: append the new type only if not already present
- Column I: append `\n<incidentDetails>` (or a safe fallback)
- Column K: append `\n\nFrom: <sender>\n<message>`

**Do not modify Columns D/E/F/G** on updates.

### 3.7) Emoji / JSON-breaking characters

Android must JSON-escape properly; Apps Script must not assume ASCII.

- Emojis are valid UTF‑16 in Apps Script.
- Newlines must arrive as `\n` inside JSON.
- If message sanitization is needed (to protect Sheets formulas), escape:
  - leading `=` `+` `-` `@` at the start of a cell value by prefixing with `'` (single quote).

---

## 4) Source Routing and Expected Apps Script Response

Apps Script should return JSON for observability.

### 4.1) BNN Response

```json
{ "result": "success", "type": "bnn", "incidentId": "1825784", "action": "new" | "update" }
```

### 4.2) SMS Response

```json
{ "result": "success", "type": "sms", "incidentId": "AL-294966", "action": "new" | "update", "parsed": true }
```

- `parsed: true` means we extracted at least the stable ID and at least one of {county, address, incident type}.
- If not recognized as AdjustLeads / fire alert, return `parsed: false` and treat as generic SMS.

---

## 5) Examples

### 5.1) BNN New Incident Example

Input (`originalBody` style):

```
New Alert BNN
09/18/2025 11:29 AM
NJ | Atlantic | Atlantic City | 31 Virginia Ave | Fire Department Activity | FD O/S ... | <C> BNN | BNNDESK | #1825784
```

Output Row:
- A: `New Incident`
- B: timestamp
- C: `1825784`
- D/E/F/G populated
- H/I populated
- K contains full original
- FD codes filled and deduped

### 5.2) SMS AdjustLeads Example

Input (`message`):

```
🔥 New Fire Alert in Bergen

📍 2100 North Central Road, Fort Lee, NJ 07024-7558, USA
🗺️ https://maps.google.com/?q=...
📋 Structural Fire - First engine on scene...
ℹ️ https://www.adjustleads.com/app/alerts/294966
```

Parsed:
- C: `AL-294966`
- E: `Bergen`
- D/F/G from 📍 line
- H: `Structural Fire`
- I: `First engine on scene...`
- K: `From: +1561...\n<full message>`

---

## 6) Geocoding Readiness (Next Step)

To support geocoding later without breaking current logic:

- Ensure SMS parsing captures either:
  - `address + city + state`, **or**
  - the `mapsUrl`.
- The geocoding step must:
  - run **after** the row is created/updated
  - write results into **new columns only** (never overwrite D/E/F/G on updates)
  - be idempotent (re-runs do not create drift)

---

## 7) Non-negotiable Safety Checks

- **BNN handler correctness is sacred.** SMS work must not regress BNN merge + FD code dedupe.
- **Row merge is always by Column C.**
- **Updates never rewrite D/E/F/G.**
- **FD codes are unique per incident.**

---

## 8) Not a parsing.md problem

- `APP-<timestamp>` IDs, duplicate dashboard cards, or wrong IDs only for APP events are **Android delivery/source identity/persistence issues**, not Apps Script parsing issues.
- `parsing.md` should not be used to justify adding Apps Script fallbacks that **mask Android bugs**.

