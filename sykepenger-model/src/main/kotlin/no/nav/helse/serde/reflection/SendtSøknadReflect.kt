package no.nav.helse.serde.reflection

import no.nav.helse.hendelser.ModelSendtSøknad
import no.nav.helse.person.ArbeidstakerHendelse.Hendelsestype
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class SendtSøknadReflect(sendtSøknad: ModelSendtSøknad) {
    private val hendelseId: UUID = sendtSøknad.hendelseId()
    private val hendelsestype: Hendelsestype = sendtSøknad.hendelsetype()
    private val fnr: String = sendtSøknad["fnr"]
    private val aktørId: String = sendtSøknad["aktørId"]
    private val orgnummer: String = sendtSøknad["orgnummer"]
    private val rapportertdato: LocalDateTime = sendtSøknad["rapportertdato"]
    private val perioder: List<ModelSendtSøknad.Periode> = sendtSøknad["perioder"]
    private val originalJson: String = sendtSøknad["originalJson"]

    internal fun toMap() = mutableMapOf<String, Any?>(
        "hendelseId" to hendelseId,
        "hendelsetype" to hendelsestype.name,
        "fnr" to fnr,
        "aktørId" to aktørId,
        "orgnummer" to orgnummer,
        "rapportertdato" to rapportertdato,
        "perioder" to perioder.map { PeriodeReflect(it).toMap() },
        "originalJson" to originalJson
    )

    private class PeriodeReflect(private val periode: ModelSendtSøknad.Periode) {
        private val fom: LocalDate = periode.fom
        private val tom: LocalDate = periode.tom

        internal fun toMap() = mutableMapOf<String, Any?>(
            "fom" to fom,
            "tom" to tom
        ).also {
            when (periode) {
                is ModelSendtSøknad.Periode.Sykdom -> {
                    it["grad"] = periode.get<ModelSendtSøknad.Periode.Sykdom, Int>("grad")
                    it["faktiskGrad"] = periode.get<ModelSendtSøknad.Periode.Sykdom, Double>("faktiskGrad")
                }
                is ModelSendtSøknad.Periode.Utdanning -> {
                    it["fom"] = periode.get<ModelSendtSøknad.Periode.Utdanning, LocalDate?>("fom")
                }
            }
        }
    }
}
