# BNN Notification Parsing Logic (v2.1)

## Overview

This document serves as the definitive guide to the BNN parsing logic implemented in `Parser.kt`. The parser transforms raw notification text into structured data for Google Sheets.

## Core Pipeline

### 1. Pre-Processing & Validation

- **Delimiter**: The parser **must** find a pipe `|` delimiter. If missing, it returns `null`.
- **Normalization**: Converts line endings to Unix style (`\n`) and removes blank lines.

### 2. Status Determination

- **Primary Check**: First segment of the pipe line.
  - `U/D`, `Update` -> **Update**
  - `N/D`, `New` -> **New Incident**
- **Multi-line Check**: If the pipe line isn't the first line, it scans previous lines for "Update" or "U/D".

### 3. State & Location (NYC Loophole Fixed)

- **State**: Extracted from the first segment (e.g., "NJ", "NY"). Prefixes are stripped.
- **City/County Logic**:
  - **Standard**: Field 1 = County, Field 2 = City.
  - **NYC Exception**: If State is "NY" and Field 1 is a Borough (Queens, Brooklyn, Bronx, Manhattan, Staten Island):
    - **County** = <Borough Name> (Fix applied: no longer empty)
    - **City** = <Borough Name>
    - Indexing adjusts to skip only one field.

### 4. Incident ID (Hash Strictness)

- **Pattern**: format `#1xxxxxx`.
- **Hash Rule**: The ID **MUST** start with `#` to ensure Apps Script can match it against existing rows in the sheet.
  - If the parser extracts "1234567", it automatically prepends `#` -> `#1234567`.
- **Fallback**: If no ID is found, a hash code is generated and `#` is prepended.

### 5. Incident Details

- **Location**: The field distinctively followed by the Source Tag (`<C> BNN` or `BNN`).
- **Heuristic**: If the tag is missing, the longest field (that isn't an ID) is selected.

### 6. Middle Fields (Address vs Type)

A "fuzzy" logic block distinguishes between Address and Incident Type in the fields between City and Details.

- **Address**: Detected by digit start, suffix (Ave, St, Rd), or intersection markers.
- **Type**: Detected by keywords (Fire, Alarm, Rescue, Police, etc.) or short codes.
- **Result**: Fields are concatenated into `address` or `incidentType` respectively.

### 7. FD Code Extraction (Strict Filtering)

- **Scope**: All fields after the Incident Details/Source Tag.
- **Exclusion List (Blacklist)**:
  - `BNNDESK` (substring match)
  - `DESK` (substring match)
  - `BNN`
  - `<C>`
  - Pipe `|`
  - The Incident ID itself
  - Pure numeric strings (e.g., "12") if not part of a code.
- **Output**: A clean, distinct list of codes (e.g., `nyc337`, `E-23`).

## Google Sheet Behavior

- **New Incident**: Creates a new row.
- **Update**: Because the ID matches (thanks to the `#`), Apps Script finds the existing row and **appends** the new status, timestamp, and details to the same cells (new lines), instead of creating a duplicate row.
- **FD Codes**: New codes are added to Columns K+.
