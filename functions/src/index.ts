/**
 * AlertsToSheets Cloud Functions
 * 
 * Milestone 1: Ingestion endpoint with idempotent writes
 * Phase 3: CRM Foundation - Alert enrichment and property creation
 * 
 * Functions:
 * - ingest: Accept events from Android client, deduplicate, store in Firestore
 * - deliverEvent: (Future) Trigger on new event, fan-out to endpoints
 * - enrichAlert: (NEW) Enrich alerts with normalized address, link to property
 * - enrichProperty: (NEW) Trigger property enrichment (ATTOM, etc.)
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { getFeatureFlag, initializeFeatureFlags, getAllFeatureFlags } from './utils/featureFlags';

admin.initializeApp();

// ============================================================================
// EXPORT ENRICHMENT FUNCTIONS
// ============================================================================

export { enrichAlert, enrichProperty } from './enrichment';

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
 * Accept alert from client, validate, deduplicate, store in Firestore /alerts collection
 * 
 * Idempotency: If alert with same UUID exists, return 200 (no-op)
 * Security: Requires Firebase Auth token in Authorization header
 * 
 * CRM Integration: Alert is written to /alerts, which triggers enrichAlert function
 * 
 * Request body:
 * {
 *   "uuid": "550e8400-e29b-41d4-a716-446655440000",
 *   "sourceId": "bnn-app",
 *   "payload": "{...}",  // Must contain 'address' or 'text' field with address
 *   "timestamp": "2025-12-23T10:00:00.000Z",
 *   "deviceId": "android-12345",
 *   "appVersion": "2.0.1"
 * }
 * 
 * Response:
 * {
 *   "status": "ok",
 *   "message": "Alert ingested",
 *   "uuid": "550e8400-...",
 *   "isDuplicate": false
 * }
 */
export const ingest = functions.https.onRequest(async (req, res) => {
  // ========================================
  // STEP 0: Check feature flags
  // ========================================
  
  // Check maintenance mode
  const maintenanceMode = await getFeatureFlag('maintenanceMode');
  if (maintenanceMode) {
    res.status(503).json({
      status: 'error',
      message: 'Service temporarily unavailable (maintenance mode)'
    });
    return;
  }
  
  // Check Firestore ingest enabled
  const firestoreIngestEnabled = await getFeatureFlag('firestoreIngest');
  if (!firestoreIngestEnabled) {
    res.status(503).json({
      status: 'error',
      message: 'Firestore ingest temporarily disabled'
    });
    return;
  }
  
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
  
  const alertRef = admin.firestore().collection('alerts').doc(body.uuid);
  const existingAlert = await alertRef.get();
  
  if (existingAlert.exists) {
    // Alert already ingested, return success (idempotent)
    console.log(`[INGEST] Duplicate alert: ${body.uuid} (userId: ${userId})`);
    res.status(200).json({
      status: 'ok',
      message: 'Alert already ingested (duplicate)',
      uuid: body.uuid,
      isDuplicate: true
    } as IngestResponse);
    return;
  }
  
  // ========================================
  // STEP 4: Write to Firestore /alerts
  // ========================================
  
  try {
    // Parse payload to extract address
    const parsedPayload = JSON.parse(body.payload);
    const rawAddress = parsedPayload.address || parsedPayload.location || null;
    
    if (!rawAddress) {
      console.warn(`[INGEST] No address found in payload for alert ${body.uuid}`);
    }
    
    await alertRef.set({
      // Identity
      alertId: body.uuid,
      sourceId: body.sourceId,
      eventTimestamp: timestamp.getTime(),
      ingestedAt: Date.now(),
      
      // Raw Data
      rawAddress: rawAddress || '',
      rawPayload: parsedPayload,
      
      // Derived (enriched by enrichAlert function)
      propertyId: null,
      normalizedAddress: null,
      
      // Geo (enriched by enrichAlert function)
      lat: null,
      lng: null,
      geocodeProvider: null,
      geocodeConfidence: null,
      
      // Metadata
      clientAppVersion: body.appVersion || 'unknown',
      clientDeviceId: body.deviceId || 'unknown',
      clientUserId: userId
    });
    
    console.log(`[INGEST] Alert ingested: ${body.uuid} (userId: ${userId}, sourceId: ${body.sourceId}, hasAddress: ${!!rawAddress})`);
    
    res.status(200).json({
      status: 'ok',
      message: 'Alert ingested successfully',
      uuid: body.uuid,
      isDuplicate: false
    } as IngestResponse);
    
  } catch (error) {
    console.error('[INGEST] Firestore write failed:', error);
    res.status(500).json({
      status: 'error',
      message: 'Internal server error. Failed to write alert.'
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

// ============================================================================
// FEATURE FLAG MANAGEMENT
// ============================================================================

/**
 * POST /initConfig
 * 
 * Initialize feature flags in Firestore with defaults
 * Run once after deployment
 */
export const initConfig = functions.https.onRequest(async (req, res) => {
  try {
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
    await admin.auth().verifyIdToken(idToken);
    
    // Initialize feature flags
    await initializeFeatureFlags();
    
    // Get all flags
    const flags = await getAllFeatureFlags();
    
    res.status(200).json({
      status: 'ok',
      message: 'Feature flags initialized',
      flags
    });
    
  } catch (error) {
    console.error('[INIT_CONFIG] Error:', error);
    res.status(500).json({
      status: 'error',
      message: error instanceof Error ? error.message : 'Internal server error'
    });
  }
});

/**
 * GET /config
 * 
 * Get all feature flags
 */
export const getConfig = functions.https.onRequest(async (req, res) => {
  try {
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
    await admin.auth().verifyIdToken(idToken);
    
    // Get all flags
    const flags = await getAllFeatureFlags();
    
    res.status(200).json({
      status: 'ok',
      flags
    });
    
  } catch (error) {
    console.error('[GET_CONFIG] Error:', error);
    res.status(500).json({
      status: 'error',
      message: error instanceof Error ? error.message : 'Internal server error'
    });
  }
});

