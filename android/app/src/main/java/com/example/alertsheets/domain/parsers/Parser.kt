package com.example.alertsheets.domain.parsers

import com.example.alertsheets.domain.models.ParsedData
import com.example.alertsheets.domain.models.RawNotification
import com.example.alertsheets.domain.models.Source

/**
 * Base interface for all parsers
 * Each parser extracts structured data from raw notifications
 */
interface Parser {
    /**
     * Unique parser ID (matches Source.parserId)
     */
    val id: String
    
    /**
     * Human-readable parser name
     */
    val name: String
    
    /**
     * Check if this parser can handle the notification
     */
    fun canParse(source: Source, raw: RawNotification): Boolean
    
    /**
     * Parse raw notification into structured data
     */
    fun parse(raw: RawNotification): ParsedData?
}

/**
 * Parser registry
 * Manages all available parsers and finds the right one for a source
 */
object ParserRegistry {
    private val parsers = mutableMapOf<String, Parser>()
    
    /**
     * Register a parser
     */
    fun register(parser: Parser) {
        parsers[parser.id] = parser
    }
    
    /**
     * Get parser by ID
     */
    fun get(parserId: String): Parser? {
        return parsers[parserId]
    }
    
    /**
     * Initialize with all parsers
     */
    fun init() {
        register(BnnParser())
        register(GenericAppParser())
        register(SmsParser())
    }
}

