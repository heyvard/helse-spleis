package no.nav.helse.spleis.e2e

import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.TestArbeidsgiverInspektør
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.oktober
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.etterlevelse.MaskinellJurist
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.serde.serialize
import no.nav.helse.somPersonidentifikator
import no.nav.helse.utbetalingstidslinje.Begrunnelse.SykepengedagerOppbruktOver67
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class MaksdatoE2ETest : AbstractEndToEndTest() {

    @Test
    fun `hensyntar tidligere arbeidsgivere fra IT`() {
        nyPeriode(1.mars til 31.mars, a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(a2, 1.januar, 31.januar, 100.prosent, INNTEKT))
        håndterInntektsmelding(listOf(1.mars til 16.mars))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        val utbetalingInspektør = inspektør(a1).utbetaling(0).inspektør
        assertEquals(33, utbetalingInspektør.forbrukteSykedager)
        assertEquals(215, utbetalingInspektør.gjenståendeSykedager)
        assertEquals(25.januar(2019), utbetalingInspektør.maksdato)
    }

    @Test
    fun `hensyntar senere arbeidsgivere fra IT`() {
        nyPeriode(1.mars til 31.mars, a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(a2, 1.april, 30.april, 100.prosent, INNTEKT))
        håndterInntektsmelding(listOf(1.mars til 16.mars))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        val utbetalingInspektør = inspektør(a1).utbetaling(0).inspektør
        assertEquals(31, utbetalingInspektør.forbrukteSykedager)
        assertEquals(217, utbetalingInspektør.gjenståendeSykedager)
        assertEquals(27.februar(2019), utbetalingInspektør.maksdato)
    }

    @Test
    fun `syk etter maksdato`() {
        var forrigePeriode = 1.januar til 31.januar
        nyttVedtak(forrigePeriode.start, forrigePeriode.endInclusive, 100.prosent)
        // setter opp vedtaksperioder frem til 182 dager etter maksdato
        repeat(17) { _ ->
            forrigePeriode = nestePeriode(forrigePeriode)
            forlengVedtak(forrigePeriode.start, forrigePeriode.endInclusive)
        }
        // oppretter forlengelse fom 182 dager etter maksdato: denne blir kastet til Infotrygd
        forrigePeriode = nyPeriodeMedYtelser(forrigePeriode)
        val nestSiste = observatør.sisteVedtaksperiode()
        assertSisteTilstand(nestSiste, TIL_INFOTRYGD) {
            "Disse periodene skal kastes ut pr nå"
        }
        forrigePeriode = nyPeriode(forrigePeriode)
        val siste = observatør.sisteVedtaksperiode()
        assertNotEquals(nestSiste, siste)
        assertForkastetPeriodeTilstander(siste, START, TIL_INFOTRYGD)
    }

    @Test
    fun `avviser perioder med sammenhengende sykdom etter 26 uker fra maksdato`() {
        var forrigePeriode = 1.januar til 31.januar
        nyttVedtak(forrigePeriode.start, forrigePeriode.endInclusive, 100.prosent)
        // setter opp vedtaksperioder frem til 182 dager etter maksdato
        repeat(17) { _ ->
            forrigePeriode = nestePeriode(forrigePeriode)
            forlengVedtak(forrigePeriode.start, forrigePeriode.endInclusive)
        }
        // oppretter forlengelse fom 182 dager etter maksdato
        forrigePeriode = nyPeriodeMedYtelser(forrigePeriode)
        val siste = observatør.sisteVedtaksperiode()
        assertFunksjonellFeil("Bruker er fortsatt syk 26 uker etter maksdato", siste.filter())
        assertSisteTilstand(siste, TIL_INFOTRYGD) {
            "Disse periodene skal kastes ut pr nå"
        }
    }

    @Test
    fun `oppbrukt sykepenger etter fylte 67 serialiseres til rett begrunnelse`() {
        val fnr = "16104812345"
        createTestPerson(fnr.somPersonidentifikator(), 16.oktober(1948))
        nyttVedtak(17.januar, 1.mai)
        inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.avvistedager.forEach { avvistDag ->
            assertEquals(listOf(SykepengedagerOppbruktOver67), avvistDag.begrunnelser)
        }
        val deserialisertPerson = person.serialize().deserialize(MaskinellJurist())
        val deserialisertInspektør = TestArbeidsgiverInspektør(deserialisertPerson, ORGNUMMER)
        deserialisertInspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.avvistedager.forEach { avvistDag ->
            assertEquals(listOf(SykepengedagerOppbruktOver67), avvistDag.begrunnelser)
        }
    }

    private fun nyPeriode(forrigePeriode: Periode): Periode {
        val nestePeriode = nestePeriode(forrigePeriode)
        håndterSykmelding(Sykmeldingsperiode(nestePeriode.start, nestePeriode.endInclusive, 100.prosent))
        val id: IdInnhenter = observatør.sisteVedtaksperiode()
        håndterSøknadMedValidering(id, Søknad.Søknadsperiode.Sykdom(nestePeriode.start, nestePeriode.endInclusive, 100.prosent))
        return nestePeriode
    }

    private fun nyPeriodeMedYtelser(forrigePeriode: Periode): Periode {
        val nestePeriode = nyPeriode(forrigePeriode)
        val id: IdInnhenter = observatør.sisteVedtaksperiode()
        håndterYtelser(id)
        return nestePeriode
    }

    private fun nestePeriode(forrigePeriode: Periode): Periode {
        val nesteMåned = forrigePeriode.start.plusMonths(1)
        return nesteMåned til nesteMåned.plusDays(nesteMåned.lengthOfMonth().toLong() - 1)
    }
}
