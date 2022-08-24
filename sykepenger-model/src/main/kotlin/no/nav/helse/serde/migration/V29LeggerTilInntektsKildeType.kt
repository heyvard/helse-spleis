package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.person.AktivitetsloggObserver

internal class V29LeggerTilInntektsKildeType : JsonMigration(version = 29) {
    override val description: String = "Legger til type felt i inntektskilden for å kunne differensiere mellom dem."

    override fun doMigration(
        jsonNode: ObjectNode,
        meldingerSupplier: MeldingerSupplier,
        observer: AktivitetsloggObserver
    ) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("inntekter").forEach { inntekt ->
                inntekt as ObjectNode
                inntekt.put("kilde", "INNTEKTSMELDING")
            }
        }
    }
}
