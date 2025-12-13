package com.example.alertsheets

import org.junit.Test
import org.junit.Assert.*

class ParserTest {

    @Test
    fun testUpdateNotification() {
        val input = """
            Update
            9/15/25
            U/D NJ| Bergen| Rutherford| Car Vs Building| 510 Union Ave| CMD reports no structural damage.| <C> BNN | BNNDESK/njvx6/nj7ue | #1825178
        """.trimIndent()

        val result = Parser.parse(input)
        assertNotNull(result)
        assertEquals("Update", result?.status)
        assertEquals("9/15/25", result?.timestamp)
        assertEquals("NJ", result?.state) // We stripped U/D
        assertEquals("Bergen", result?.county)
        assertEquals("Rutherford", result?.city)
        assertEquals("Car Vs Building", result?.incidentType)
        assertEquals("510 Union Ave", result?.address)
        assertEquals("CMD reports no structural damage.", result?.incidentDetails)
        assertEquals("#1825178", result?.incidentId)
        
        assertEquals(3, result?.fdCodes?.size)
        assertEquals("BNNDESK", result?.fdCodes?.get(0))
        assertEquals("njvx6", result?.fdCodes?.get(1))
    }
}
