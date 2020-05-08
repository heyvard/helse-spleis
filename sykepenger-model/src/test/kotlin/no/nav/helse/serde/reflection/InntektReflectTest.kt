package no.nav.helse.serde.reflection

import no.nav.helse.person.Inntekthistorikk
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class InntektReflectTest {
    private val hendelseId = UUID.randomUUID()

    @Test
    fun `mapper Inntekt til map`() {
        val map = InntektReflect(inntekt).toMap()

        assertEquals(3, map.size)
        assertEquals(1.januar, map["fom"])
        assertEquals(hendelseId, map["hendelseId"])
        assertEquals(1000.0.toBigDecimal(), map["beløp"])
    }

    internal val inntekt =
        Inntekthistorikk.Inntekt(1.januar, hendelseId, 1000.0.toBigDecimal())
}
