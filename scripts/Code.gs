function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(30000); // Wait for up to 30 seconds for concurrent interactions

    const sheetId = "1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE";
    const sheet = SpreadsheetApp.openById(sheetId).getSheets()[0];

    // Parse Incoming Data
    const data = JSON.parse(e.postData.contents);

    // 0. Verification Ping (App Health Check)
    if (data.type === "verify") {
      return ContentService.createTextOutput(
        JSON.stringify({ result: "verified", debug: getSheetDebugInfo_(sheet) })
      ).setMimeType(ContentService.MimeType.JSON);
    }

    // 1. SMS Message Handling
    if (data.source === "sms") {
      return handleSmsMessage(data, sheet);
    }

    // 2. Generic App Notification Handling (no BNN structure)
    if (data.source === "app" && !data.incidentId) {
      return handleGenericApp(data, sheet);
    }

    // Guard: unknown sources without an incidentId should never fall into the BNN flow.
    // This prevents Lab "test"/"dirty test" payloads (or typos) from creating blank-ID rows.
    // Contract sources are: "sms" and "bnn" (and "app" for generic notifications).
    if (!data.incidentId && data.source !== "bnn") {
      return ContentService.createTextOutput(
        JSON.stringify({ result: "error", error: `Unknown source without incidentId: ${data.source || ""}` })
      ).setMimeType(ContentService.MimeType.JSON);
    }

    // 3. BNN Incident Handling (structured data with incident ID)
    // Key Fields
    const rawId = data.incidentId ? data.incidentId.toString().trim() : "";
    // Store BNN incident IDs as digits only in the Sheet (no leading '#')
    const incidentId = rawId.startsWith("#") ? rawId.substring(1) : rawId;
    const displayId = incidentId;
    
    const incomingCodes =
      data.fdCodes && Array.isArray(data.fdCodes) ? data.fdCodes : [];

    // Timestamp: always normalize to Script TZ display format, supporting multiple payload variants.
    const formattedTime = formatScriptTimestampFromPayload(data.timestamp || data.time || new Date());

    // Generate the "Incident" text block prefix
    // User requested: "space bullet space" then update message.
    // Initial/Update Format: [Date Time] Message
    const incomingDetail = data.incidentDetails || "";
    // Clean details to remove BNN artifacts if they slipped through
    const cleanDetail = incomingDetail
      .replace(/<C> BNN/gi, "")
      .replace(/BNNDESK.*$/gi, "")
      .trim();

    // For new incidents or prepending timestamp
    const newIncidentText = `[${formattedTime}] ${cleanDetail}`;

      // Search for existing row with this Incident ID (Column C, Index 2)
    const lastRow = sheet.getLastRow();
    let foundRow = -1;

    if (incidentId !== "" && lastRow > 1) {
      // Get all IDs from Column C (Row 2 to Last)
      // optimization: get only column C
      const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();

      for (let i = 0; i < idValues.length; i++) {
        const sheetId = idValues[i][0].toString().trim();
        const normalizedSheetId = sheetId.startsWith("#") ? sheetId.substring(1) : sheetId;
        if (normalizedSheetId === incidentId) {
          foundRow = i + 2; // +2 because index 0 is row 2
          break;
        }
      }
    }

    let writeDebug = {
      action: "unknown",
      rowIndex: null,
      lastRowBefore: null,
      lastRowAfter: null,
      wroteRow: null
    };

    if (foundRow !== -1) {
      // --- UPDATE EXISTING ROW (APPEND MODE) ---
      writeDebug.action = "update";
      writeDebug.rowIndex = foundRow;
      writeDebug.lastRowBefore = sheet.getLastRow();
      writeDebug.lastRowAfter = writeDebug.lastRowBefore;
      writeDebug.wroteRow = false;

      // Normalize stored Incident ID in Column C to digits-only (remove leading '#')
      // This is safe because lookup already tolerates existing '#'.
      try {
        const idCell = sheet.getRange(foundRow, 3);
        const existingIdRaw = (idCell.getValue() || "").toString().trim();
        if (existingIdRaw.startsWith("#")) {
          idCell.setValue(existingIdRaw.substring(1));
        }
      } catch (_) {}

      // 1. Status (Col 1/A): Append "Update" on next line
      const statusCell = sheet.getRange(foundRow, 1);
      const currentStatus = statusCell.getValue();
      const newStatus = data.status || "Update";
      statusCell.setValue(currentStatus + "\n" + newStatus);

      // 2. Timestamp (Col 2/B): Append New Timestamp on next line
      const timeCell = sheet.getRange(foundRow, 2);
      const currentTime = timeCell.getValue();
      timeCell.setValue(currentTime + "\n" + formattedTime);

      // 3. Incident Type (Col 8/H): Append only if different or just consistent
      const typeCell = sheet.getRange(foundRow, 8);
      const currentType = typeCell.getValue();
      const newType = data.incidentType || "";
      if (newType) {
        typeCell.setValue(currentType + "\n" + newType);
      }

      // 4. Incident Details (Col 9/I): Append details on next line
      const incidentCell = sheet.getRange(foundRow, 9);
      const currentText = incidentCell.getValue();
      // Only append the details content, timestamp is already in Col B
      incidentCell.setValue(currentText + "\n" + cleanDetail);

      // 5. Original Body (Col 11/K): Append full notification text (raw)
      const originalCell = sheet.getRange(foundRow, 11);
      const currentOriginal = originalCell.getValue();
      const newOriginal = data.originalBody || "";
      if (newOriginal) {
        originalCell.setValue(currentOriginal + "\n" + newOriginal);
      }

      // 6. FD Codes Logic (Col 12+/L+): Merge Unique ONLY
      const maxCols = sheet.getLastColumn();
      let existingCodeValues = [];
      if (maxCols >= 12) {
        const codeRange = sheet.getRange(foundRow, 12, 1, maxCols - 11);
        existingCodeValues = codeRange.getValues()[0].filter((c) => c !== "");
      }

      let allCodes = new Set(existingCodeValues.map(String));

      incomingCodes.forEach((c) => {
        // Only add if it's NOT just the ID again (sometimes parser duplication)
        if (c !== incidentId) {
          allCodes.add(c);
        }
      });

      const uniqueCodes = Array.from(allCodes).filter(
        (c) =>
          c !== "" &&
          c.toLowerCase() !== "bnn" &&
          c.toLowerCase() !== "bnndesk" &&
          !c.toLowerCase().includes("bnn")
      );

      // Clear & Write (Same as before, horizontal expansion ok for codes)
      if (maxCols >= 12) {
        sheet.getRange(foundRow, 12, 1, maxCols - 11).clearContent();
      }
      if (uniqueCodes.length > 0) {
        sheet
          .getRange(foundRow, 12, 1, uniqueCodes.length)
          .setValues([uniqueCodes]);
      }
    } else {
      // --- CREATE NEW ROW ---

      // row array:
      // 0: Status
      // 1: Timestamp (Formatted)
      // 2: Incident ID
      // 3: State
      // 4: County
      // 5: City
      // 6: Address
      // 7: Incident Type
      // 8: Incident Details (Formatted with Date)
      // 9: Original Body

      const row = [
        data.status || "New Incident",
        formattedTime,
        displayId, // Digits only

        data.state || "",
        data.county || "",
        data.city || "",
        data.address || "",
        data.incidentType || "",
        newIncidentText,
        getOrAssignNfaIdForNewRow(sheet),
        data.originalBody || "",
      ];

      // Add FD Codes to end of array
      incomingCodes.forEach((code) => {
        if (code.toLowerCase() !== "bnn" && code.toLowerCase() !== "bnndesk") {
          row.push(code);
        }
      });

      writeDebug.action = "new";
      writeDebug.lastRowBefore = sheet.getLastRow();
      sheet.appendRow(row);
      writeDebug.lastRowAfter = sheet.getLastRow();
      writeDebug.wroteRow = writeDebug.lastRowAfter === writeDebug.lastRowBefore + 1;
      writeDebug.rowIndex = writeDebug.lastRowAfter;
    }

    return ContentService.createTextOutput(
      JSON.stringify({ result: "success", id: incidentId, debug: Object.assign(getSheetDebugInfo_(sheet), writeDebug) })
    ).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(
      JSON.stringify({ result: "error", error: err.toString() })
    ).setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
  }
}

function getSheetDebugInfo_(sheet) {
  try {
    const ss = sheet.getParent();
    const sheets = ss.getSheets();
    return {
      spreadsheetId: ss.getId(),
      spreadsheetUrl: ss.getUrl(),
      sheetName: sheet.getName(),
      sheetIndex: sheets.indexOf(sheet),
      lastRow: sheet.getLastRow(),
      lastColumn: sheet.getLastColumn()
    };
  } catch (e) {
    return { error: String(e) };
  }
}

/**
 * Handle SMS Messages
 * Parse AdjustLeads alerts with stable incident IDs and upsert support
 */
function handleSmsMessage(data, sheet) {
  const sender = (data.sender || "Unknown").toString();
  const message = (data.message || data.body || "").toString();

  // Always normalize timestamps in Script TZ to a single display format.
  const timestamp = formatScriptTimestampFromPayload(data.timestamp || data.time || new Date());

  const parsed = parseAdjustLeadsSms(message, sender);

  // Generic SMS (non-AdjustLeads) - backward compatible append-only
  if (!parsed.isAdjustLeads) {
    const lastRowBefore = sheet.getLastRow();
    const row = [
      "SMS",
      timestamp,
      `SMS-${Date.now()}`,
      "",
      "",
      "",
      `From: ${sender}`,
      "SMS Message",
      message,
      getOrAssignNfaIdForNewRow(sheet),
      `From: ${sender}\n${message}`
    ];
    sheet.appendRow(row);
    const lastRowAfter = sheet.getLastRow();

    return ContentService.createTextOutput(JSON.stringify({
      result: "success",
      type: "sms",
      sender,
      parsed: false,
      debug: Object.assign(getSheetDebugInfo_(sheet), {
        action: "new",
        rowIndex: lastRowAfter,
        lastRowBefore,
        lastRowAfter,
        wroteRow: lastRowAfter === lastRowBefore + 1
      })
    })).setMimeType(ContentService.MimeType.JSON);
  }

  const incidentId = parsed.incidentId;
  const incidentDigits = getAdjustLeadsDigitsFromIncidentId(incidentId);
  const canUpsert = parsed.incidentIdMethod === "adjustleads_url" && !!incidentDigits;

  // Find existing row by Incident ID (Column C) but only consider SMS rows
  const lastRow = sheet.getLastRow();
  let foundRow = -1;

  if (lastRow > 1) {
    const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();
    for (let i = 0; i < idValues.length; i++) {
      const sheetId = (idValues[i][0] || "").toString().trim();
      if (sheetId !== incidentId) continue;

      // Guard 1: only update SMS rows
      const statusText = (sheet.getRange(i + 2, 1).getValue() || "").toString().toUpperCase();
      if (!statusText.includes("SMS")) continue;

      // Guard 2: anti-merge — only update if Original Body (Col J) contains same /alerts/<digits>
      if (canUpsert) {
        const originalBody = (sheet.getRange(i + 2, 11).getValue() || "").toString(); // Col K
        if (!originalBody.includes(`/alerts/${incidentDigits}`)) continue;
      } else {
        // Never upsert on fallback IDs
        continue;
      }

      foundRow = i + 2;
      break;
    }
  }

  if (foundRow !== -1) {
    // UPDATE: append lines; preserve C-G immutability
    const statusCell = sheet.getRange(foundRow, 1);
    statusCell.setValue(`${statusCell.getValue()}\nSMS Update`.trim());

    const timeCell = sheet.getRange(foundRow, 2);
    timeCell.setValue(`${timeCell.getValue()}\n${timestamp}`.trim());

    if (parsed.incidentType) {
      const typeCell = sheet.getRange(foundRow, 8);
      const currentType = (typeCell.getValue() || "").toString();
      if (!currentType.includes(parsed.incidentType)) {
        typeCell.setValue(`${currentType}\n${parsed.incidentType}`.trim());
      }
    }

    if (parsed.incidentDetails) {
      const detailsCell = sheet.getRange(foundRow, 9);
      detailsCell.setValue(`${detailsCell.getValue()}\n${parsed.incidentDetails}`.trim());
    }

    const originalCell = sheet.getRange(foundRow, 11); // Col K
    originalCell.setValue(`${originalCell.getValue()}\n\nFrom: ${sender}\n${message}`.trim());

    return ContentService.createTextOutput(JSON.stringify({
      result: "success",
      type: "sms",
      sender,
      incidentId,
      action: "update",
      parsed: true,
      debug: Object.assign(getSheetDebugInfo_(sheet), {
        action: "update",
        rowIndex: foundRow,
        lastRowBefore: sheet.getLastRow(),
        lastRowAfter: sheet.getLastRow(),
        wroteRow: false
      })
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // NEW ROW
  const lastRowBefore = sheet.getLastRow();
  const row = [
    "SMS Fire Alert",
    timestamp,
    incidentId,
    parsed.state || "",
    parsed.county || "",
    parsed.city || "",
    parsed.address || "",
    parsed.incidentType || "Fire Alert",
    parsed.incidentDetails || message,
    getOrAssignNfaIdForNewRow(sheet),
    `From: ${sender}\n${message}`
  ];
  sheet.appendRow(row);
  const lastRowAfter = sheet.getLastRow();

  return ContentService.createTextOutput(JSON.stringify({
    result: "success",
    type: "sms",
    sender,
    incidentId,
    action: "new",
    parsed: true,
    incidentIdMethod: parsed.incidentIdMethod,
    debug: Object.assign(getSheetDebugInfo_(sheet), {
      action: "new",
      rowIndex: lastRowAfter,
      lastRowBefore,
      lastRowAfter,
      wroteRow: lastRowAfter === lastRowBefore + 1
    })
  })).setMimeType(ContentService.MimeType.JSON);
}

/**
 * Parse AdjustLeads SMS Content
 * Extracts stable incident ID and structured data from AdjustLeads fire alert SMS
 */
function parseAdjustLeadsSms(message, sender) {
  const result = {
    isAdjustLeads: false,
    incidentId: "",
    incidentIdMethod: "",
    incidentIdLineSnippet: "",
    address: "",
    city: "",
    county: "",
    state: "",
    incidentType: "",
    incidentDetails: "",
    mapsUrl: "",
    adjustLeadsUrl: "",
    originalMessage: message
  };

  const msg = (message || "").toString();
  if (!msg) return result;

  // Determine if this is likely an AdjustLeads fire alert
  const looksLikeAlert = msg.includes("adjustleads.") || msg.includes("Fire Alert") || msg.includes("New Fire Alert");
  if (!looksLikeAlert) return result;

  result.isAdjustLeads = true;

  const idInfo = extractAdjustLeadsIncidentId(msg, sender);
  result.incidentId = idInfo.incidentId;
  result.incidentIdMethod = idInfo.method;
  result.adjustLeadsUrl = idInfo.url || "";
  result.incidentIdLineSnippet = idInfo.lineSnippet || "";

  const lines = getNonEmptyLines(msg);

  for (const line of lines) {
    // 🔥 County / Borough line
    if (line.includes('🔥') || line.toLowerCase().includes('fire alert')) {
      const countyMatch = line.match(/(?:in|at)\s+(.+?)\s+County/i);
      if (countyMatch) result.county = countyMatch[1].trim();
      // Some real alerts omit the "County" suffix (e.g. "🔥 New Fire Alert in Union")
      if (!result.county) {
        const simple = line.match(/(?:in|at)\s+([A-Za-z .'-]{2,40})\s*$/i);
        if (simple) result.county = simple[1].trim();
      }
    }

    // 📍 Address line
    if (line.includes('📍') || (line.match(/^\d+\s+\S+/) && line.includes(','))) {
      const cleanLine = line.replace(/📍/g, '').trim();
      // Typical: Street, City, ST
      const m = cleanLine.match(/^([^,]+),\s*([^,]+),\s*([A-Z]{2})\b/i);
      if (m) {
        result.address = m[1].trim();
        result.city = m[2].trim();
        result.state = m[3].trim().toUpperCase();
      } else {
        // Robust comma split for real messages like:
        // "69 Division Street, Elizabeth, New Jersey, 07201, United States"
        const parts = cleanLine.split(",").map(p => p.trim()).filter(Boolean);
        if (parts.length >= 3) {
          result.address = parts[0];
          result.city = parts[1];

          const statePart = parts[2];
          const st = normalizeStateToAbbrev(statePart);
          if (st) result.state = st;
        } else {
          const streetMatch = cleanLine.match(/^(\d+\s+.+)$/);
          if (streetMatch) result.address = streetMatch[1].trim();
        }
      }
    }

    // 🗺️ Maps URL
    if (line.includes('🗺️') || line.includes('maps.google.com')) {
      const mapsMatch = line.match(/(https?:\/\/[^\s]+)/);
      if (mapsMatch) result.mapsUrl = mapsMatch[1];
    }

    // 📋 Type/details
    if (line.includes('📋') || (line.includes('Fire') && line.includes('-'))) {
      const cleanLine = line.replace(/📋/g, '').trim();
      const parts = cleanLine.split(' - ').map(p => p.trim()).filter(Boolean);
      if (parts.length >= 2) {
        result.incidentType = parts[0];
        result.incidentDetails = parts.slice(1).join(' - ');
      } else {
        result.incidentType = result.incidentType || "Fire Alert";
        result.incidentDetails = result.incidentDetails || cleanLine;
      }
    }
  }

  // NYC borough normalization for geocoding safety
  normalizeNyBoroughsInPlace(result);

  // If county missing but city present (non-NYC), leave county blank (safer than guessing)
  if (!result.county && result.city) {
    // leave blank
  }

  return result;
}

function normalizeStateToAbbrev(stateRaw) {
  const s = (stateRaw || "").toString().trim();
  if (!s) return "";
  if (/^[A-Za-z]{2}$/.test(s)) return s.toUpperCase();

  const key = s.toLowerCase().replace(/\./g, "");
  const map = {
    "new york": "NY",
    "new jersey": "NJ",
    "pennsylvania": "PA",
    "connecticut": "CT",
    "massachusetts": "MA",
    "delaware": "DE",
    "maryland": "MD",
    "virginia": "VA",
    "vermont": "VT",
    "maine": "ME",
    "new hampshire": "NH",
    "rhode island": "RI",
    "north carolina": "NC",
    "south carolina": "SC"
  };
  return map[key] || "";
}

/**
 * Handle Generic App Notifications
 * For apps that don't have BNN-style structured data
 */
function handleGenericApp(data, sheet) {
  const formattedTime = formatScriptTimestampFromPayload(data.timestamp || data.time || new Date());

  // Extract app notification fields
  const packageName = data.package || "Unknown App";
  const title = data.title || "";
  const text = data.text || "";
  const bigText = data.bigText || "";
  const timestamp = formattedTime;
  
  // Combine text fields for details
  const details = bigText || text || title || "(No content)";

  // BNN detection: when Android sends BNN as generic app notification (no incidentId)
  if (isLikelyBnnPayload(packageName, details)) {
    const parsed = parseBnnFromRaw(details);
    if (parsed && parsed.displayId) {
      const bnnData = {
        incidentId: parsed.displayId,
        status: parsed.status,
        state: parsed.state,
        county: parsed.county,
        city: parsed.city,
        address: parsed.address,
        incidentType: parsed.incidentType,
        incidentDetails: parsed.incidentDetails,
        fdCodes: parsed.fdCodes,
        originalBody: details
      };
      return handleBnnStructured(bnnData, sheet);
    }
  }
  
  // Generic App Row Format
  // Status | Timestamp | ID | State | County | City | Address | Type | Details | Original
  const row = [
    "App Notification",             // Status
    timestamp,                      // Timestamp
    `APP-${Date.now()}`,            // Unique App ID
    "",                             // State (blank for generic apps)
    "",                             // County (blank for generic apps)
    "",                             // City (blank for generic apps)
    packageName,                    // Address shows package name
    title || "Notification",        // Type shows notification title
    details,                        // Details shows notification content
    getOrAssignNfaIdForNewRow(sheet),
    `App: ${packageName}\nTitle: ${title}\nText: ${text}\nBigText: ${bigText}` // Original
  ];

  sheet.appendRow(row);

  return ContentService.createTextOutput(
    JSON.stringify({ result: "success", type: "app", package: packageName })
  ).setMimeType(ContentService.MimeType.JSON);
}

function getOrAssignNfaIdForNewRow(sheet) {
  const props = PropertiesService.getScriptProperties();
  const key = "NFA_NEXT_ID";
  const current = Number(props.getProperty(key) || "");
  if (current && isFinite(current) && current > 0) {
    props.setProperty(key, String(current + 1));
    return current;
  }

  // Lazy init: scan Column J (nfa-id) for max, then set next
  const lastRow = sheet.getLastRow();
  let maxId = 999999;
  if (lastRow > 1) {
    const values = sheet.getRange(2, 10, lastRow - 1, 1).getValues(); // Col J
    for (let i = 0; i < values.length; i++) {
      const n = Number((values[i][0] || "").toString().trim());
      if (isFinite(n) && n > maxId) maxId = n;
    }
  }
  const next = Math.max(1000000, maxId + 1);
  props.setProperty(key, String(next + 1));
  return next;
}

function isLikelyBnnPayload(packageName, details) {
  const pkg = (packageName || "").toString().toLowerCase();
  const body = (details || "").toString();
  if (pkg === "us.bnn.newsapp") return true;
  return body.includes("<C> BNN") || body.toUpperCase().includes("BNNDESK") || /#([12]\d{6})/.test(body);
}

function parseBnnFromRaw(raw) {
  const text = (raw || "").toString();
  if (!text) return null;
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  let bnnLine = "";
  for (let i = lines.length - 1; i >= 0; i--) {
    if (lines[i].includes("|") && lines[i].includes("#")) {
      bnnLine = lines[i];
      break;
    }
  }
  if (!bnnLine) return null;

  const idMatches = bnnLine.match(/#([12]\d{6})/g) || [];
  const last = idMatches.length ? idMatches[idMatches.length - 1] : "";
  if (!last) return null;
  const displayId = last.replace("#", ""); // digits only

  const statusLine = lines.find(l => /update|u\/d|u\/|new\s+incident|new\s+alert/i.test(l)) || "";
  const status = /update|u\/d|u\//i.test(statusLine) ? "Update" : "New Incident";

  const parts = bnnLine.split("|").map(p => p.trim());
  // Core fields stop before the "<C>" marker when present.
  const metaIdx = parts.findIndex(p => p.toUpperCase().includes("<C>"));
  const core = (metaIdx >= 0 ? parts.slice(0, metaIdx) : parts).map(p => p.trim()).filter(Boolean);

  const stateRaw = core[0] || "";
  const state = (stateRaw.match(/\b([A-Z]{2})\b/) || [])[1] || stateRaw.replace(/[^A-Z]/g, "").slice(-2);
  const unitCodeLike = (s) => /^[A-Z]{1,3}-?\d{3,5}$/.test((s || "").toString().trim());
  const looksAddressLike = (s) => {
    const t = (s || "").toString().trim();
    if (!t) return false;
    return /\d/.test(t) || /&/.test(t) || /\b(st|ave|rd|blvd|hwy|ct|dr|ln|pl|pkwy|street|avenue|road)\b/i.test(t);
  };
  const looksBoroughLike = (s) => /^(manhattan|brooklyn|queens|bronx|staten island)$/i.test((s || "").toString().trim());

  // Parse from the right (most stable): details, address, type (with optional unit code).
  // Then whatever remains (after state) becomes county/city depending on count.
  let county = "";
  let city = "";
  let incidentType = "";
  let address = "";
  let incidentDetails = "";

  if (core.length >= 4) {
    incidentDetails = core[core.length - 1] || "";
    address = core[core.length - 2] || "";
    let typeIdx = core.length - 3;

    // Optional unit code between type and address (e.g., "BK-4085", "BX-3015")
    if (unitCodeLike(core[typeIdx]) && typeIdx - 1 >= 1) {
      typeIdx = typeIdx - 1;
    }
    incidentType = core[typeIdx] || "";

    // Location tokens are between state token (index 0) and incidentType token (typeIdx)
    const loc = core.slice(1, typeIdx).map(s => s.trim()).filter(Boolean);

    if (loc.length >= 2) {
      county = loc[0];
      city = loc[1];
    } else if (loc.length === 1) {
      // NYC borough lines frequently collapse fields: treat borough token as City, keep County blank
      if (state === "NY" && looksBoroughLike(loc[0])) {
        county = "";
        city = loc[0];
      } else {
        county = loc[0];
        city = "";
      }
    } else {
      county = "";
      city = "";
    }

    // Safety: if address doesn't look like an address but incidentType does, swap
    if (!looksAddressLike(address) && looksAddressLike(incidentType)) {
      const tmp = incidentType;
      incidentType = address;
      address = tmp;
    }
  }

  const tail = parts.slice(-3).join(" | ");
  const codeTokens = tail.split(/[\/\s|]+/).map(t => t.trim()).filter(Boolean);
  const fdCodes = codeTokens
    .map(t => t.toLowerCase())
    .filter(t => t && t !== "bnn" && t !== "bnndesk" && !t.includes("bnn") && /^[a-z]{2}\w{1,}$/.test(t));

  return { displayId, status, state, county, city, address, incidentType, incidentDetails, fdCodes };
}

// Diagnostics: exercise right-to-left parsing across multiple BNN variants
function testBnnReverseParseDiagnostics() {
  const examples = [
    // With city
    `U/D NJ| Passaic| Pompton Lakes| Working Fire| 15 Central Ave| FD on the scene with a working structure fire | <C> BNN | BNNDESK/nj0w0/njq73 | #1847009`,
    // With unit code between type and address
    `NY| Brooklyn| Fire Damage| BK-0860| 405 Bainbridge St| Fire in the wall 1st floor of a 3 story brownstone | <C> BNN | BNNDESK | #1848404`,
    // NYC borough-style (no explicit city)
    `NY| Manhattan| Water Main Break| Pearl St & Water St| DEP on the scene | <C> BNN | BNNDESK | #1848577`,
    // Another borough example
    `NY| Queens| Fire Department Activity| 157th St & 134th Ave| Units O/S with a tree on top of car blocking 157 st. | <C> BNN | BNNDESK | #1844123`,
    // Your real line
    `U/D PA| Delaware| Ardmore| Working Fire| 700 Ardmore Ave| Command placed the fire under control | <C> BNN | BNNDESK | #1848573`
  ];

  Logger.log("=== BNN Reverse Parse Diagnostics ===");
  for (const raw of examples) {
    const parsed = parseBnnFromRaw(raw);
    Logger.log("--- RAW --- %s", raw);
    Logger.log("id=%s state=%s county='%s' city='%s'", parsed && parsed.displayId, parsed && parsed.state, parsed && (parsed.county || ""), parsed && (parsed.city || ""));
    Logger.log("type=%s address=%s", parsed && parsed.incidentType, parsed && parsed.address);
    Logger.log("details=%s", parsed && parsed.incidentDetails);
    Logger.log("fdCodes=%s", parsed && JSON.stringify(parsed.fdCodes || []));
  }
}

// Diagnostics: NYC/borough-style BNN line (no city field) mapping proof
function testBnnParseMappingDiagnostics_NoCity() {
  const raw = `NY| Manhattan| Water Main Break| Pearl St & Water St| DEP on the scene | <C> BNN | BNNDESK | #1848577`;
  const parsed = parseBnnFromRaw(raw);
  Logger.log("=== BNN Parse Mapping Diagnostics (No City) ===");
  Logger.log("id=%s status=%s state=%s county=%s city=%s", parsed && parsed.displayId, parsed && parsed.status, parsed && parsed.state, parsed && parsed.county, parsed && (parsed.city || ""));
  Logger.log("incidentType=%s address=%s", parsed && parsed.incidentType, parsed && parsed.address);
  Logger.log("details=%s", parsed && parsed.incidentDetails);
}

// Diagnostics: confirm borough handling (borough -> City, County blank)
function testBnnBoroughCityOnlyDiagnostics() {
  const raw = `NY| Queens| Fire Department Activity| 157th St & 134th Ave| Units O/S with a tree on top of car blocking 157 st. | <C> BNN | BNNDESK | #1844123`;
  const parsed = parseBnnFromRaw(raw);
  Logger.log("=== BNN Borough City-Only Diagnostics ===");
  Logger.log("state=%s county='%s' city='%s'", parsed && parsed.state, parsed && (parsed.county || ""), parsed && (parsed.city || ""));
  Logger.log("type=%s address=%s", parsed && parsed.incidentType, parsed && parsed.address);
}

// Diagnostics: prove whether a given BNN incidentId exists in Column C and which row would be updated.
function testBnnRowLookupDiagnostics() {
  const target = "1848573"; // change if needed
  const sheetId = "1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE";
  const sheet = SpreadsheetApp.openById(sheetId).getSheets()[0];
  const lastRow = sheet.getLastRow();
  let foundRow = -1;

  if (lastRow > 1) {
    const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();
    for (let i = 0; i < idValues.length; i++) {
      const sheetIdVal = (idValues[i][0] || "").toString().trim();
      const normalized = sheetIdVal.startsWith("#") ? sheetIdVal.substring(1) : sheetIdVal;
      if (normalized === target) {
        foundRow = i + 2;
        break;
      }
    }
  }

  Logger.log("=== BNN Row Lookup Diagnostics ===");
  Logger.log("target=%s foundRow=%s", target, foundRow);
  if (foundRow !== -1) {
    Logger.log("row=%s colC(raw)=%s colA(status)=%s", foundRow, sheet.getRange(foundRow, 3).getValue(), sheet.getRange(foundRow, 1).getValue());
  }
}

// Diagnostics: prove BNN raw mapping is correct (incidentType vs address) for a real line
function testBnnParseMappingDiagnostics() {
  const raw = `U/D PA| Delaware| Ardmore| Working Fire| 700 Ardmore Ave| Command placed the fire under control | <C> BNN | BNNDESK | #1848573`;
  const parsed = parseBnnFromRaw(raw);
  Logger.log("=== BNN Parse Mapping Diagnostics ===");
  Logger.log("id=%s status=%s state=%s county=%s city=%s", parsed && parsed.displayId, parsed && parsed.status, parsed && parsed.state, parsed && parsed.county, parsed && parsed.city);
  Logger.log("incidentType=%s address=%s", parsed && parsed.incidentType, parsed && parsed.address);
  Logger.log("details=%s", parsed && parsed.incidentDetails);
  Logger.log("fdCodes=%s", parsed && JSON.stringify(parsed.fdCodes || []));
}

function handleBnnStructured(data, sheet) {
  const rawId = data.incidentId ? data.incidentId.toString().trim() : "";
  const incidentId = rawId.startsWith("#") ? rawId.substring(1) : rawId;
  const displayId = incidentId; // digits only
  const incomingCodes = data.fdCodes && Array.isArray(data.fdCodes) ? data.fdCodes : [];

  const formattedTime = formatScriptTimestampFromPayload(data.timestamp || data.time || new Date());

  const incomingDetail = data.incidentDetails || "";
  const cleanDetail = incomingDetail
    .replace(/<C> BNN/gi, "")
    .replace(/BNNDESK.*$/gi, "")
    .trim();

  const newIncidentText = `[${formattedTime}] ${cleanDetail}`;

  const lastRow = sheet.getLastRow();
  let foundRow = -1;
  if (incidentId !== "" && lastRow > 1) {
    const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();
    for (let i = 0; i < idValues.length; i++) {
      const sheetId = idValues[i][0].toString().trim();
      const normalizedSheetId = sheetId.startsWith("#") ? sheetId.substring(1) : sheetId;
      if (normalizedSheetId === incidentId) {
        foundRow = i + 2;
        break;
      }
    }
  }

  if (foundRow !== -1) {
    // Normalize stored Incident ID in Column C to digits-only (remove leading '#')
    try {
      const idCell = sheet.getRange(foundRow, 3);
      const existingIdRaw = (idCell.getValue() || "").toString().trim();
      if (existingIdRaw.startsWith("#")) {
        idCell.setValue(existingIdRaw.substring(1));
      }
    } catch (_) {}

    const statusCell = sheet.getRange(foundRow, 1);
    statusCell.setValue(statusCell.getValue() + "\n" + (data.status || "Update"));

    const timeCell = sheet.getRange(foundRow, 2);
    timeCell.setValue(timeCell.getValue() + "\n" + formattedTime);

    const typeCell = sheet.getRange(foundRow, 8);
    const currentType = typeCell.getValue();
    const newType = data.incidentType || "";
    if (newType) typeCell.setValue(currentType + "\n" + newType);

    const incidentCell = sheet.getRange(foundRow, 9);
    incidentCell.setValue(incidentCell.getValue() + "\n" + cleanDetail);

    const originalCell = sheet.getRange(foundRow, 11);
    const newOriginal = data.originalBody || "";
    if (newOriginal) originalCell.setValue(originalCell.getValue() + "\n" + newOriginal);

    const maxCols = sheet.getLastColumn();
    let existingCodeValues = [];
    if (maxCols >= 12) {
      const codeRange = sheet.getRange(foundRow, 12, 1, maxCols - 11);
      existingCodeValues = codeRange.getValues()[0].filter((c) => c !== "");
    }
    let allCodes = new Set(existingCodeValues.map(String));
    incomingCodes.forEach((c) => { if (c !== incidentId) allCodes.add(c); });
    const uniqueCodes = Array.from(allCodes).filter(
      (c) => c !== "" && c.toLowerCase() !== "bnn" && c.toLowerCase() !== "bnndesk" && !c.toLowerCase().includes("bnn")
    );
    if (maxCols >= 12) sheet.getRange(foundRow, 12, 1, maxCols - 11).clearContent();
    if (uniqueCodes.length > 0) sheet.getRange(foundRow, 12, 1, uniqueCodes.length).setValues([uniqueCodes]);

    return ContentService.createTextOutput(JSON.stringify({ result: "success", type: "bnn", incidentId: displayId, action: "update" }))
      .setMimeType(ContentService.MimeType.JSON);
  }

  const row = [
    data.status || "New Incident",
    formattedTime,
    displayId,
    data.state || "",
    data.county || "",
    data.city || "",
    data.address || "",
    data.incidentType || "",
    newIncidentText,
    getOrAssignNfaIdForNewRow(sheet),
    data.originalBody || ""
  ];
  incomingCodes.forEach((code) => {
    if (code.toLowerCase() !== "bnn" && code.toLowerCase() !== "bnndesk") row.push(code);
  });
  sheet.appendRow(row);

  return ContentService.createTextOutput(JSON.stringify({ result: "success", type: "bnn", incidentId: displayId, action: "new" }))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * Test function for SMS Parser
 * Run this in Apps Script Editor to verify parsing logic
 */
function testSmsParser() {
  const testMessage = `🔥 New Fire Alert in Morris County
📍 31 Grand Avenue, Cedar Knolls, NJ
🗺️ https://maps.google.com/?q=31+Grand+Avenue+Cedar+Knolls+NJ
📋 Residential Fire - Possible structure fire with smoke showing
ℹ️ https://www.adjustleads.com/app/alerts/294966`;

  const result = parseAdjustLeadsSms(testMessage, "+1 888-660-1455");
  
  Logger.log("=== SMS Parser Test ===");
  Logger.log("Incident ID: " + result.incidentId);  // Should be: AL-294966
  Logger.log("County: " + result.county);            // Should be: Morris
  Logger.log("Address: " + result.address);          // Should be: 31 Grand Avenue
  Logger.log("City: " + result.city);                // Should be: Cedar Knolls
  Logger.log("State: " + result.state);              // Should be: NJ
  Logger.log("Type: " + result.incidentType);        // Should be: Residential Fire
  Logger.log("Details: " + result.incidentDetails);  // Should be: Possible structure...
  Logger.log("Is AdjustLeads: " + result.isAdjustLeads); // Should be: true
}

function getNonEmptyLines(text) {
  return (text || "")
    .toString()
    .split(/\r?\n/)
    .map(l => l.trim())
    .filter(l => l.length > 0);
}

function formatScriptTimestampFromPayload(raw) {
  const dt = parseTimestampToDate(raw) || new Date();
  return Utilities.formatDate(dt, Session.getScriptTimeZone(), "MM/dd/yyyy hh:mm:ss a");
}

function parseTimestampToDate(raw) {
  if (raw === null || raw === undefined) return null;

  // If already formatted like "MM/dd/yyyy hh:mm:ss a", keep it as-is by parsing conservatively.
  if (typeof raw === "string") {
    const s = raw.trim();

    // Already formatted (best-effort pass-through)
    if (/^\d{1,2}\/\d{1,2}\/\d{4}\s+\d{1,2}:\d{2}:\d{2}\s+(AM|PM)$/i.test(s)) {
      return new Date(s); // Apps Script Date parsing is locale-dependent, but acceptable here because we only re-format.
    }

    // Numeric string epoch seconds/millis
    if (/^\d{10,13}$/.test(s)) {
      const n = Number(s);
      return epochNumberToDate(n);
    }

    // ISO or Date.toString() formats
    const d = new Date(s);
    return isNaN(d.getTime()) ? null : d;
  }

  if (typeof raw === "number") {
    return epochNumberToDate(raw);
  }

  if (raw instanceof Date) {
    return raw;
  }

  return null;
}

function epochNumberToDate(n) {
  if (!isFinite(n)) return null;
  // seconds vs millis
  if (n < 1e12) return new Date(n * 1000);
  return new Date(n);
}

function extractAdjustLeadsIncidentId(message, sender) {
  const urlRegex = /https?:\/\/(?:www\.)?adjustleads\.(?:com|net)\/(?:app\/)?alerts\/(\d{6,})/i;
  const lines = getNonEmptyLines(message);

  // Prefer: bottom-most line containing the AdjustLeads URL
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    const m = line.match(urlRegex);
    if (m) {
      return {
        incidentId: `AL-${m[1]}`,
        method: "adjustleads_url",
        url: m[0],
        lineSnippet: redactDigits(line)
      };
    }
  }

  // Fallback: last standalone 6+ digits from NON-URL lines only (no upsert)
  let fallbackDigits = "";
  let fallbackLine = "";
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    const lower = line.toLowerCase();
    if (lower.includes("http://") || lower.includes("https://")) continue;
    if (lower.includes("maps.google") || lower.includes("google.com/maps")) continue;

    // reject phone-like sequences 10-11 digits
    if (/\b\d{10,11}\b/.test(line)) continue;

    const m = line.match(/\b(\d{6,})\b/g);
    if (m && m.length) {
      fallbackDigits = m[m.length - 1];
      fallbackLine = line;
      break;
    }
  }

  if (fallbackDigits) {
    return {
      incidentId: `AL-${fallbackDigits}`,
      method: "fallback_digits",
      url: "",
      lineSnippet: redactDigits(fallbackLine)
    };
  }

  // Last resort: stable hash on sender + prefix
  const hashBytes = Utilities.computeDigest(
    Utilities.DigestAlgorithm.MD5,
    (sender || "") + (message || "").toString().substring(0, 50),
    Utilities.Charset.UTF_8
  );
  const hex = hashBytes.map(b => (b < 0 ? b + 256 : b).toString(16).padStart(2, "0")).join("");
  return {
    incidentId: `AL-${hex.substring(0, 16)}`,
    method: "hash",
    url: "",
    lineSnippet: ""
  };
}

function getAdjustLeadsDigitsFromIncidentId(incidentId) {
  const m = (incidentId || "").toString().match(/^AL-(\d{6,})$/);
  return m ? m[1] : "";
}

function redactDigits(text) {
  return (text || "").toString().replace(/\d/g, "X").slice(0, 160);
}

function normalizeNyBoroughsInPlace(parsed) {
  const state = (parsed.state || "").toString().trim().toUpperCase();
  const cityRaw = (parsed.city || "").toString().trim();

  if (state !== "NY") return;

  const city = cityRaw.toLowerCase();
  const map = {
    "manhattan": { county: "New York", city: "New York" },
    "brooklyn": { county: "Kings", city: "New York" },
    "queens": { county: "Queens", city: "New York" },
    "bronx": { county: "Bronx", city: "New York" },
    "staten island": { county: "Richmond", city: "New York" }
  };

  if (map[city]) {
    parsed.county = map[city].county;
    parsed.city = map[city].city;
  }
}

// Diagnostics
function testSmsWiringDiagnostics() {
  const examples = [
    {
      name: "AdjustLeads standard",
      sender: "+1 888-660-1455",
      message: `🔥 New Fire Alert in Morris County
📍 31 Grand Avenue, Cedar Knolls, NJ
🗺️ https://maps.google.com/?q=31+Grand+Avenue+Cedar+Knolls+NJ
📋 Residential Fire - Possible structure fire with smoke showing
ℹ️ https://www.adjustleads.com/app/alerts/294966`,
      ts: "2025-12-30T20:01:21Z"
    },
    {
      name: "NYC Manhattan normalization",
      sender: "+1 888-660-1455",
      message: `🔥 New Fire Alert in New York County
📍 10 Whitehall St, Manhattan, NY
📋 Electrical Fire - Small fire on the wall with crews opening up.
ℹ️ https://www.adjustleads.com/app/alerts/12554444`,
      ts: 1767138546261
    },
    {
      name: "No URL noisy digits (no upsert)",
      sender: "+1 888-660-1455",
      message: `🔥 New Fire Alert
📍 719 East 11th Street, Ocean City, NJ 08226
🗺️ https://maps.google.com/?q=719+East+11th+Street+Ocean+City+NJ+08226
📋 Structural Fire - reported in a structure at the addressed location`,
      ts: "12/30/2025 08:01:21 PM"
    },
    {
      name: "Real format (full state name, no 'County')",
      sender: "+1 888-660-1455",
      message: `🔥 New Fire Alert in Union

📍 69 Division Street, Elizabeth, New Jersey, 07201, United States
🗺️ https://maps.google.com/?q=69+Division+Street,+Elizabeth,+New+Jersey,+07201,+United+States
📋 Structural Fire - High-rise structure fire with injuries; multiple units responding.
ℹ️ https://www.adjustleads.com/app/alerts/301516`,
      ts: "12/30/2025 08:01:21 PM"
    }
  ];

  Logger.log("=== SMS Wiring Diagnostics ===");
  for (const ex of examples) {
    const parsed = parseAdjustLeadsSms(ex.message, ex.sender);
    const ts = formatScriptTimestampFromPayload(ex.ts);
    Logger.log("--- %s ---", ex.name);
    Logger.log("timestamp=%s", ts);
    Logger.log("incidentId=%s method=%s snippet=%s", parsed.incidentId, parsed.incidentIdMethod, parsed.incidentIdLineSnippet);
    Logger.log("state=%s county=%s city=%s address=%s", parsed.state, parsed.county, parsed.city, parsed.address);
    Logger.log("type=%s details=%s", parsed.incidentType, parsed.incidentDetails);
  }
}

// Diagnostics: confirm BNN ID is stored digits-only (no '#')
function testBnnIdDigitsOnlyDiagnostics() {
  const raw = `Update
9/15/25
U/D NJ| Bergen| Rutherford| Car Vs Building| 510 Union Ave| CMD reports no structural damage.| <C> BNN | BNNDESK/njvx6/nj7ue | #1825178`;
  const parsed = parseBnnFromRaw(raw);
  Logger.log("=== BNN ID Digits-Only Diagnostics ===");
  Logger.log("displayId=%s (should be digits only)", parsed && parsed.displayId);
}


