package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import no.nav.helse.person.AktivitetsloggObserver

internal class V31LeggerTilInntektendringTidsstempel : JsonMigration(version = 31) {
    override val description: String = "Legger til tidsstempel felt i inntektsendring"

    override fun doMigration(
        jsonNode: ObjectNode,
        meldingerSupplier: MeldingerSupplier,
        observer: AktivitetsloggObserver
    ) {
        val tidsstempel = LocalDate.now().atStartOfDay().toString()
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("inntekter").forEach { inntekt ->
                inntekt as ObjectNode
                inntekt.put("tidsstempel", tidsstempel)
            }
        }
    }
}
