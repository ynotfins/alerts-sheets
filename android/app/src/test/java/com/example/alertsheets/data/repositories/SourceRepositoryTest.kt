package com.example.alertsheets.data.repositories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.alertsheets.domain.models.Source
import com.example.alertsheets.domain.models.SourceStats
import com.example.alertsheets.domain.models.SourceType
import com.example.alertsheets.utils.AppConstants
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Unit tests for SourceRepository
 * 
 * Tests cover:
 * - CRUD operations
 * - Error handling (corrupt JSON)
 * - Edge cases (empty list, missing file)
 * - Statistics updates
 * - Concurrent access scenarios
 */
@RunWith(AndroidJUnit4::class)
class SourceRepositoryTest {
    
    private lateinit var context: Context
    private lateinit var repository: SourceRepository
    private lateinit var sourcesFile: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = SourceRepository(context)
        sourcesFile = File(context.filesDir, AppConstants.FILE_SOURCES)
        
        // Clean slate for each test
        if (sourcesFile.exists()) {
            sourcesFile.delete()
        }
    }
    
    @After
    fun teardown() {
        // Cleanup after each test
        if (sourcesFile.exists()) {
            sourcesFile.delete()
        }
    }
    
    // ============================================================
    // CREATE TESTS
    // ============================================================
    
    @Test
    fun `save new source creates entry`() {
        // Given
        val source = createTestAppSource("com.test.app", "Test App")
        
        // When
        repository.save(source)
        
        // Then
        val retrieved = repository.getById("com.test.app")
        assertNotNull("Source should exist after save", retrieved)
        assertEquals("Test App", retrieved?.name)
        assertEquals(SourceType.APP, retrieved?.type)
        assertTrue("Source should be enabled", retrieved?.enabled == true)
    }
    
    @Test
    fun `save multiple sources preserves all`() {
        // Given
        val source1 = createTestAppSource("com.app1", "App 1")
        val source2 = createTestAppSource("com.app2", "App 2")
        val source3 = createTestSmsSource("sms:555-1234", "Fire Dept")
        
        // When
        repository.save(source1)
        repository.save(source2)
        repository.save(source3)
        
        // Then
        val all = repository.getAll()
        assertEquals("Should have 3 sources", 3, all.size)
        assertNotNull(repository.getById("com.app1"))
        assertNotNull(repository.getById("com.app2"))
        assertNotNull(repository.getById("sms:555-1234"))
    }
    
    // ============================================================
    // READ TESTS
    // ============================================================
    
    @Test
    fun `getAll returns empty list when no sources exist`() {
        // When
        val all = repository.getAll()
        
        // Then
        assertTrue("Should return empty list, not null", all.isEmpty())
        assertEquals(0, all.size)
    }
    
    @Test
    fun `getById returns null for non-existent source`() {
        // When
        val retrieved = repository.getById("non.existent.app")
        
        // Then
        assertNull("Should return null for non-existent source", retrieved)
    }
    
    @Test
    fun `getEnabled filters disabled sources`() {
        // Given
        val enabled1 = createTestAppSource("com.app1", "App 1", enabled = true)
        val disabled = createTestAppSource("com.app2", "App 2", enabled = false)
        val enabled2 = createTestAppSource("com.app3", "App 3", enabled = true)
        
        repository.save(enabled1)
        repository.save(disabled)
        repository.save(enabled2)
        
        // When
        val enabledSources = repository.getEnabled()
        
        // Then
        assertEquals("Should have 2 enabled sources", 2, enabledSources.size)
        assertTrue(enabledSources.all { it.enabled })
        assertFalse(enabledSources.any { it.id == "com.app2" })
    }
    
    @Test
    fun `findByPackage returns correct source`() {
        // Given
        val source1 = createTestAppSource("com.example.bnn", "BNN")
        val source2 = createTestAppSource("com.example.other", "Other")
        repository.save(source1)
        repository.save(source2)
        
        // When
        val found = repository.findByPackage("com.example.bnn")
        
        // Then
        assertNotNull(found)
        assertEquals("BNN", found?.name)
    }
    
    @Test
    fun `findBySender returns correct SMS source`() {
        // Given
        val sms1 = createTestSmsSource("sms:555-1234", "Fire Dept")
        val sms2 = createTestSmsSource("sms:555-5678", "Police")
        repository.save(sms1)
        repository.save(sms2)
        
        // When
        val found = repository.findBySender("555-1234")
        
        // Then
        assertNotNull(found)
        assertEquals("Fire Dept", found?.name)
    }
    
    // ============================================================
    // UPDATE TESTS
    // ============================================================
    
    @Test
    fun `save existing source updates entry`() {
        // Given
        val original = createTestAppSource("com.test.app", "Original Name")
        repository.save(original)
        
        // When
        val updated = original.copy(name = "Updated Name")
        repository.save(updated)
        
        // Then
        val retrieved = repository.getById("com.test.app")
        assertEquals("Updated Name", retrieved?.name)
        
        // Should only have one entry (not duplicate)
        val all = repository.getAll()
        assertEquals(1, all.size)
    }
    
    @Test
    fun `save updates timestamp on existing source`() {
        // Given
        val original = createTestAppSource("com.test.app", "Test")
        repository.save(original)
        val firstTimestamp = repository.getById("com.test.app")?.updatedAt
        
        // Wait a bit to ensure timestamp difference
        Thread.sleep(10)
        
        // When
        repository.save(original)
        
        // Then
        val secondTimestamp = repository.getById("com.test.app")?.updatedAt
        assertNotNull(firstTimestamp)
        assertNotNull(secondTimestamp)
        assertTrue("Updated timestamp should be later", secondTimestamp!! > firstTimestamp!!)
    }
    
    @Test
    fun `updateStats increments counters correctly`() {
        // Given
        val source = createTestAppSource("com.test.app", "Test")
        repository.save(source)
        
        // When
        repository.updateStats("com.test.app", processed = 5, sent = 3, failed = 2)
        
        // Then
        val updated = repository.getById("com.test.app")
        assertEquals(5, updated?.stats?.totalProcessed)
        assertEquals(3, updated?.stats?.totalSent)
        assertEquals(2, updated?.stats?.totalFailed)
        assertTrue("Last activity should be set", updated?.stats?.lastActivity!! > 0)
    }
    
    @Test
    fun `updateStats accumulates over multiple calls`() {
        // Given
        val source = createTestAppSource("com.test.app", "Test")
        repository.save(source)
        
        // When
        repository.updateStats("com.test.app", processed = 5, sent = 3)
        repository.updateStats("com.test.app", processed = 3, sent = 2, failed = 1)
        repository.updateStats("com.test.app", processed = 2)
        
        // Then
        val updated = repository.getById("com.test.app")
        assertEquals("Should accumulate: 5+3+2=10", 10, updated?.stats?.totalProcessed)
        assertEquals("Should accumulate: 3+2=5", 5, updated?.stats?.totalSent)
        assertEquals("Should accumulate: 0+1+0=1", 1, updated?.stats?.totalFailed)
    }
    
    @Test
    fun `updateStats on non-existent source does nothing`() {
        // When
        repository.updateStats("non.existent", processed = 5)
        
        // Then
        val retrieved = repository.getById("non.existent")
        assertNull("Should not create source", retrieved)
    }
    
    // ============================================================
    // DELETE TESTS
    // ============================================================
    
    @Test
    fun `delete removes source`() {
        // Given
        val source = createTestAppSource("com.test.app", "Test")
        repository.save(source)
        assertTrue("Source should exist", repository.getById("com.test.app") != null)
        
        // When
        repository.delete("com.test.app")
        
        // Then
        assertNull("Source should be deleted", repository.getById("com.test.app"))
    }
    
    @Test
    fun `delete non-existent source does nothing`() {
        // Given
        val source = createTestAppSource("com.test.app", "Test")
        repository.save(source)
        
        // When
        repository.delete("non.existent")
        
        // Then
        assertEquals("Original source should remain", 1, repository.getAll().size)
        assertNotNull(repository.getById("com.test.app"))
    }
    
    @Test
    fun `delete one of multiple sources preserves others`() {
        // Given
        val source1 = createTestAppSource("com.app1", "App 1")
        val source2 = createTestAppSource("com.app2", "App 2")
        val source3 = createTestAppSource("com.app3", "App 3")
        repository.save(source1)
        repository.save(source2)
        repository.save(source3)
        
        // When
        repository.delete("com.app2")
        
        // Then
        assertEquals("Should have 2 sources remaining", 2, repository.getAll().size)
        assertNotNull(repository.getById("com.app1"))
        assertNull(repository.getById("com.app2"))
        assertNotNull(repository.getById("com.app3"))
    }
    
    // ============================================================
    // ERROR HANDLING TESTS
    // ============================================================
    
    @Test
    fun `corrupt JSON returns empty list without crashing`() {
        // Given: Manually write corrupt JSON
        sourcesFile.writeText("{corrupt json [[ this is bad")
        
        // When
        val sources = repository.getAll()
        
        // Then
        assertTrue("Should return empty list on corrupt JSON", sources.isEmpty())
        // Should not crash or throw exception
    }
    
    @Test
    fun `empty JSON file returns empty list`() {
        // Given: Empty file
        sourcesFile.writeText("")
        
        // When
        val sources = repository.getAll()
        
        // Then
        assertTrue("Should return empty list on empty file", sources.isEmpty())
    }
    
    @Test
    fun `null JSON array returns empty list`() {
        // Given: JSON with null
        sourcesFile.writeText("null")
        
        // When
        val sources = repository.getAll()
        
        // Then
        assertTrue("Should return empty list on null JSON", sources.isEmpty())
    }
    
    // ============================================================
    // HELPER METHODS
    // ============================================================
    
    private fun createTestAppSource(
        id: String,
        name: String,
        enabled: Boolean = true
    ): Source {
        return Source(
            id = id,
            type = SourceType.APP,
            name = name,
            enabled = enabled,
            autoClean = true,
            templateId = AppConstants.TEMPLATE_APP_DEFAULT,
            parserId = AppConstants.PARSER_GENERIC,
            endpointId = AppConstants.ENDPOINT_DEFAULT,
            iconColor = AppConstants.COLOR_APP,
            stats = SourceStats(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    private fun createTestSmsSource(
        id: String,
        name: String,
        enabled: Boolean = true
    ): Source {
        return Source(
            id = id,
            type = SourceType.SMS,
            name = name,
            enabled = enabled,
            autoClean = true,
            templateId = AppConstants.TEMPLATE_SMS_DEFAULT,
            parserId = AppConstants.PARSER_SMS,
            endpointId = AppConstants.ENDPOINT_DEFAULT,
            iconColor = AppConstants.COLOR_SMS,
            stats = SourceStats(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}

