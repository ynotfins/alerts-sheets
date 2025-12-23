"use strict";
/**
 * AlertsToSheets Cloud Functions
 *
 * Milestone 1: Ingestion endpoint with idempotent writes
 *
 * Functions:
 * - ingest: Accept events from Android client, deduplicate, store in Firestore
 * - deliverEvent: (Future) Trigger on new event, fan-out to endpoints
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
exports.health = exports.deliverEvent = exports.ingest = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
admin.initializeApp();
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
exports.ingest = functions.https.onRequest(async (req, res) => {
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
        });
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
        });
    }
    catch (error) {
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
//# sourceMappingURL=index.js.map