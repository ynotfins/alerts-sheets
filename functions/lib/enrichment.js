"use strict";
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
exports.enrichProperty = exports.enrichAlert = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
const addressUtils_1 = require("./utils/addressUtils");
const featureFlags_1 = require("./utils/featureFlags");
const db = admin.firestore();
/**
 * Alert Enrichment Cloud Function
 *
 * Triggered when a new alert is created in /alerts collection
 *
 * Responsibilities:
 * 1. Extract address from alert payload
 * 2. Normalize address
 * 3. Generate deterministic property ID
 * 4. Create or update property record
 * 5. Link alert to property
 * 6. Optional: Trigger geocoding (if implemented)
 *
 * CRITICAL: This function runs ASYNC and NEVER blocks client writes
 */
exports.enrichAlert = functions.firestore
    .document('alerts/{alertId}')
    .onCreate(async (snap, context) => {
    const alertId = context.params.alertId;
    const alert = snap.data();
    console.log(`[enrichAlert] Processing alert ${alertId} from source ${alert.sourceId}`);
    // CHECK FEATURE FLAG: Alert enrichment enabled?
    const enrichmentEnabled = await (0, featureFlags_1.getFeatureFlag)('alertEnrichment');
    if (!enrichmentEnabled) {
        console.log(`[enrichAlert] Alert enrichment DISABLED by feature flag, skipping ${alertId}`);
        await snap.ref.update({
            processingNote: 'Enrichment disabled by feature flag',
            processedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        return null;
    }
    try {
        // STEP 1: Extract address from payload
        const rawAddress = alert.rawAddress || (0, addressUtils_1.extractAddressFromPayload)(alert.rawPayload, alert.sourceId);
        if (!rawAddress) {
            console.warn(`[enrichAlert] No address found in alert ${alertId}, skipping enrichment`);
            await snap.ref.update({
                processingNote: 'No address found in payload',
                processedAt: admin.firestore.FieldValue.serverTimestamp()
            });
            return null;
        }
        console.log(`[enrichAlert] Extracted address: ${rawAddress}`);
        // STEP 2: Normalize address
        const normalized = (0, addressUtils_1.normalizeAddress)(rawAddress);
        if (!normalized) {
            console.warn(`[enrichAlert] Address normalization failed for alert ${alertId}: ${rawAddress}`);
            await snap.ref.update({
                processingNote: 'Address normalization failed',
                processedAt: admin.firestore.FieldValue.serverTimestamp()
            });
            return null;
        }
        console.log(`[enrichAlert] Normalized address: ${normalized.normalized}`);
        // STEP 3: Generate deterministic property ID
        const propertyId = (0, addressUtils_1.generatePropertyId)(normalized.normalized);
        console.log(`[enrichAlert] Generated property ID: ${propertyId}`);
        // STEP 4: Geocode address (optional, may be null if not implemented)
        let geocode = null;
        const geocodingEnabled = await (0, featureFlags_1.getFeatureFlag)('geocoding');
        if (geocodingEnabled) {
            try {
                geocode = await (0, addressUtils_1.geocodeAddress)(normalized.normalized);
                if (geocode) {
                    console.log(`[enrichAlert] Geocoded: lat=${geocode.lat}, lng=${geocode.lng}, confidence=${geocode.confidence}`);
                }
            }
            catch (geocodeError) {
                console.warn(`[enrichAlert] Geocoding failed (non-fatal): ${geocodeError}`);
            }
        }
        else {
            console.log(`[enrichAlert] Geocoding DISABLED by feature flag`);
        }
        // STEP 5: Create or update property record
        const propertyRef = db.collection('properties').doc(propertyId);
        const propertySnap = await propertyRef.get();
        if (propertySnap.exists) {
            // Property exists - update timestamps and increment alert count
            console.log(`[enrichAlert] Property ${propertyId} exists, updating`);
            await propertyRef.update({
                lastAlertAt: alert.eventTimestamp,
                totalAlerts: admin.firestore.FieldValue.increment(1),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        }
        else {
            // New property - create full record
            console.log(`[enrichAlert] Creating new property ${propertyId}`);
            await propertyRef.set({
                // Identity
                propertyId,
                normalizedAddress: normalized.normalized,
                // Address Components
                streetNumber: normalized.streetNumber,
                streetName: normalized.streetName,
                city: normalized.city,
                state: normalized.state,
                zipCode: normalized.zipCode,
                county: normalized.county || null,
                // Geo (from geocoder or null)
                lat: (geocode === null || geocode === void 0 ? void 0 : geocode.lat) || 0,
                lng: (geocode === null || geocode === void 0 ? void 0 : geocode.lng) || 0,
                geocodeProvider: (geocode === null || geocode === void 0 ? void 0 : geocode.provider) || null,
                geocodeConfidence: (geocode === null || geocode === void 0 ? void 0 : geocode.confidence) || null,
                placeId: (geocode === null || geocode === void 0 ? void 0 : geocode.placeId) || null,
                // External IDs (populated by enrichment later)
                parcelId: null,
                attomId: null,
                coreLogicId: null,
                // Property Details (populated by enrichment later)
                propertyType: null,
                bedrooms: null,
                bathrooms: null,
                squareFeet: null,
                yearBuilt: null,
                lotSizeAcres: null,
                assessedValue: null,
                lastSaleDate: null,
                lastSalePrice: null,
                // CRM Metadata
                firstAlertAt: alert.eventTimestamp,
                lastAlertAt: alert.eventTimestamp,
                totalAlerts: 1,
                // Status
                enrichmentStatus: 'pending',
                enrichedAt: null,
                // Timestamps
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        }
        // STEP 6: Update alert with property linkage
        await snap.ref.update({
            propertyId,
            normalizedAddress: normalized.normalized,
            lat: (geocode === null || geocode === void 0 ? void 0 : geocode.lat) || null,
            lng: (geocode === null || geocode === void 0 ? void 0 : geocode.lng) || null,
            geocodeProvider: (geocode === null || geocode === void 0 ? void 0 : geocode.provider) || null,
            geocodeConfidence: (geocode === null || geocode === void 0 ? void 0 : geocode.confidence) || null,
            processedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        console.log(`[enrichAlert] Alert ${alertId} successfully enriched and linked to property ${propertyId}`);
        // STEP 7: Trigger further enrichment (ATTOM, owner lookup, etc.)
        // TODO: Implement provider-specific enrichment triggers
        // await triggerOwnerEnrichment(propertyId);
        return null;
    }
    catch (error) {
        console.error(`[enrichAlert] Error processing alert ${alertId}:`, error);
        // Log error on alert document (non-fatal)
        await snap.ref.update({
            processingError: error instanceof Error ? error.message : 'Unknown error',
            processingErrorAt: admin.firestore.FieldValue.serverTimestamp()
        }).catch(updateError => {
            console.error(`[enrichAlert] Failed to log error on alert ${alertId}:`, updateError);
        });
        // Don't throw - we don't want to retry indefinitely
        return null;
    }
});
/**
 * Property Enrichment Trigger (placeholder)
 *
 * Triggered when a property's enrichmentStatus changes to 'pending'
 * or when manually requested
 *
 * Responsibilities:
 * - Call ATTOM API for owner info
 * - Create Person records
 * - Create Contact records (phones/emails)
 * - Create Household linkage
 * - Update property enrichmentStatus to 'complete'
 */
exports.enrichProperty = functions.firestore
    .document('properties/{propertyId}')
    .onCreate(async (snap, context) => {
    const propertyId = context.params.propertyId;
    const property = snap.data();
    console.log(`[enrichProperty] New property created: ${propertyId}, status=${property.enrichmentStatus}`);
    // CHECK FEATURE FLAG: Property enrichment enabled?
    const enrichmentEnabled = await (0, featureFlags_1.getFeatureFlag)('propertyEnrichment');
    if (!enrichmentEnabled) {
        console.log(`[enrichProperty] Property enrichment DISABLED by feature flag, skipping ${propertyId}`);
        return null;
    }
    // TODO: Implement ATTOM API integration
    // For now, just log that enrichment is needed
    if (property.enrichmentStatus === 'pending') {
        console.log(`[enrichProperty] Property ${propertyId} needs enrichment (ATTOM API not implemented yet)`);
        // Create placeholder enrichment run
        const runId = `${new Date().toISOString()}_placeholder`;
        await snap.ref.collection('enrichments').doc(runId).set({
            runId,
            propertyId,
            provider: 'none',
            requestedAt: Date.now(),
            status: 'failed',
            errorMessage: 'Enrichment provider not configured',
            completedAt: Date.now()
        });
    }
    return null;
});
//# sourceMappingURL=enrichment.js.map