package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.person.AktivitetsloggObserver

internal class V80InntektskildePåVedtaksperiode : JsonMigration(version = 80) {
    override val description: String = "Legger på inntektskilde på vedtaksperioder"

    override fun doMigration(
        jsonNode: ObjectNode,
        meldingerSupplier: MeldingerSupplier,
        observer: AktivitetsloggObserver
    ) {
        jsonNode["arbeidsgivere"]
            .flatMap { it["vedtaksperioder"] + it["forkastede"].map { forkastet -> forkastet["vedtaksperiode"] } }
            .forEach {
                it as ObjectNode
                it.put("inntektskilde", "EN_ARBEIDSGIVER")
            }
    }
}
