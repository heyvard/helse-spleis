package no.nav.helse.hendelser.inntektsmelding

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.omsluttendePeriode
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse

internal class DagerFraInntektsmelding(
    private val inntektsmelding: Inntektsmelding
): IAktivitetslogg by inntektsmelding {
    private val opprinneligPeriode = inntektsmelding.sykdomstidslinje().periode()
    private val gjenståendeDager = opprinneligPeriode?.toMutableSet() ?: mutableSetOf()

    internal fun meldingsreferanseId() = inntektsmelding.meldingsreferanseId()
    internal fun leggTil(hendelseIder: MutableSet<Dokumentsporing>) = inntektsmelding.leggTil(hendelseIder)

    internal fun vurdertTilOgMed(dato: LocalDate) = inntektsmelding.trimLeft(dato)
    internal fun oppdatertFom(periode: Periode) = inntektsmelding.oppdaterFom(periode)
    internal fun leggTilArbeidsdagerFør(dato: LocalDate) {
        checkNotNull(opprinneligPeriode) { "Forventer ikke å utvide en tom sykdomstidslinje" }
        inntektsmelding.padLeft(dato)
        val oppdatertPeriode = inntektsmelding.sykdomstidslinje().periode() ?: return
        if (opprinneligPeriode == oppdatertPeriode) return
        val nyeDager = oppdatertPeriode - opprinneligPeriode
        gjenståendeDager.addAll(nyeDager)
    }
    private fun dagerFør(periode: Periode) = gjenståendeDager.filter { it < periode.start }.toSet()

    internal fun håndterGjenståendeFør(periode: Periode, oppdaterSykdom: (sykdomstidslinje: SykdomstidslinjeHendelse) -> Unit) {
        val dagerFør = dagerFør(periode).takeUnless { it.isEmpty() } ?: return
        oppdaterSykdom(PeriodeFraInntektsmelding(inntektsmelding, dagerFør.omsluttendePeriode!!))
        gjenståendeDager.removeAll(dagerFør)
    }

    internal fun håndterGjenståendeFør(periode: Periode, arbeidsgiver: Arbeidsgiver) = håndterGjenståendeFør(periode) {
        arbeidsgiver.oppdaterSykdom(it)
    }

    private fun overlappendeDager(periode: Periode) = periode.intersect(gjenståendeDager)
    internal fun skalHåndteresAv(periode: Periode) = overlappendeDager(periode).isNotEmpty()

    internal fun harBlittHåndtertAv(periode: Periode): Boolean {
        val overlappendeDagerOpprinnelig = periode.intersect(opprinneligPeriode?.toSet() ?: emptySet())
        val overlappendeDagerNå = overlappendeDager(periode)
        return overlappendeDagerOpprinnelig.isNotEmpty() && overlappendeDagerNå.isEmpty()
    }

    internal fun håndter(periode: Periode, arbeidsgiver: Arbeidsgiver) = håndter(periode) {
        arbeidsgiver.oppdaterSykdom(it)
    }

    internal fun håndter(periode: Periode, oppdaterSykdom: (sykdomstidslinje: SykdomstidslinjeHendelse) -> Sykdomstidslinje): Sykdomstidslinje? {
        val overlappendeDager = overlappendeDager(periode).takeUnless { it.isEmpty() } ?: return null
        val arbeidsgiverSykedomstidslinje = oppdaterSykdom(PeriodeFraInntektsmelding(inntektsmelding, overlappendeDager.omsluttendePeriode!!))
        gjenståendeDager.removeAll(overlappendeDager)
        return arbeidsgiverSykedomstidslinje.subset(periode)
    }

    internal fun håndterGjenstående(oppdaterSykdom: (sykdomstidslinje: SykdomstidslinjeHendelse) -> Unit) {
        if (gjenståendeDager.isEmpty()) return
        oppdaterSykdom(PeriodeFraInntektsmelding(inntektsmelding, gjenståendeDager.omsluttendePeriode!!))
        gjenståendeDager.clear()
    }

    internal fun håndterGjenstående(arbeidsgiver: Arbeidsgiver) = håndterGjenstående {
        arbeidsgiver.oppdaterSykdom(it)
    }

    private class PeriodeFraInntektsmelding(
        private val inntektsmelding: Inntektsmelding,
        private val periode: Periode
    ): SykdomstidslinjeHendelse(UUID.randomUUID(), inntektsmelding) {
        override fun sykdomstidslinje() = inntektsmelding.sykdomstidslinje().subset(periode)
        override fun valider(periode: Periode, subsumsjonObserver: SubsumsjonObserver) = throw IllegalStateException("Ikke i bruk")
        override fun fortsettÅBehandle(arbeidsgiver: Arbeidsgiver) = throw IllegalStateException("Ikke i bruk")
        override fun leggTil(hendelseIder: MutableSet<Dokumentsporing>) = throw IllegalStateException("Ikke i bruk")
    }
}