Android to Google Sheets Walkthrough
I have generated a complete solution to send your Android notifications to Google Sheets.

Part 1: Backend Setup (Google Apps Script)
Open your Google Sheet: Link
Go to Extensions > Apps Script.
Copy the content of 
Code.gs
 into the script editor.
Click Deploy > New Deployment.
Select type: Web app.
Description: "Alerts Listener".
Execute as: Me.
Who has access: Anyone (This is crucial so the Android app can post data without complex auth).
Click Deploy.
Copy the Web App URL. You will need this for the Android App.
Part 2: Android App
I have created a complete Android project in d:\github\alerts-sheets\android.

Open this folder in Android Studio.
Build and Run the app on your phone.
Configuration:
Verify the Web App URL is pre-filled (it should match your deployment). Click Save.
Click Grant Notification Permission: Allow access.
IMPORTANT

Android 13+ Users: If "AlertsToSheets" is grayed out or says "Restricted Settings", go to Settings > Apps > AlertsToSheets > (Three Dots) > Allow Restricted Settings.

Click Ignore Battery Opt: Allow the app to stay active in background.
Click Enable Accessibility: Find "AlertsToSheets" in the list (might be under "Installed Apps" or "Downloaded Apps") and enable it.
NOTE

This "Accessibility" listener is the same robust mechanism used by apps like AutoNotification to capture content that might otherwise be missed.

Robustness Features
Accessibility Service: The app now includes 
NotificationAccessibilityService
 which provides a secondary, more powerful layer of notification interception.
Battery Optimization: The app explicitly requests to ignore battery optimizations to prevent Android 15 from killing it.
Code Overview
Parser.kt
: Handles the logic to split the pipe | separated values.
NotificationService.kt
: Standard listener.
NotificationAccessibilityService.kt
: Powerful accessibility-based listener.
NetworkClient.kt
: Sends data to Sheets.