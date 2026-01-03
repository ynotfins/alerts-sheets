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
        JSON.stringify({ result: "verified" })
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

    // 3. BNN Incident Handling (structured data with incident ID)
    // Key Fields
    const rawId = data.incidentId ? data.incidentId.toString().trim() : "";
    const incidentId = rawId.startsWith("#") ? rawId.substring(1) : rawId; // Strip # for search
    const displayId = rawId.startsWith("#") ? rawId : "#" + rawId; // Ensure # for display
    
    const incomingCodes =
      data.fdCodes && Array.isArray(data.fdCodes) ? data.fdCodes : [];

    // Timestamp Generation (User requested: Date + Time)
    // Format: "MM/dd/yyyy HH:mm:ss"
    const now = new Date();
    const formattedTime = Utilities.formatDate(
      now,
      Session.getScriptTimeZone(),
      "MM/dd/yyyy hh:mm:ss a"
    );

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

    if (foundRow !== -1) {
      // --- UPDATE EXISTING ROW (APPEND MODE) ---

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

      // 5. Original Body (Col 10/J): Append full notification text
      const originalCell = sheet.getRange(foundRow, 10);
      const currentOriginal = originalCell.getValue();
      const newOriginal = data.originalBody || "";
      if (newOriginal) {
        originalCell.setValue(currentOriginal + "\n" + newOriginal);
      }

      // 6. FD Codes Logic (Col 11+/K+): Merge Unique ONLY
      const maxCols = sheet.getLastColumn();
      let existingCodeValues = [];
      if (maxCols >= 11) {
        const codeRange = sheet.getRange(foundRow, 11, 1, maxCols - 10);
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
      if (maxCols >= 11) {
        sheet.getRange(foundRow, 11, 1, maxCols - 10).clearContent();
      }
      if (uniqueCodes.length > 0) {
        sheet
          .getRange(foundRow, 11, 1, uniqueCodes.length)
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
        displayId, // Always write with # prefix

        data.state || "",
        data.county || "",
        data.city || "",
        data.address || "",
        data.incidentType || "",
        newIncidentText,
        data.originalBody || "",
      ];

      // Add FD Codes to end of array
      incomingCodes.forEach((code) => {
        if (code.toLowerCase() !== "bnn" && code.toLowerCase() !== "bnndesk") {
          row.push(code);
        }
      });

      sheet.appendRow(row);
    }

    return ContentService.createTextOutput(
      JSON.stringify({ result: "success", id: incidentId })
    ).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(
      JSON.stringify({ result: "error", error: err.toString() })
    ).setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
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
      `From: ${sender}\n${message}`
    ];
    sheet.appendRow(row);

    return ContentService.createTextOutput(JSON.stringify({
      result: "success",
      type: "sms",
      sender,
      parsed: false
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
        const originalBody = (sheet.getRange(i + 2, 10).getValue() || "").toString();
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

    const originalCell = sheet.getRange(foundRow, 10);
    originalCell.setValue(`${originalCell.getValue()}\n\nFrom: ${sender}\n${message}`.trim());

    return ContentService.createTextOutput(JSON.stringify({
      result: "success",
      type: "sms",
      sender,
      incidentId,
      action: "update",
      parsed: true
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // NEW ROW
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
    `From: ${sender}\n${message}`
  ];
  sheet.appendRow(row);

  return ContentService.createTextOutput(JSON.stringify({
    result: "success",
    type: "sms",
    sender,
    incidentId,
    action: "new",
    parsed: true,
    incidentIdMethod: parsed.incidentIdMethod
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
        // Sometimes: Street, City, State ZIP
        const m2 = cleanLine.match(/^([^,]+),\s*([^,]+),\s*(New\s+York|New\s+Jersey|Pennsylvania|Connecticut|Massachusetts|Delaware|Maryland|Virginia|Vermont|Maine|New\s+Hampshire|Rhode\s+Island|North\s+Carolina|South\s+Carolina)\b/i);
        if (m2) {
          result.address = m2[1].trim();
          result.city = m2[2].trim();
          // State abbreviation from full name is out-of-scope; leave state blank if not 2-letter
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

/**
 * Handle Generic App Notifications
 * For apps that don't have BNN-style structured data
 */
function handleGenericApp(data, sheet) {
  const now = new Date();
  const formattedTime = Utilities.formatDate(
    now,
    Session.getScriptTimeZone(),
    "MM/dd/yyyy hh:mm:ss a"
  );

  // Extract app notification fields
  const packageName = data.package || "Unknown App";
  const title = data.title || "";
  const text = data.text || "";
  const bigText = data.bigText || "";
  const timestamp = data.timestamp || formattedTime;
  
  // Combine text fields for details
  const details = bigText || text || title || "(No content)";
  
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
    `App: ${packageName}\nTitle: ${title}\nText: ${text}\nBigText: ${bigText}` // Original
  ];

  sheet.appendRow(row);

  return ContentService.createTextOutput(
    JSON.stringify({ result: "success", type: "app", package: packageName })
  ).setMimeType(ContentService.MimeType.JSON);
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

// (Instrumentation cleaned up after fix confirmation)
clasp login