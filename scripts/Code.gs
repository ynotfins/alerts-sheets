function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(30000); // Wait for up to 30 seconds for concurrent interactions

    const sheetId = "1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE";
    const sheet = SpreadsheetApp.openById(sheetId).getSheets()[0];

    // Parse Incoming Data
    const data = JSON.parse(e.postData.contents);

    // Key Fields
    const incidentId = data.incidentId ? data.incidentId.toString().trim() : "";
    const incomingCodes =
      data.fdCodes && Array.isArray(data.fdCodes) ? data.fdCodes : [];

    // Timestamp Generation (User requested: Date + Time)
    // Format: "MM/dd/yyyy HH:mm:ss"
    const now = new Date();
    const formattedTime = Utilities.formatDate(
      now,
      Session.getScriptTimeZone(),
      "MM/dd/yyyy HH:mm:ss"
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

    const newIncidentText = `[${formattedTime}] ${cleanDetail}`;

    // Search for existing row with this Incident ID (Column C, Index 2)
    const lastRow = sheet.getLastRow();
    let foundRow = -1;
    let existingCodes = [];

    if (incidentId !== "" && lastRow > 1) {
      // Get all IDs from Column C (Row 2 to Last)
      // optimization: get only column C
      const idValues = sheet.getRange(2, 3, lastRow - 1, 1).getValues();

      for (let i = 0; i < idValues.length; i++) {
        if (idValues[i][0].toString().trim() === incidentId) {
          foundRow = i + 2; // +2 because index 0 is row 2
          break;
        }
      }
    }

    if (foundRow !== -1) {
      // --- UPDATE EXISTING ROW ---

      // 1. Update Incident Text (Column H -> Index 8)
      // User wants: APPEND " • [Date Time] Message"
      const incidentCell = sheet.getRange(foundRow, 9); // Column I (index 9) ?? Wait, letters.
      // A=1, B=2, C=3, D=4, E=5, F=6, G=7, H=8, I=9
      // Code.gs snippet from user before:
      // row = [status, timestamp, id, state, county, city, address, type, details, original]
      // That maps:
      // A: Status
      // B: Timestamp
      // C: ID
      // D: State
      // E: County
      // F: City
      // G: Address
      // H: Type
      // I: Incident (Details)

      // Let's verify mapping:
      // row[8] is Incident Details. range column is 9.

      const currentText = incidentCell.getValue();
      const appendText = ` • ${newIncidentText}`;
      incidentCell.setValue(currentText + appendText);

      // 2. Update Incident Type (Column H -> Range Col 8)??
      // Based on array above: Type is index 7 -> Column H (8).
      // User said "Alert type should get added the same way"
      const typeCell = sheet.getRange(foundRow, 8);
      const currentType = typeCell.getValue();
      const newType = data.incidentType || "";
      if (newType && !currentType.includes(newType)) {
        typeCell.setValue(currentType + ` • ${newType}`);
      }

      // 3. Update Status (Column A -> Col 1) -> Set to "Update" or "Active"
      sheet.getRange(foundRow, 1).setValue("Update");

      // 4. Merge FD Codes (Column K onwards -> Col 11+)
      // Read existing codes from K to Z?
      // Let's assume codes start at K (Col 11).
      // We need to find the next empty cell in that row or check duplicates.

      // Strategy: Get strict list of codes currently in row
      const maxCols = sheet.getLastColumn();
      const codeRange = sheet.getRange(foundRow, 11, 1, maxCols - 10); // K is 11.
      const existingCodeValues = codeRange
        .getValues()[0]
        .filter((c) => c !== "");

      let allCodes = new Set(existingCodeValues.map(String));
      incomingCodes.forEach((c) => allCodes.add(c));

      // Write back all unique codes starting at K
      const uniqueCodes = Array.from(allCodes);

      // Clear old codes region first to be safe
      if (maxCols >= 11) {
        sheet.getRange(foundRow, 11, 1, maxCols - 10).clearContent();
      }

      // Write new set
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
        incidentId,
        data.state || "",
        data.county || "",
        data.city || "",
        data.address || "",
        data.incidentType || "",
        newIncidentText,
        data.originalBody || "",
      ];

      // Add FD Codes to end of array
      incomingCodes.forEach((code) => row.push(code));

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
