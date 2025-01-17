package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

internal class V49UtbetalingId : JsonMigration(version = 49) {
    override val description: String = "Lager id på utbetaling"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
            arbeidsgiver.path("utbetalinger").forEach { utbetaling ->
                (utbetaling as ObjectNode).put("id", "${UUID.randomUUID()}")
            }
        }
    }
}

