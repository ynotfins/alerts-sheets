# BNN Notification Parsing Logic

## Overview

The parsing logic in `Parser.kt` is the core intelligence of the application. It transforms raw BNN notification text into structured data suitable for the Google Sheet schema.

## Core logic

The parser employs a robust, multi-stage pipeline designed to handle real-world variations in BNN formats, including multi-line inputs and varying delimiters.

### 1. Pre-Processing

- **Normalization**: Converts Windows/Mac line endings (`\r\n`) to Unix (`\n`).
- **Line Validation**: Filters out blank lines.
- **Delimiter Check**: **MANDATORY**. The parser searches for a line containing the pipe symbol `|`. If no pipe is found, parsing aborts (returns `null`), triggering the fallback mechanism.

### 2. Status Determination

The parser determines if the alert is a "New Incident" or an "Update".

- **Primary Check**: Looks at the first segment of the pipe-delimited line.
  - `U/D`, `Update` -> **Update**
  - `N/D`, `New` -> **New Incident**
- **Secondary (Multi-line) Check**: If the pipe line is not the first line (common in some Android notifications), it scans _previous lines_ for keywords like "Update" or "U/D".

### 3. State & Location Extraction

- **State**: Extracted from the first segment. Prefixes like "U/D", "N/D" are stripped.
- **City/County Logic**:
  - **New York Exception**: If State is "NY" and the next field is a Borough (Queens, Bronx, etc.), that field is treated as **City**, and County is left blank.
  - **Standard**: Field 1 = County, Field 2 = City.

### 4. Incident ID & Source

- **Pattern Matching**: Scans all segments _backwards_ for a 7-digit ID pattern (`1xxxxxx`).
- **Hash Removal**: As per user request, the leading `#` is stripped from the ID (e.g., `1234567`).
- **Fallback**: If no ID is found, a hash code of the full text is generated.
- **Source Tag**: Locates `<C> BNN` or `BNN` to establish the boundary between Incident Details and FD Codes.

### 5. Incident Details

- **Strict Positioning**: The Incident Details are strictly defined as the field **immediately preceding** the Source Tag (`<C> BNN`).
- **Heuristic Fallback**: If the Source Tag is missing (rare), it looks for the longest text field that isn't the ID.

### 6. Middle Fields (Address vs. Incident Type)

A "fuzzy" logic block distinguishes between Address and Incident Type in the fields between City and Details.

- **Address Indicators**:
  - Starts with a digit (e.g., "123 Main").
  - Contains Suffixes: "Ave", "St", "Rd", "Blvd", "Hwy", etc.
  - Contains Intersections: "&", " and ".
- **Type Indicators**:
  - Keywords: "Fire", "Alarm", "MVA", "Crash", "Rescue", "EMS", "Gas", "Smoke", "Police", "Medical", etc.
  - Short codes (e.g., "Box 22").
- **Logic**:
  - If matches Address indicators -> **Address**.
  - If matches Type indicators -> **Incident Type** (Hyphen-separated if multiple).
  - If ambiguous: length heuristics are used.

### 7. FD Code Extraction

- **Scope**: Scans all fields _after_ the Incident Details.
- **Filtering (Exclusion List)**:
  - Ignores: `BNN`, `BNNDESK`, `DESK`, empty strings, pipe `|`.
  - Ignores: The Incident ID itself.
  - Ignores: Tokens longer than 20 characters.
- **Output**: Returns a distinct list of valid FD codes (e.g., `E-1`, `L-1`, `nj312`).

## Error Handling

The entire process is wrapped in a `try-catch` block.

- **Critical Errors**: Logged via `Log.e`.
- **Return Value**: Returns `null` on failure, allowing `NotificationService` to fall back to the Generic Template.
