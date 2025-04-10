package no.nav.helse.person

import java.util.UUID
import no.nav.helse.dto.deserialisering.ForkastetVedtaksperiodeInnDto
import no.nav.helse.dto.serialisering.ForkastetVedtaksperiodeUtDto
import no.nav.helse.etterlevelse.Regelverkslogg
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Vedtaksperiode.Companion.MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_28
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_31
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_32
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_33
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_34
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_35
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_36
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_37
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SØ_38
import no.nav.helse.utbetalingslinjer.Utbetaling

internal class ForkastetVedtaksperiode(
    private val vedtaksperiode: Vedtaksperiode, // 🚨Denne skal bare brukes til view/dto 🚨
    private val arbeidsgiver: String,
    private val periode: Periode) {

    internal fun view() = vedtaksperiode.view()
    internal fun dto() = ForkastetVedtaksperiodeUtDto(vedtaksperiode.dto(null))

    internal companion object {
        internal fun Iterable<ForkastetVedtaksperiode>.perioder() = map { it.periode }

        private fun List<ForkastetVedtaksperiode>.forlenger(nyPeriode: Periode, arbeidsgiver: String, aktivitetslogg: IAktivitetslogg) = this
            .filter { it.periode.erRettFør(nyPeriode) }
            .onEach {
                val sammeArbeidsgiver = it.arbeidsgiver == arbeidsgiver
                aktivitetslogg.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_37 else RV_SØ_38)
                aktivitetslogg.info("Søknad $nyPeriode for $arbeidsgiver forlenger forkastet periode ${it.periode} for ${it.arbeidsgiver}")
            }.isNotEmpty()

        private fun List<ForkastetVedtaksperiode>.overlapper(nyPeriode: Periode, arbeidsgiver: String, aktivitetslogg: IAktivitetslogg) = this
            .filter { it.periode.overlapperMed(nyPeriode) }
            .onEach {
                val delvisOverlappende = !it.periode.inneholder(nyPeriode) // hvorvidt vedtaksperioden strekker seg utenfor den forkastede
                val sammeArbeidsgiver = it.arbeidsgiver == arbeidsgiver
                aktivitetslogg.funksjonellFeil(
                    when {
                        delvisOverlappende && sammeArbeidsgiver -> RV_SØ_35
                        delvisOverlappende && !sammeArbeidsgiver -> RV_SØ_36
                        !delvisOverlappende && sammeArbeidsgiver -> RV_SØ_33
                        else -> RV_SØ_34
                    }
                )
                aktivitetslogg.info("Søknad $nyPeriode for $arbeidsgiver overlapper med forkastet periode ${it.periode} for ${it.arbeidsgiver}")
            }
            .isNotEmpty()

        private fun List<ForkastetVedtaksperiode>.nyere(nyPeriode: Periode, arbeidsgiver: String, aktivitetslogg: IAktivitetslogg) = this
            .filter { it.periode.start > nyPeriode.endInclusive }
            .onEach {
                val sammeArbeidsgiver = it.arbeidsgiver == arbeidsgiver
                aktivitetslogg.funksjonellFeil(if (sammeArbeidsgiver) RV_SØ_31 else RV_SØ_32)
                aktivitetslogg.info("Søknaden $nyPeriode for $arbeidsgiver er før forkastet periode ${it.periode} for ${it.arbeidsgiver}")
            }
            .isNotEmpty()

        private fun List<ForkastetVedtaksperiode>.kortGap(nyPeriode: Periode, arbeidsgiver: String, aktivitetslogg: IAktivitetslogg) = this
            .filter { it.arbeidsgiver == arbeidsgiver }
            .filter { when (val gap = Periode.mellom(nyPeriode, it.periode)?.count()) {
                null -> false
                else -> gap < MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD
            }}
            .onEach {
                aktivitetslogg.funksjonellFeil(RV_SØ_28)
                aktivitetslogg.info("Søknaden $nyPeriode for $arbeidsgiver har et gap som er mindre enn $MINIMALT_TILLATT_AVSTAND_TIL_INFOTRYGD dager til forkastet periode ${it.periode} på samme arbeidsgiver")
            }
            .isNotEmpty()

        internal fun List<ForkastetVedtaksperiode>.blokkererBehandlingAv(nyPeriode: Periode, arbeidsgiver: String, aktivitetslogg: IAktivitetslogg): Boolean {
            if (forlenger(nyPeriode, arbeidsgiver, aktivitetslogg)) return true
            if (overlapper(nyPeriode, arbeidsgiver, aktivitetslogg)) return true
            if (nyere(nyPeriode, arbeidsgiver, aktivitetslogg)) return true
            if (kortGap(nyPeriode, arbeidsgiver, aktivitetslogg)) return true
            return false
        }

        internal fun gjenopprett(
            person: Person,
            arbeidsgiver: Arbeidsgiver,
            dto: ForkastetVedtaksperiodeInnDto,
            regelverkslogg: Regelverkslogg,
            grunnlagsdata: Map<UUID, VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement>,
            utbetalinger: Map<UUID, Utbetaling>
        ): ForkastetVedtaksperiode {
            val vedtaksperiode = Vedtaksperiode.gjenopprett(
                person = person,
                arbeidsgiver = arbeidsgiver,
                dto = dto.vedtaksperiode,
                regelverkslogg = regelverkslogg,
                grunnlagsdata = grunnlagsdata,
                utbetalinger = utbetalinger
            )
            return ForkastetVedtaksperiode(
                periode = vedtaksperiode.periode,
                arbeidsgiver = arbeidsgiver.organisasjonsnummer,
                vedtaksperiode = vedtaksperiode
            )
        }
    }
}
