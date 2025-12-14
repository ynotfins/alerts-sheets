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
        if (idValues[i][0].toString().trim() === incidentId) {
          foundRow = i + 2; // +2 because index 0 is row 2
          break;
        }
      }
    }

    if (foundRow !== -1) {
      // --- UPDATE EXISTING ROW ---

      // 1. Update Incident Text (Column H -> Index 8 (Column I))
      // User wants: APPEND " • [Date Time] Message"
      // Wait, user said: "put a space bullet space then the update message".

      const incidentCell = sheet.getRange(foundRow, 9); // Column I
      const currentText = incidentCell.getValue();
      const appendText = ` • ${newIncidentText}`;
      incidentCell.setValue(currentText + appendText);

      // 2. Update Incident Type (Column G -> Index 7 (Column H))
      // User said "Alert type should get added the same way"
      const typeCell = sheet.getRange(foundRow, 8); // Column H
      const currentType = typeCell.getValue();
      const newType = data.incidentType || "";
      if (newType && !currentType.includes(newType)) {
        typeCell.setValue(currentType + ` • ${newType}`);
      }

      // 3. Update Status (Column A -> Col 1) and Timestamp (Column B -> Col 2)
      sheet.getRange(foundRow, 1).setValue("Update");
      sheet.getRange(foundRow, 2).setValue(formattedTime);

      // 4. Merge FD Codes (Column K onwards -> Col 11+)
      const maxCols = sheet.getLastColumn();
      // Only read if there are columns
      let existingCodeValues = [];
      if (maxCols >= 11) {
        const codeRange = sheet.getRange(foundRow, 11, 1, maxCols - 10);
        existingCodeValues = codeRange.getValues()[0].filter((c) => c !== "");
      }

      let allCodes = new Set(existingCodeValues.map(String));
      incomingCodes.forEach((c) => allCodes.add(c));

      // Filter BNN/BNNDESK just in case
      const uniqueCodes = Array.from(allCodes).filter(
        (c) =>
          c.toLowerCase() !== "bnn" &&
          c.toLowerCase() !== "bnndesk" &&
          !c.toLowerCase().includes("bnn")
      );

      // Clear old codes region first
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
