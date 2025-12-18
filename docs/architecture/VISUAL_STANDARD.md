# Visual Standard: Google Sheet Header Formatting

**Date:** December 17, 2025  
**Status:** Active Convention  
**Priority:** P0 - Source of Truth

---

## 🎨 **The Rule**

### **Underlined Header Text = Static Field**

In the Google Sheet, look at the header row (Row 1):

```
If header text is UNDERLINED:
  → Field is STATIC
  → Write ONCE on new incident
  → NEVER append on updates
  → Identifies the incident

If header text is REGULAR (not underlined):
  → Field is DYNAMIC
  → Append with \n on every update
  → Captures incident evolution
```

---

## 📊 **Current Sheet Configuration**

### **UNDERLINED Headers (Static)**
- Column C: Incident ID
- Column D: State
- Column E: County
- Column F: City
- Column G: Address

**Apps Script Behavior:** Write once, never modify on updates

---

### **REGULAR Headers (Dynamic)**
- Column A: Status
- Column B: Timestamp
- Column H: Incident Type
- Column I: Incident Details
- Column J: Original Full Notification

**Apps Script Behavior:** Append with `\n` separator on every update

---

### **Special: FD Codes (K-U)**
- Columns K-U: FD Codes (one per cell)

**Apps Script Behavior:** Only add NEW codes, skip duplicates for same incident ID

---

## 💡 **Why This Convention?**

**Visual clarity:** Anyone looking at the sheet can instantly see which fields are static vs dynamic

**No ambiguity:** Header formatting is the single source of truth

**Easy to modify:** To make a field static, just underline its header. To make it dynamic, remove the underline.

**Self-documenting:** The sheet itself documents its update behavior

---

## 🔧 **For Developers**

### **Apps Script Implementation**

```javascript
// Pseudo-code for determining if column should append:

function shouldAppendOnUpdate(columnLetter) {
  const headerCell = sheet.getRange(`${columnLetter}1`);
  const isUnderlined = headerCell.getFontLine() === 'underline';
  
  if (isUnderlined) {
    // Static field - do NOT append
    return false;
  } else {
    // Dynamic field - append with \n
    return true;
  }
}

// Exception: FD Codes (K-U) use special merge logic
```

### **Alternative: Hardcode Column List**

Since sheet structure is stable, can hardcode:

```javascript
const STATIC_COLUMNS = ['C', 'D', 'E', 'F', 'G']; // Underlined headers
const DYNAMIC_COLUMNS = ['A', 'B', 'H', 'I', 'J']; // Regular headers
const FD_CODE_COLUMNS = ['K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U'];
```

---

## 📋 **Verification Checklist**

When implementing update logic, verify:

- [ ] Static columns (underlined headers) are NOT modified on updates
- [ ] Dynamic columns (regular headers) append with `\n` on updates
- [ ] FD Codes only add new codes (no duplicates per incident)
- [ ] Row 22 behavior is the standard (multiple updates on same row)

---

## 🎯 **Sheet URL**

https://docs.google.com/spreadsheets/d/1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE/edit?gid=0#gid=0

**Check Row 1:** Columns C-G should have underlined header text

---

**This visual standard is now the authoritative way to determine field update behavior.** 🎨


