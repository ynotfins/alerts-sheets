"use strict";
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
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.getConfig = exports.initConfig = exports.health = exports.deliverEvent = exports.ingest = exports.enrichProperty = exports.enrichAlert = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
const featureFlags_1 = require("./utils/featureFlags");
admin.initializeApp();
// ============================================================================
// EXPORT ENRICHMENT FUNCTIONS
// ============================================================================
var enrichment_1 = require("./enrichment");
Object.defineProperty(exports, "enrichAlert", { enumerable: true, get: function () { return enrichment_1.enrichAlert; } });
Object.defineProperty(exports, "enrichProperty", { enumerable: true, get: function () { return enrichment_1.enrichProperty; } });
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
exports.ingest = functions.https.onRequest(async (req, res) => {
    // ========================================
    // STEP 0: Check feature flags
    // ========================================
    // Check maintenance mode
    const maintenanceMode = await (0, featureFlags_1.getFeatureFlag)('maintenanceMode');
    if (maintenanceMode) {
        res.status(503).json({
            status: 'error',
            message: 'Service temporarily unavailable (maintenance mode)'
        });
        return;
    }
    // Check Firestore ingest enabled
    const firestoreIngestEnabled = await (0, featureFlags_1.getFeatureFlag)('firestoreIngest');
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
    }
    catch (error) {
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
    const body = req.body;
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
    }
    catch (error) {
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
        });
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
        });
    }
    catch (error) {
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
exports.deliverEvent = functions.firestore
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
exports.health = functions.https.onRequest((req, res) => {
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
exports.initConfig = functions.https.onRequest(async (req, res) => {
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
        await (0, featureFlags_1.initializeFeatureFlags)();
        // Get all flags
        const flags = await (0, featureFlags_1.getAllFeatureFlags)();
        res.status(200).json({
            status: 'ok',
            message: 'Feature flags initialized',
            flags
        });
    }
    catch (error) {
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
exports.getConfig = functions.https.onRequest(async (req, res) => {
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
        const flags = await (0, featureFlags_1.getAllFeatureFlags)();
        res.status(200).json({
            status: 'ok',
            flags
        });
    }
    catch (error) {
        console.error('[GET_CONFIG] Error:', error);
        res.status(500).json({
            status: 'error',
            message: error instanceof Error ? error.message : 'Internal server error'
        });
    }
});
//# sourceMappingURL=index.js.map