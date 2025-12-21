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
 * Intelligently parse SMS content for fire alerts vs generic SMS
 */
function handleSmsMessage(data, sheet) {
  const now = new Date();
  const formattedTime = Utilities.formatDate(
    now,
    Session.getScriptTimeZone(),
    "MM/dd/yyyy hh:mm:ss a"
  );

  const sender = data.sender || "Unknown";
  const message = data.message || "";
  const timestamp = data.timestamp || formattedTime;

  // ✅ SMART PARSING: Try to extract fire alert data from SMS content
  const parsedData = parseFireAlertSms(message, sender);

  if (parsedData.isFireAlert) {
    // Fire Alert SMS - Use structured format
    const row = [
      "SMS Fire Alert",              // Status
      timestamp,                     // Timestamp
      `SMS-${Date.now()}`,           // Unique SMS ID
      parsedData.state || "",        // State
      parsedData.county || "",       // County
      parsedData.city || "",         // City
      parsedData.address || "",      // Address
      parsedData.incidentType || "Fire Alert", // Type
      parsedData.details || message, // Details
      `From: ${sender}\n${message}`  // Original Body
    ];
    sheet.appendRow(row);
  } else {
    // Generic SMS - Use simple format
    const row = [
      "SMS",                          // Status
      timestamp,                      // Timestamp
      `SMS-${Date.now()}`,            // Unique SMS ID
      "",                             // State (blank)
      "",                             // County (blank)
      "",                             // City (blank)
      `From: ${sender}`,              // Address shows sender
      "SMS Message",                  // Type
      message,                        // Details shows message text
      `From: ${sender}\n${message}`   // Original Body
    ];
    sheet.appendRow(row);
  }

  return ContentService.createTextOutput(
    JSON.stringify({ 
      result: "success", 
      type: "sms", 
      sender: sender,
      parsed: parsedData.isFireAlert 
    })
  ).setMimeType(ContentService.MimeType.JSON);
}

/**
 * Parse Fire Alert SMS Content
 * Extracts address, city, county, incident type from SMS text
 */
function parseFireAlertSms(message, sender) {
  const result = {
    isFireAlert: false,
    address: "",
    city: "",
    county: "",
    state: "",
    incidentType: "",
    details: message
  };

  // Check if this looks like a fire alert
  const alertKeywords = /\b(ALERT|Fire|Emergency|Residential|Structure|Vehicle|Medical|EMS|Accident|Rescue)\b/i;
  if (!alertKeywords.test(message)) {
    return result; // Not a fire alert
  }

  result.isFireAlert = true;

  // Extract address patterns
  // Pattern 1: "Fire reported at 123 Main St"
  // Pattern 2: "📍 31 Grand Avenue, Cedar Knolls, NJ"
  // Pattern 3: "Address: 123 Main Street, City, State ZIP"
  
  // Try to extract full address with city/state/zip
  const fullAddressMatch = message.match(/(?:at|📍|Address:)\s*([^,\n]+),\s*([^,\n]+),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)/i);
  if (fullAddressMatch) {
    result.address = fullAddressMatch[1].trim();
    result.city = fullAddressMatch[2].trim();
    result.state = fullAddressMatch[3].trim();
  } else {
    // Try simpler address pattern
    const simpleAddressMatch = message.match(/(?:at|reported at|located at|📍)\s*([0-9]+\s+[A-Za-z\s]+(?:Street|St|Avenue|Ave|Road|Rd|Drive|Dr|Lane|Ln|Boulevard|Blvd|Way))/i);
    if (simpleAddressMatch) {
      result.address = simpleAddressMatch[1].trim();
    }
  }

  // Extract county (common in fire alerts)
  const countyMatch = message.match(/(\w+)\s+County/i);
  if (countyMatch) {
    result.county = countyMatch[1].trim();
  }

  // Extract incident type
  const typePatterns = [
    /Residential\s+Fire/i,
    /Structure\s+Fire/i,
    /Vehicle\s+Fire/i,
    /Medical\s+Emergency/i,
    /Fire\s+with\s+smoke/i,
    /\b(Fire|Medical|Rescue|Accident|Emergency)\b/i
  ];

  for (const pattern of typePatterns) {
    const match = message.match(pattern);
    if (match) {
      result.incidentType = match[0].trim();
      break;
    }
  }

  // Extract details (first line or text before URLs)
  const detailsMatch = message.split(/\n|https?:\/\//)[0].trim();
  if (detailsMatch) {
    result.details = detailsMatch;
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
