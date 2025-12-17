# FD Codes Analysis

## Definition

FD Codes are specialized identifiers included in BNN notifications that indicate specific fire department units, radio frequencies, or status codes (e.g., `E-1` for Engine 1, `L-4` for Ladder 4, `nj312` for a regional dispatch code).

## Importance

These codes are **High Priority** for analytics. They allow tracking of specific unit activity and regional responses.

## Extraction Logic

1.  **Location**: Codes are found in the segments _following_ the Incident Details and the Source Tag (`<C> BNN`).
2.  **Delimiters**: Codes can be separated by pipes `|`, slashes `/`, or spaces.
3.  **Deduplication**: The system ensures only **distinct** codes are stored for a single notification to prevent "E-1, E-1, E-1" spam.

## Exclusion Rules

To ensure data quality, the following tokens are strictly **IGNORED**:

- `BNN`
- `BNNDESK`
- `DESK` (Explicitly removed)
- The **Incident ID** (e.g., `1854839`), which often repeats in the code section.
- The Pipe Symbol `|`.
- Any token > 20 characters (treated as junk/text).

## Storage in Google Sheets

- **Columns**: K through Z (Columns 11+).
- **Behavior**:
  - **New Incident**: Codes are appended starting at Column K.
  - **Update**: New codes are **Merged** with existing codes. The script reads existing codes in the row, adds the new ones, removes duplicates, and re-writes the line.
  - **Limit**: Codes expand horizontally. 1 Code = 1 Cell.
