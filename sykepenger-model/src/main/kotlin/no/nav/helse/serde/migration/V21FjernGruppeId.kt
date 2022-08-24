package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.person.AktivitetsloggObserver

internal class V21FjernGruppeId : JsonMigration(version = 21) {
    override val description: String = "Fjerner gruppeId fra vedtaksperiode"

    override fun doMigration(
        jsonNode: ObjectNode,
        meldingerSupplier: MeldingerSupplier,
        observer: AktivitetsloggObserver
    ) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("vedtaksperioder").forEach { periode -> (periode as ObjectNode).remove("gruppeId") }
            arbeidsgiver.path("forkastede").forEach { periode -> (periode as ObjectNode).remove("gruppeId") }
        }
    }
}
