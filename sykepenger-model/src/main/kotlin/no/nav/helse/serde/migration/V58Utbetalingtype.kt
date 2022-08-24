package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.person.AktivitetsloggObserver

internal class V58Utbetalingtype : JsonMigration(version = 58) {
    override val description: String = "Utvider Utbetaling med type"

    override fun doMigration(
        jsonNode: ObjectNode,
        meldingerSupplier: MeldingerSupplier,
        observer: AktivitetsloggObserver
    ) {
        jsonNode
            .path("arbeidsgivere")
            .forEach { arbeidsgiver ->
                arbeidsgiver
                    .path("utbetalinger")
                    .map { it as ObjectNode }
                    .onEach {
                        it.put("type", if (it.path("annullert").asBoolean()) "ANNULLERING" else "UTBETALING")
                    }
            }
    }
}

