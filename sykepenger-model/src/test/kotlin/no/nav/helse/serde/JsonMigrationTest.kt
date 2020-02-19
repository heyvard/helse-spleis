package no.nav.helse.serde

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.serde.JsonMigration.Companion.skjemaVersjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class JsonMigrationTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    internal fun `kan migrere JSON uten skjemaversjon`() {
        val version = 1
        val expectedField = "field_1"
        val expectedValue = "value"

        val migratedJson = objectMapper.readTree("{}")
            .migrate(AddFieldMigration(version, expectedField, expectedValue))

        assertEquals(version, skjemaVersjon(migratedJson))
        assertEquals(expectedValue, migratedJson[expectedField].textValue())
    }

    @Test
    internal fun `kan migrere flere ganger`() {
        val version1 = 1
        val version2 = 2
        val field1 = "field_1"
        val value1 = "value1"
        val field2 = "field_2"
        val value2 = "value2"

        val migratedJson = objectMapper.readTree("{}")
            .migrate(AddFieldMigration(version1, field1, value1))
            .migrate(AddFieldMigration(version2, field2, value2))

        assertEquals(version2, skjemaVersjon(migratedJson))
        assertEquals(value1, migratedJson[field1].textValue())
        assertEquals(value2, migratedJson[field2].textValue())
    }

    @Test
    internal fun `kan ikke migrere til mindre versjoner`() {
        val version1 = 1
        val version2 = 2
        val field1 = "field_1"
        val value1 = "value1"
        val field2 = "field_2"
        val value2 = "value2"

        val migratedJson = objectMapper.readTree("{}")
            .migrate(AddFieldMigration(version2, field2, value2))
            .migrate(AddFieldMigration(version1, field1, value1))

        assertEquals(version2, skjemaVersjon(migratedJson))
        assertFalse(migratedJson.has(field1))
        assertEquals(value2, migratedJson[field2].textValue())
    }

    private fun JsonNode.migrate(migration: JsonMigration) = apply {
        migration.migrate(this)
    }

    private class AddFieldMigration(version: Int, private val field: String, private val value: String) :
        JsonMigration(version) {
        override fun doMigration(jsonNode: ObjectNode) {
            jsonNode.put(field, value)
        }
    }
}
