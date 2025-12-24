import * as crypto from 'crypto';

/**
 * Address normalization utilities
 * Pure functions - no external API calls
 */

export interface NormalizedAddress {
  normalized: string;
  streetNumber: string;
  streetName: string;
  city: string;
  state: string;
  zipCode: string;
  county?: string;
}

/**
 * Basic address normalization
 * TODO: Integrate USPS API for production-grade normalization
 */
export function normalizeAddress(rawAddress: string): NormalizedAddress | null {
  try {
    // Remove extra whitespace
    let cleaned = rawAddress.trim().replace(/\s+/g, ' ');
    
    // Common abbreviations
    cleaned = cleaned
      .replace(/\bStreet\b/gi, 'St')
      .replace(/\bAvenue\b/gi, 'Ave')
      .replace(/\bBoulevard\b/gi, 'Blvd')
      .replace(/\bRoad\b/gi, 'Rd')
      .replace(/\bDrive\b/gi, 'Dr')
      .replace(/\bLane\b/gi, 'Ln')
      .replace(/\bCourt\b/gi, 'Ct')
      .replace(/\bCircle\b/gi, 'Cir')
      .replace(/\bParkway\b/gi, 'Pkwy')
      .replace(/\bApartment\b/gi, 'Apt')
      .replace(/\bSuite\b/gi, 'Ste')
      .replace(/\bNorth\b/gi, 'N')
      .replace(/\bSouth\b/gi, 'S')
      .replace(/\bEast\b/gi, 'E')
      .replace(/\bWest\b/gi, 'W');
    
    // Parse components (basic regex - USPS API would be better)
    // Format: "123 Main St, Austin, TX 78701"
    const parts = cleaned.split(',').map(p => p.trim());
    
    if (parts.length < 3) {
      console.warn(`Address parse failed (too few parts): ${rawAddress}`);
      return null;
    }
    
    const streetPart = parts[0];
    const city = parts[1];
    const stateZipPart = parts[2];
    
    // Extract state + zip
    const stateZipMatch = stateZipPart.match(/([A-Z]{2})\s*(\d{5}(?:-\d{4})?)/);
    if (!stateZipMatch) {
      console.warn(`State/ZIP parse failed: ${rawAddress}`);
      return null;
    }
    
    const state = stateZipMatch[1];
    const zipCode = stateZipMatch[2].split('-')[0]; // Use 5-digit ZIP only
    
    // Extract street number + name
    const streetMatch = streetPart.match(/^(\d+)\s+(.+)$/);
    if (!streetMatch) {
      console.warn(`Street number parse failed: ${rawAddress}`);
      return null;
    }
    
    const streetNumber = streetMatch[1];
    const streetName = streetMatch[2];
    
    // Build normalized string
    const normalized = `${streetNumber} ${streetName}, ${city}, ${state} ${zipCode}`;
    
    return {
      normalized,
      streetNumber,
      streetName,
      city,
      state,
      zipCode
    };
    
  } catch (error) {
    console.error(`Address normalization error: ${error}`);
    return null;
  }
}

/**
 * Generate deterministic property ID from normalized address
 * Uses SHA-256 hash of canonical form
 */
export function generatePropertyId(normalizedAddress: string): string {
  // Canonical form: lowercase, alphanumeric only
  const canonical = normalizedAddress
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '')
    .trim();
  
  // SHA-256 hash, first 16 chars
  const hash = crypto.createHash('sha256')
    .update(canonical)
    .digest('hex')
    .slice(0, 16);
  
  return hash;
}

/**
 * Geocoding interface (provider-agnostic)
 * TODO: Implement Google Maps / Mapbox / HERE integration
 */
export interface GeocodeResult {
  lat: number;
  lng: number;
  provider: string;
  confidence: 'rooftop' | 'range_interpolated' | 'geometric_center' | 'approximate';
  placeId?: string;
}

/**
 * Placeholder geocoder (returns null - implement with real API)
 */
export async function geocodeAddress(address: string): Promise<GeocodeResult | null> {
  // TODO: Call geocoding API
  // For now, return null to allow testing without API keys
  console.warn(`Geocoding not implemented yet: ${address}`);
  return null;
}

/**
 * Extract address from alert payload
 * Heuristics for BNN Fire alerts and SMS messages
 */
export function extractAddressFromPayload(payload: any, sourceId: string): string | null {
  try {
    // BNN Fire alerts: Look in 'address', 'location', or 'text' field
    if (payload.address) {
      return payload.address;
    }
    
    if (payload.location) {
      return payload.location;
    }
    
    // SMS or generic: Look for address pattern in text
    if (payload.text) {
      // Simple regex for US addresses: "123 Main St, City, ST 12345"
      const addressPattern = /\d+\s+[A-Za-z0-9\s,]+,\s*[A-Za-z\s]+,\s*[A-Z]{2}\s+\d{5}/;
      const match = payload.text.match(addressPattern);
      if (match) {
        return match[0];
      }
    }
    
    // Last resort: check 'title' field
    if (payload.title) {
      const addressPattern = /\d+\s+[A-Za-z0-9\s,]+,\s*[A-Za-z\s]+,\s*[A-Z]{2}\s+\d{5}/;
      const match = payload.title.match(addressPattern);
      if (match) {
        return match[0];
      }
    }
    
    console.warn(`No address found in payload for source ${sourceId}`);
    return null;
    
  } catch (error) {
    console.error(`Address extraction error: ${error}`);
    return null;
  }
}

