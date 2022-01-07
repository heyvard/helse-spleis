package no.nav.helse.person

import no.nav.helse.Toggle
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.person.builders.UtbetaltEventBuilder
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.filter.Brukerutbetalingfilter
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.harId
import no.nav.helse.utbetalingstidslinje.Sykepengerettighet
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import java.time.LocalDate
import java.util.*

internal class VedtaksperiodeUtbetalinger(private val arbeidsgiver: Arbeidsgiver, utbetalinger: List<Utbetaling>) {
    internal constructor(arbeidsgiver: Arbeidsgiver) : this(arbeidsgiver, mutableListOf())

    private val utbetalinger = utbetalinger.toMutableList()
    private val siste get() = utbetalinger.lastOrNull()

    internal fun accept(visitor: VedtaksperiodeVisitor) {
        visitor.preVisitVedtakserperiodeUtbetalinger(utbetalinger)
        utbetalinger.forEach { it.accept(visitor) }
        visitor.postVisitVedtakserperiodeUtbetalinger(utbetalinger)
    }

    internal fun harUtbetaling() = siste != null

    private fun erSiste(other: Utbetaling) = siste == other
    internal fun erSiste(other: VedtaksperiodeUtbetalinger) = erSiste(other.siste!!)

    internal fun erAvsluttet() = siste?.erAvsluttet() == true
    internal fun erAvvist() = siste?.erAvvist() == true
    internal fun harUtbetalinger() = siste?.harUtbetalinger() == true
    internal fun harFeilet() = siste?.harFeilet() == true
    internal fun erUtbetalt() = siste?.erUtbetalt() == true
    internal fun erUbetalt() = siste?.erUbetalt() == true
    internal fun kanIkkeForsøkesPåNy() = siste?.kanIkkeForsøkesPåNy() == true
    internal fun kanForkastes(other: List<Utbetaling>) =
        utbetalinger.isEmpty() || siste!!.kanForkastes(other)
    internal fun harAvsluttede() = utbetalinger.any { it.erAvsluttet() }
    internal fun harId(utbetalingId: UUID) = utbetalinger.harId(utbetalingId)
    internal fun overlapperMed(other: Periode) = utbetalinger.any { it.periode.overlapperMed(other) }
    internal fun hørerIkkeSammenMed(other: Utbetaling) = siste?.hørerSammen(other) == false
    internal fun hørerIkkeSammenMed(other: VedtaksperiodeUtbetalinger) = hørerIkkeSammenMed(other.siste!!)
    internal fun gjelderIkkeFor(hendelse: UtbetalingHendelse) = siste?.gjelderFor(hendelse) != true
    internal fun gjelderIkkeFor(hendelse: Utbetalingsgodkjenning) = siste?.gjelderFor(hendelse) != true
    internal fun erHistorikkEndretSidenBeregning(infotrygdhistorikk: Infotrygdhistorikk) = infotrygdhistorikk.harEndretHistorikk(siste!!)

    internal fun reberegnUtbetaling(hvisRevurdering: () -> Unit, hvisUtbetaling: () -> Unit) = siste!!.reberegnUtbetaling(hvisRevurdering, hvisUtbetaling)

    internal fun vedtakFattet(hendelse: IAktivitetslogg) = siste!!.vedtakFattet(hendelse)

    internal fun forkast(hendelse: IAktivitetslogg) {
        siste?.forkast(hendelse)
    }

    internal fun mottaRevurdering(hendelse: ArbeidstakerHendelse, utbetaling: Utbetaling, periode: Periode): Utbetalingstidslinje {
        return nyUtbetaling(hendelse, periode) { utbetaling }
    }

    internal fun lagUtbetaling(fødselsnummer: String, periode: Periode, sykepengerettighet: Sykepengerettighet, hendelse: ArbeidstakerHendelse): Utbetalingstidslinje {
        return nyUtbetaling(hendelse, periode) {
            arbeidsgiver.lagUtbetaling(
                aktivitetslogg = hendelse,
                fødselsnummer = fødselsnummer,
                maksdato = sykepengerettighet.maksdato,
                forbrukteSykedager = sykepengerettighet.forbrukteSykedager,
                gjenståendeSykedager = sykepengerettighet.gjenståendeSykedager,
                periode = periode,
                forrige = siste
            )
        }
    }

    internal fun lagRevurdering(fødselsnummer: String, periode: Periode, sykepengerettighet: Sykepengerettighet, hendelse: ArbeidstakerHendelse): Utbetalingstidslinje {
        return nyUtbetaling(hendelse, periode) {
            arbeidsgiver.lagRevurdering(
                aktivitetslogg = hendelse,
                fødselsnummer = fødselsnummer,
                maksdato = sykepengerettighet.maksdato,
                forbrukteSykedager = sykepengerettighet.forbrukteSykedager,
                gjenståendeSykedager = sykepengerettighet.gjenståendeSykedager,
                periode = periode,
                forrige = utbetalinger
            ).also { arbeidsgiver.fordelRevurdertUtbetaling(hendelse, it) }
        }
    }

    private fun nyUtbetaling(hendelse: IAktivitetslogg, periode: Periode, generator: () -> Utbetaling): Utbetalingstidslinje {
        siste?.forkast(hendelse)
        return generator().also { utbetalinger.add(it) }.utbetalingstidslinje(periode)
    }

    internal fun build(builder: VedtakFattetBuilder) {
        siste?.build(builder)
    }
    internal fun build(builder: UtbetaltEventBuilder) {
        siste?.build(builder)
    }

    internal fun build(builder: Brukerutbetalingfilter.Builder) {
        builder.utbetaling(siste!!)
    }

    internal fun kanIkkeFortsette(hendelse: IAktivitetslogg, harBrukerutbetaling: Boolean) =
        Toggle.LageBrukerutbetaling.kanIkkeFortsette(hendelse, siste!!, harBrukerutbetaling)

    internal fun valider(simulering: Simulering) = siste!!.valider(simulering)
    internal fun erKlarForGodkjenning() = siste!!.erKlarForGodkjenning()
    internal fun simuler(hendelse: IAktivitetslogg) = siste!!.simuler(hendelse)

    internal fun godkjenning(
        hendelse: IAktivitetslogg,
        vedtaksperiode: Vedtaksperiode,
        skjæringstidspunkt: LocalDate,
        periodetype: Periodetype,
        aktiveVedtaksperioder: List<Aktivitetslogg.Aktivitet.AktivVedtaksperiode>,
        arbeidsforholdId: String?,
        orgnummereMedAktiveArbeidsforhold: List<String>,
        aktivitetslogg: Aktivitetslogg
    ) {
        siste!!.godkjenning(
            hendelse = hendelse,
            vedtaksperiode = vedtaksperiode,
            skjæringstidspunkt = skjæringstidspunkt,
            periodetype = periodetype,
            aktiveVedtaksperioder = aktiveVedtaksperioder,
            arbeidsforholdId = arbeidsforholdId,
            orgnummereMedAktiveArbeidsforhold = orgnummereMedAktiveArbeidsforhold,
            aktivitetslogg = aktivitetslogg
        )
    }
}