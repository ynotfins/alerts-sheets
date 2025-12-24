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
exports.getFeatureFlag = getFeatureFlag;
exports.setFeatureFlag = setFeatureFlag;
exports.initializeFeatureFlags = initializeFeatureFlags;
exports.getAllFeatureFlags = getAllFeatureFlags;
const admin = __importStar(require("firebase-admin"));
/**
 * Feature Flag / Kill Switch System
 *
 * Stored in Firestore /config collection
 * Can be toggled without redeployment
 *
 * Usage:
 *   const isEnabled = await getFeatureFlag('firestoreIngest');
 *   if (!isEnabled) {
 *     console.log('Firestore ingest disabled, skipping');
 *     return;
 *   }
 */
const CONFIG_COLLECTION = 'config';
// Lazy-initialize Firestore (only when first function is called)
function getDb() {
    return admin.firestore();
}
// Default feature flags (used if Firestore doc doesn't exist)
const DEFAULT_FLAGS = {
    // Core Features
    firestoreIngest: true, // Master switch for /ingest endpoint
    alertEnrichment: true, // Master switch for enrichAlert function
    propertyEnrichment: false, // Switch for ATTOM/owner lookups (not implemented yet)
    geocoding: false, // Switch for geocoding API calls (not implemented yet)
    // Delivery
    appsScriptDelivery: true, // Existing Sheets delivery (NEVER disable in prod)
    firestoreFanout: false, // Future: Firestore-based fanout delivery
    // Safety
    maxAlertsPerMinute: 100, // Rate limit for /ingest
    maxEnrichmentsPerHour: 500, // Rate limit for enrichment API calls
    // Maintenance
    maintenanceMode: false // Emergency kill switch (returns 503 for all endpoints)
};
/**
 * Get feature flag value from Firestore
 * Falls back to default if doc doesn't exist
 */
async function getFeatureFlag(flagName) {
    try {
        const db = getDb();
        const doc = await db.collection(CONFIG_COLLECTION).doc('featureFlags').get();
        if (!doc.exists) {
            console.warn(`[CONFIG] Feature flags doc doesn't exist, using default for ${flagName}`);
            return DEFAULT_FLAGS[flagName];
        }
        const data = doc.data();
        if (data && flagName in data) {
            return data[flagName];
        }
        console.warn(`[CONFIG] Flag ${flagName} not found in Firestore, using default`);
        return DEFAULT_FLAGS[flagName];
    }
    catch (error) {
        console.error(`[CONFIG] Error reading feature flag ${flagName}:`, error);
        return DEFAULT_FLAGS[flagName];
    }
}
/**
 * Set feature flag value in Firestore
 * Admin/server-only operation
 */
async function setFeatureFlag(flagName, value) {
    try {
        const db = getDb();
        await db.collection(CONFIG_COLLECTION).doc('featureFlags').set({ [flagName]: value }, { merge: true });
        console.log(`[CONFIG] Feature flag ${flagName} set to ${value}`);
    }
    catch (error) {
        console.error(`[CONFIG] Error setting feature flag ${flagName}:`, error);
        throw error;
    }
}
/**
 * Initialize feature flags in Firestore if they don't exist
 * Run this once during deployment
 */
async function initializeFeatureFlags() {
    try {
        const db = getDb();
        const doc = await db.collection(CONFIG_COLLECTION).doc('featureFlags').get();
        if (!doc.exists) {
            await db.collection(CONFIG_COLLECTION).doc('featureFlags').set(DEFAULT_FLAGS);
            console.log('[CONFIG] Feature flags initialized with defaults');
        }
        else {
            console.log('[CONFIG] Feature flags already exist');
        }
    }
    catch (error) {
        console.error('[CONFIG] Error initializing feature flags:', error);
        throw error;
    }
}
/**
 * Get all feature flags as object
 */
async function getAllFeatureFlags() {
    try {
        const db = getDb();
        const doc = await db.collection(CONFIG_COLLECTION).doc('featureFlags').get();
        if (!doc.exists) {
            return DEFAULT_FLAGS;
        }
        return doc.data();
    }
    catch (error) {
        console.error('[CONFIG] Error reading all feature flags:', error);
        return DEFAULT_FLAGS;
    }
}
//# sourceMappingURL=featureFlags.js.map