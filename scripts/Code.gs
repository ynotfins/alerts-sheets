function doPost(e) {
  try {
    const sheetIs = "1yKUvWtG7wBdjBhLpmM78vWhUoxiKMIryczIUt_Z2wOE"; // User provided ID
    const sheet = SpreadsheetApp.openById(sheetIs).getSheets()[0];
    
    // Parse the JSON data sent from the Android app
    const data = JSON.parse(e.postData.contents);
    
    // Expected fields from the user request
    // New/Update, Timestamp, Incident ID, State, County, City, Address, Incident type, Incident, Original Full Notification, FD Codes...
    
    // Prepare the row data
    const row = [
      data.status || "",           // New/Update
      data.timestamp || "",        // Timestamp
      data.incidentId || "",       // Incident ID
      data.state || "",            // State
      data.county || "",           // County
      data.city || "",             // City
      data.address || "",          // Address
      data.incidentType || "",     // Incident type
      data.incidentDetails || "",   // Incident (Description)
      data.originalBody || ""      // Original Full Notification
    ];
    
    // Add FD Codes (variable number of columns)
    // The user showed "FD Codes" repeated many times. We'll append all we find.
    if (data.fdCodes && Array.isArray(data.fdCodes)) {
      data.fdCodes.forEach(code => row.push(code));
    }
    
    // Append to the sheet
    sheet.appendRow(row);
    
    return ContentService.createTextOutput(JSON.stringify({ "result": "success" })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ "result": "error", "error": err.toString() })).setMimeType(ContentService.MimeType.JSON);
  }
}
