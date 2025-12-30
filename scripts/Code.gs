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
  const sender = data.sender || "Unknown";
  const message = data.message || "";

  // SMS timestamp rule: ALWAYS display in Script timezone as "MM/dd/yyyy hh:mm:ss a"
  const timestamp = formatScriptTimestampFromPayload(data.timestamp || data.time);

  // ✅ Parse AdjustLeads SMS
  const parsedData = parseAdjustLeadsSms(message, sender);

  if (!parsedData.isAdjustLeads) {
    // Generic SMS - Simple format (backward compat)
    const row = [
      "SMS",
      timestamp,
      `SMS-${Date.now()}`,  // One-off ID OK for generic
      "",
      "",
      "",
      `From: ${sender}`,
      "SMS Message",
      message,
      `From: ${sender}\n${message}`
    ];
    sheet.appendRow(row);
    
    return ContentService.createTextOutput(
      JSON.stringify({ 
        result: "success", 
        type: "sms", 
        sender: sender,
        parsed: false 
      })
    ).setMimeType(ContentService.MimeType.JSON);
  }

  // ✅ AdjustLeads SMS with stable incident ID
  const incidentId = parsedData.incidentId;
  const incidentDigits = getIncidentDigitsFromAlId(incidentId);

  // Required debug logging (PII-safe): incident id method + line snippet (digits redacted)
  Logger.log(
    "[SMS] incidentId=%s method=%s matchedLine=%s",
    incidentId,
    parsedData.incidentIdMethod || "",
    parsedData.incidentIdLineSnippet || ""
  );
  
  // Search for existing row (same logic as BNN)
  // Defensive: only upsert when incidentId came from AdjustLeads URL extraction.
  // This prevents accidental row merging when fallback digits are ambiguous.
  let foundRow = -1;
  if (parsedData.incidentIdMethod === "adjustleads_url") {
    const lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();
      for (let i = 0; i < idValues.length; i++) {
        const sheetId = idValues[i][0].toString().trim();
        if (sheetId === incidentId) {
          foundRow = i + 2;
          break;
        }
      }
    }
  } else {
    Logger.log(
      "[SMS] upsert_suppressed incidentId=%s method=%s",
      incidentId,
      parsedData.incidentIdMethod || ""
    );
  }
  
  if (foundRow !== -1) {
    // Extra anti-merge defense:
    // If the existing row doesn't include the same AdjustLeads /alerts/<digits> in column J,
    // do NOT upsert; treat as a new incident row instead.
    if (incidentDigits) {
      const originalCell = sheet.getRange(foundRow, 10);
      const existingOriginal = (originalCell.getValue() || "").toString();
      const expectedUrlFragment = "/alerts/" + incidentDigits;
      if (existingOriginal.indexOf(expectedUrlFragment) === -1) {
        Logger.log(
          "[SMS] upsert_row_mismatch incidentId=%s expectedFragment=%s (creating new row instead)",
          incidentId,
          expectedUrlFragment
        );
        foundRow = -1;
      }
    }
  }

  if (foundRow !== -1) {
    // ✅ UPDATE EXISTING ROW (append lines)
    
    // Status: Append "SMS Update"
    const statusCell = sheet.getRange(foundRow, 1);
    statusCell.setValue(statusCell.getValue() + "\nSMS Update");
    
    // Timestamp: Append new timestamp
    const timeCell = sheet.getRange(foundRow, 2);
    timeCell.setValue(timeCell.getValue() + "\n" + timestamp);
    
    // Incident Type: Append if changed (column H)
    if (parsedData.incidentType) {
      const typeCell = sheet.getRange(foundRow, 8);
      const currentType = typeCell.getValue();
      if (!currentType.includes(parsedData.incidentType)) {
        typeCell.setValue(currentType + "\n" + parsedData.incidentType);
      }
    }
    
    // Incident Details: Append new details (column I)
    if (parsedData.incidentDetails) {
      const detailsCell = sheet.getRange(foundRow, 9);
      detailsCell.setValue(detailsCell.getValue() + "\n" + parsedData.incidentDetails);
    }
    
    // Original Body: Append full SMS (column J)
    const originalCell = sheet.getRange(foundRow, 10);
    originalCell.setValue(originalCell.getValue() + "\n\nFrom: " + sender + "\n" + message);
    
    return ContentService.createTextOutput(
      JSON.stringify({ 
        result: "success", 
        type: "sms", 
        sender: sender,
        incidentId: incidentId,
        action: "update",
        parsed: true 
      })
    ).setMimeType(ContentService.MimeType.JSON);
    
  } else {
    // ✅ CREATE NEW ROW
    const row = [
      "SMS Fire Alert",                // Status
      timestamp,                       // Timestamp
      incidentId,                      // Stable incident ID (AL-294966)
      parsedData.state || "",          // State (from 📍 line)
      parsedData.county || "",         // County (from 🔥 line)
      parsedData.city || "",           // City (from 📍 line)
      parsedData.address || "",        // Address (from 📍 line)
      parsedData.incidentType || "Fire Alert",  // Type (from 📋 line)
      parsedData.incidentDetails || message,    // Details (from 📋 line)
      `From: ${sender}\n${message}`    // Original Body
    ];
    
    sheet.appendRow(row);
    
    return ContentService.createTextOutput(
      JSON.stringify({ 
        result: "success", 
        type: "sms", 
        sender: sender,
        incidentId: incidentId,
        action: "new",
        parsed: true 
      })
    ).setMimeType(ContentService.MimeType.JSON);
  }
}

/**
 * Parse AdjustLeads SMS Content
 * Extracts stable incident ID and structured data from AdjustLeads fire alert SMS
 */
function parseAdjustLeadsSms(message, sender) {
  const result = {
    isAdjustLeads: false,
    incidentId: "",           // ✅ Stable ID for upsert
    incidentIdMethod: "",     // "adjustleads_url" | "fallback_digits" | "hash"
    incidentIdLineSnippet: "",// PII-safe snippet (digits redacted) of the matched line
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
  
  // Step 1: Heuristic: AdjustLeads-style messages typically include emojis or "Fire Alert"
  const looksLikeAdjustLeads =
    message.includes("adjustleads.") ||
    message.includes("Fire Alert") ||
    (message.includes("🔥") && message.includes("📍") && message.includes("📋"));
  if (!looksLikeAdjustLeads) {
    return result;
  }

  result.isAdjustLeads = true;

  // Step 2: Extract incident ID (STRICT)
  // Rule:
  // - Prefer AdjustLeads URL line (typically last non-empty line)
  // - Fallback: last 6+ digit sequence ONLY if URL is absent and candidate line is safe (not maps/urls/phone-like)
  const idExtraction = extractAdjustLeadsIncidentId(message, sender);
  result.incidentId = idExtraction.incidentId;
  result.incidentIdMethod = idExtraction.method;
  result.incidentIdLineSnippet = idExtraction.matchedLineSnippet;
  result.adjustLeadsUrl = idExtraction.adjustLeadsUrl;
  
  // Step 3: Parse emoji-delimited lines
  const lines = getNonEmptyLines(message);
  
  for (const line of lines) {
    // 🔥 County line: "New Fire Alert in Morris County"
    if (line.includes('🔥') || line.includes('Fire Alert')) {
      const countyMatch = line.match(/(?:in|at)\s+(\w+(?:\s+\w+)?)\s+County/i);
      if (countyMatch) {
        result.county = countyMatch[1].trim();
      }
    }
    
    // 📍 Address line: "31 Grand Avenue, Cedar Knolls, NJ"
    if (line.includes('📍') || (line.match(/\d+\s+\w+/) && line.includes(','))) {
      const cleanLine = line.replace(/📍/g, '').trim();
      // Pattern: Street Address, City, State
      const addressMatch = cleanLine.match(/^([^,]+),\s*([^,]+),\s*([A-Z]{2})/i);
      if (addressMatch) {
        result.address = addressMatch[1].trim();
        result.city = addressMatch[2].trim();
        result.state = addressMatch[3].trim().toUpperCase();
      } else {
        // Fallback: Just take the street address part
        const streetMatch = cleanLine.match(/^(\d+\s+[A-Za-z\s]+)/);
        if (streetMatch) {
          result.address = streetMatch[1].trim();
        }
      }
    }
    
    // 🗺️ Maps URL line (capture for future geocoding)
    if (line.includes('🗺️') || line.includes('maps.google.com')) {
      const mapsMatch = line.match(/(https?:\/\/[^\s]+)/);
      if (mapsMatch) {
        result.mapsUrl = mapsMatch[1];
      }
    }
    
    // 📋 Incident type line: "Residential Fire - Possible structure fire..."
    if (line.includes('📋') || (line.includes('Fire') && line.includes('-'))) {
      const cleanLine = line.replace(/📋/g, '').trim();
      const parts = cleanLine.split('-').map(p => p.trim());
      if (parts.length >= 2) {
        result.incidentType = parts[0]; // "Residential Fire"
        result.incidentDetails = parts.slice(1).join(' - '); // Rest
      } else {
        result.incidentType = "Fire Alert";
        result.incidentDetails = cleanLine;
      }
    }
  }
  
  // Validation: Ensure we extracted minimum data
  if (!result.address && !result.county) {
    // Mark as partially parsed
    result.incidentDetails = result.incidentDetails || message;
  }

  // Step 4: NYC borough normalization (geocoding safety)
  // Apply for state == NY OR if borough clearly indicated in message lines.
  const nyNormalized = normalizeNyBoroughs({
    city: result.city,
    county: result.county,
    state: result.state,
    address: result.address,
    messageLines: lines
  });
  result.city = nyNormalized.city;
  result.county = nyNormalized.county;
  result.state = nyNormalized.state;
  
  return result;
}

/**
 * SMS timestamp formatting: ALWAYS in Script timezone as "MM/dd/yyyy hh:mm:ss a"
 * Accepts payload timestamps as number (ms) or string (ISO), but always normalizes output.
 */
function formatScriptTimestampFromPayload(payloadTimestamp) {
  const tz = Session.getScriptTimeZone();

  // If payload already looks like our desired format, return as-is
  // "MM/dd/yyyy hh:mm:ss a"
  const asString = payloadTimestamp !== null && payloadTimestamp !== undefined ? payloadTimestamp.toString().trim() : "";
  if (/^\d{2}\/\d{2}\/\d{4}\s+\d{2}:\d{2}:\d{2}\s+[AP]M$/.test(asString)) {
    return asString;
  }

  let d = null;

  if (payloadTimestamp !== null && payloadTimestamp !== undefined && payloadTimestamp !== "") {
    if (typeof payloadTimestamp === "number") {
      // Support epoch seconds as well as millis
      const n = payloadTimestamp;
      d = new Date(n < 1e12 ? n * 1000 : n);
    } else if (typeof payloadTimestamp === "string") {
      const s = payloadTimestamp.trim();

      // Numeric string: epoch seconds/millis
      if (/^\d+$/.test(s)) {
        const n = parseInt(s, 10);
        d = new Date(n < 1e12 ? n * 1000 : n);
      } else {
        // ISO string or other Date-parsable string
        d = new Date(s);
      }
    }
  }

  if (!d || isNaN(d.getTime())) {
    d = new Date();
  }

  return Utilities.formatDate(d, tz, "MM/dd/yyyy hh:mm:ss a");
}

function getNonEmptyLines(message) {
  return message
    .split("\n")
    .map((l) => (l || "").toString().trim())
    .filter((l) => l.length > 0);
}

function redactDigitsForLogs(text) {
  return (text || "").toString().replace(/\d/g, "X");
}

function getIncidentDigitsFromAlId(alId) {
  const m = (alId || "").toString().match(/^AL-(\d{6,})$/);
  return m ? m[1] : "";
}

function extractAdjustLeadsIncidentId(message, sender) {
  const urlRegex = /https?:\/\/(?:www\.)?adjustleads\.(?:com|net)\/(?:app\/)?alerts\/(\d{6,})/i;
  const lines = getNonEmptyLines(message);

  // Prefer: the last (bottom-most) line containing the AdjustLeads URL.
  // This aligns with "URL at end" while still tolerating occasional trailing lines/whitespace.
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    const m = line.match(urlRegex);
    if (m) {
      const incidentId = `AL-${m[1]}`;
      const matchedLineSnippet = redactDigitsForLogs(line).slice(0, 80);
      Logger.log(
        "[SMS] incidentId_extracted id=%s method=%s line=%s",
        incidentId,
        "adjustleads_url",
        matchedLineSnippet
      );
      return {
        incidentId: incidentId,
        method: "adjustleads_url",
        matchedLineSnippet: matchedLineSnippet,
        adjustLeadsUrl: m[0]
      };
    }
  }

  // Fallback: last 6+ digit sequence ONLY if URL is absent and candidate line is safe.
  // Defensive filters:
  // - ignore maps/google URL lines
  // - ignore any URL-containing lines (http/https)
  // - ignore phone-like 10-11 digit sequences
  let fallback = null;
  let fallbackLine = "";
  for (let i = lines.length - 1; i >= 0; i--) {
    const line = lines[i];
    const lower = line.toLowerCase();

    if (lower.includes("maps.google.com") || lower.includes("google.com/maps") || lower.includes("maps.app.goo.gl")) {
      continue;
    }
    if (lower.includes("http://") || lower.includes("https://")) {
      continue;
    }

    // Ignore address/zip context lines to avoid mistaking ZIP+4 or other numeric address noise as an incidentId
    if (/\b[A-Z]{2}\s+\d{5}(?:-\d{4})?\b/.test(line)) {
      continue;
    }
    if (line.includes("📍")) {
      continue;
    }

    const matches = line.match(/\b(\d{6,})\b/g);
    if (!matches || matches.length === 0) {
      continue;
    }

    // pick last match in this line
    const candidate = matches[matches.length - 1];

    // reject phone-like candidates
    if (candidate.length === 10 || candidate.length === 11) {
      continue;
    }

    fallback = candidate;
    fallbackLine = line;
    break;
  }

  if (fallback) {
    const incidentId = `AL-${fallback}`;
    const matchedLineSnippet = redactDigitsForLogs(fallbackLine).slice(0, 80);
    Logger.log(
      "[SMS] incidentId_extracted id=%s method=%s line=%s",
      incidentId,
      "fallback_digits",
      matchedLineSnippet
    );
    return {
      incidentId: incidentId,
      method: "fallback_digits",
      matchedLineSnippet: matchedLineSnippet,
      adjustLeadsUrl: ""
    };
  }

  // Last resort: hash-based stable ID (never used for upsert)
  const hash = Utilities.computeDigest(
    Utilities.DigestAlgorithm.MD5,
    (sender || "") + (message || "").substring(0, 50)
  );
  const shortHash = hash
    .slice(0, 8)
    .map((b) => (b & 0xff).toString(16).padStart(2, "0"))
    .join("");
  const incidentId = `AL-${shortHash}`;
  Logger.log("[SMS] incidentId_extracted id=%s method=%s line=%s", incidentId, "hash", "(hash)");
  return {
    incidentId: incidentId,
    method: "hash",
    matchedLineSnippet: "(hash)",
    adjustLeadsUrl: ""
  };
}

/**
 * NYC borough normalization helper (geocoding safety)
 * - Treat boroughs as counties when county missing
 * - Prefer city="New York" and county=<official county> for consistency
 */
function normalizeNyBoroughs(input) {
  const cityRaw = (input.city || "").toString().trim();
  const countyRaw = (input.county || "").toString().trim();
  const stateRaw = (input.state || "").toString().trim().toUpperCase();
  const lines = Array.isArray(input.messageLines) ? input.messageLines : [];

  const boroughMap = {
    manhattan: { county: "New York", city: "New York" },
    "new york": { county: "New York", city: "New York" },
    brooklyn: { county: "Kings", city: "New York" },
    queens: { county: "Queens", city: "New York" },
    bronx: { county: "Bronx", city: "New York" },
    "staten island": { county: "Richmond", city: "New York" }
  };

  function detectBoroughName(text) {
    const t = (text || "").toString().trim().toLowerCase();
    if (!t) return "";
    if (t === "staten" || t === "statenisland") return "staten island";
    if (boroughMap[t]) return t;
    return "";
  }

  // Borough can be in city or county fields
  let borough = detectBoroughName(cityRaw) || detectBoroughName(countyRaw);

  // Or inferred from message lines
  if (!borough) {
    for (let i = 0; i < lines.length; i++) {
      const l = lines[i].toLowerCase();
      if (l.includes("manhattan")) borough = "manhattan";
      else if (l.includes("brooklyn")) borough = "brooklyn";
      else if (l.includes("queens")) borough = "queens";
      else if (l.includes("bronx")) borough = "bronx";
      else if (l.includes("staten island")) borough = "staten island";
      if (borough) break;
    }
  }

  // Apply only if NY OR borough clearly indicated
  const shouldApply = stateRaw === "NY" || !!borough;
  if (!shouldApply) {
    return { city: cityRaw, county: countyRaw, state: stateRaw };
  }

  let outState = stateRaw || (borough ? "NY" : "");
  let outCity = cityRaw;
  let outCounty = countyRaw;

  if (borough && boroughMap[borough]) {
    // Fill missing pieces (do not overwrite non-empty county unless it's also borough name)
    if (!outCounty || detectBoroughName(outCounty)) {
      outCounty = boroughMap[borough].county;
    }
    if (!outCity || detectBoroughName(outCity)) {
      outCity = boroughMap[borough].city;
    }
  }

  return { city: outCity, county: outCounty, state: outState };
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

/**
 * Deterministic SMS parsing harness (3 cases)
 * Run in Apps Script editor and inspect Execution Logs.
 */
function testSmsParsingExamples() {
  const examples = [
    {
      name: "1) Standard AdjustLeads w/ URL at end",
      sender: "+1 888-660-1455",
      message: `🔥 New Fire Alert in Morris County
📍 31 Grand Avenue, Cedar Knolls, NJ
🗺️ https://maps.google.com/?q=31+Grand+Avenue+Cedar+Knolls+NJ
📋 Residential Fire - Possible structure fire with smoke showing
ℹ️ https://www.adjustleads.com/app/alerts/294966`
    },
    {
      name: "2) NYC borough (Manhattan) missing county",
      sender: "+1 888-660-1455",
      message: `🔥 New Fire Alert
📍 123 Broadway, Manhattan, NY
📋 Residential Fire - Smoke in lobby
ℹ️ https://www.adjustleads.com/app/alerts/555666`
    },
    {
      name: "3) Multiple numbers (maps + zip + id) - ensure correct incidentId",
      sender: "+1 888-660-1455",
      message: `🔥 New Fire Alert
📍 10 Main Street, Queens, NY 11101
🗺️ https://maps.google.com/?q=10+Main+Street+Queens+NY+11101
📋 Residential Fire - Caller reports smoke
ℹ️ See map for details (no AdjustLeads link present)`
    }
  ];

  Logger.log("=== testSmsParsingExamples ===");
  for (let i = 0; i < examples.length; i++) {
    const ex = examples[i];
    const r = parseAdjustLeadsSms(ex.message, ex.sender);
    Logger.log("--- %s ---", ex.name);
    Logger.log("incidentId=%s method=%s line=%s", r.incidentId, r.incidentIdMethod, r.incidentIdLineSnippet);
    Logger.log("state=%s county=%s city=%s address=%s", r.state, r.county, r.city, r.address);
    Logger.log("type=%s details=%s", r.incidentType, r.incidentDetails);
  }
}
