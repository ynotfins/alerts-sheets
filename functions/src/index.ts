/**
 * AlertsToSheets Cloud Functions
 * 
 * Milestone 1: Ingestion endpoint with idempotent writes
 * 
 * Functions:
 * - ingest: Accept events from Android client, deduplicate, store in Firestore
 * - deliverEvent: (Future) Trigger on new event, fan-out to endpoints
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

admin.initializeApp();

// ============================================================================
// TYPES
// ============================================================================

interface IngestRequest {
  uuid: string;
  sourceId: string;
  payload: string;
  timestamp: string;
  deviceId?: string;
  appVersion?: string;
}

interface IngestResponse {
  status: 'ok' | 'error';
  message: string;
  uuid: string;
  isDuplicate?: boolean;
}

// ============================================================================
// INGESTION ENDPOINT
// ============================================================================

/**
 * POST /ingest
 * 
 * Accept event from client, validate, deduplicate, store in Firestore
 * 
 * Idempotency: If event with same UUID exists, return 200 (no-op)
 * Security: Requires Firebase Auth token in Authorization header
 * 
 * Request body:
 * {
 *   "uuid": "550e8400-e29b-41d4-a716-446655440000",
 *   "sourceId": "bnn-app",
 *   "payload": "{...}",
 *   "timestamp": "2025-12-23T10:00:00.000Z",
 *   "deviceId": "android-12345",
 *   "appVersion": "2.0.1"
 * }
 * 
 * Response:
 * {
 *   "status": "ok",
 *   "message": "Event ingested",
 *   "uuid": "550e8400-...",
 *   "isDuplicate": false
 * }
 */
export const ingest = functions.https.onRequest(async (req, res) => {
  // ========================================
  // STEP 1: Validate request
  // ========================================
  
  // Only accept POST
  if (req.method !== 'POST') {
    res.status(405).json({
      status: 'error',
      message: 'Method not allowed. Use POST.'
    });
    return;
  }
  
  // Check authentication
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({
      status: 'error',
      message: 'Unauthorized. Missing or invalid Authorization header.'
    });
    return;
  }
  
  const idToken = authHeader.split('Bearer ')[1];
  let decodedToken;
  
  try {
    decodedToken = await admin.auth().verifyIdToken(idToken);
  } catch (error) {
    console.error('Token verification failed:', error);
    res.status(401).json({
      status: 'error',
      message: 'Unauthorized. Invalid token.'
    });
    return;
  }
  
  const userId = decodedToken.uid;
  
  // ========================================
  // STEP 2: Validate payload
  // ========================================
  
  const body: IngestRequest = req.body;
  
  // Required fields
  if (!body.uuid || !body.sourceId || !body.payload || !body.timestamp) {
    res.status(400).json({
      status: 'error',
      message: 'Missing required fields: uuid, sourceId, payload, timestamp'
    });
    return;
  }
  
  // Validate UUID format (basic check)
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!uuidRegex.test(body.uuid)) {
    res.status(400).json({
      status: 'error',
      message: 'Invalid UUID format'
    });
    return;
  }
  
  // Validate timestamp format (ISO 8601)
  const timestamp = new Date(body.timestamp);
  if (isNaN(timestamp.getTime())) {
    res.status(400).json({
      status: 'error',
      message: 'Invalid timestamp format. Use ISO 8601.'
    });
    return;
  }
  
  // Validate payload is valid JSON
  try {
    JSON.parse(body.payload);
  } catch (error) {
    res.status(400).json({
      status: 'error',
      message: 'Invalid JSON in payload field'
    });
    return;
  }
  
  // ========================================
  // STEP 3: Check for duplicate (idempotency)
  // ========================================
  
  const eventRef = admin.firestore().collection('events').doc(body.uuid);
  const existingEvent = await eventRef.get();
  
  if (existingEvent.exists) {
    // Event already ingested, return success (idempotent)
    console.log(`[INGEST] Duplicate event: ${body.uuid} (userId: ${userId})`);
    res.status(200).json({
      status: 'ok',
      message: 'Event already ingested (duplicate)',
      uuid: body.uuid,
      isDuplicate: true
    } as IngestResponse);
    return;
  }
  
  // ========================================
  // STEP 4: Write to Firestore
  // ========================================
  
  try {
    await eventRef.set({
      // Event data
      uuid: body.uuid,
      sourceId: body.sourceId,
      payload: body.payload,
      timestamp: admin.firestore.Timestamp.fromDate(timestamp),
      
      // Metadata
      userId: userId,
      deviceId: body.deviceId || 'unknown',
      appVersion: body.appVersion || 'unknown',
      
      // Status tracking
      ingestionStatus: 'INGESTED',
      ingestedAt: admin.firestore.FieldValue.serverTimestamp(),
      deliveryStatus: 'PENDING',
      deliveredAt: null,
      
      // Raw data (for debugging)
      raw: null // Will be populated by client in future version
    });
    
    console.log(`[INGEST] Event ingested: ${body.uuid} (userId: ${userId}, sourceId: ${body.sourceId})`);
    
    res.status(200).json({
      status: 'ok',
      message: 'Event ingested successfully',
      uuid: body.uuid,
      isDuplicate: false
    } as IngestResponse);
    
  } catch (error) {
    console.error('[INGEST] Firestore write failed:', error);
    res.status(500).json({
      status: 'error',
      message: 'Internal server error. Failed to write event.'
    });
  }
});

// ============================================================================
// DELIVERY TRIGGER (Placeholder for Milestone 1 completion)
// ============================================================================

/**
 * Firestore trigger: On new event created, trigger delivery
 * 
 * TODO (Milestone 1 completion):
 * - Read fanout config for sourceId
 * - Deliver to all configured endpoints
 * - Write delivery receipts
 * - Update deliveryStatus
 */
export const deliverEvent = functions.firestore
  .document('events/{eventId}')
  .onCreate(async (snap, context) => {
    const event = snap.data();
    const eventId = context.params.eventId;
    
    console.log(`[DELIVER] Triggered for event: ${eventId} (sourceId: ${event.sourceId})`);
    
    // TODO: Implement fan-out delivery
    // For now, just log
    console.log(`[DELIVER] Delivery not implemented yet (Milestone 1 Phase 2)`);
    
    return null;
  });

// ============================================================================
// HEALTH CHECK
// ============================================================================

/**
 * GET /health
 * 
 * Simple health check endpoint
 */
export const health = functions.https.onRequest((req, res) => {
  res.status(200).json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    service: 'AlertsToSheets Cloud Functions',
    version: '1.0.0-milestone1'
  });
});

