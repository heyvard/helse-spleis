package no.nav.helse.serde.reflection

import no.nav.helse.hendelser.ModelForeldrepenger
import no.nav.helse.hendelser.ModelSykepengehistorikk
import no.nav.helse.hendelser.ModelYtelser
import no.nav.helse.person.ArbeidstakerHendelse.Hendelsestype
import java.time.LocalDateTime
import java.util.*

internal class YtelserReflect(ytelser: ModelYtelser) {
    private val hendelseId: UUID = ytelser.hendelseId()
    private val hendelsestype: Hendelsestype = ytelser.hendelsetype()
    private val aktørId: String = ytelser["aktørId"]
    private val fødselsnummer: String = ytelser["fødselsnummer"]
    private val organisasjonsnummer: String = ytelser["organisasjonsnummer"]
    private val vedtaksperiodeId: String = ytelser["vedtaksperiodeId"]
    private val sykepengehistorikk: ModelSykepengehistorikk = ytelser["sykepengehistorikk"]
    private val foreldrepenger: ModelForeldrepenger = ytelser["foreldrepenger"]
    private val rapportertdato: LocalDateTime = ytelser["rapportertdato"]

    internal fun toMap() = mutableMapOf<String, Any?>(
        "hendelseId" to hendelseId,
        "hendelsetype" to hendelsestype.name,
        "aktørId" to aktørId,
        "fødselsnummer" to fødselsnummer,
        "organisasjonsnummer" to organisasjonsnummer,
        "vedtaksperiodeId" to vedtaksperiodeId,
        "sykepengehistorikk" to sykepengehistorikk.let { SykepengehistorikkReflect(it).toMap() },
        "foreldrepenger" to foreldrepenger.let { ForeldrepengerReflect(it).toMap() },
        "rapportertdato" to rapportertdato
    )
}
