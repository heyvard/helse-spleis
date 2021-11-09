package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.person.TilstandType.*
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class FlereArbeidsgivereForlengelserTest : AbstractEndToEndTest() {
    private companion object {
        private const val a1 = "arbeidsgiver 1"
        private const val a2 = "arbeidsgiver 2"
        private const val a3 = "arbeidsgiver 3"
    }

    @Test
    fun `Tillater forlengelse av flere arbeidsgivere`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a1
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2020) til 1.desember(2020) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Forlengelsen starter her
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertTilstand(a1, AVVENTER_HISTORIKK, 2)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE, 2)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE, 2)
        assertTilstand(a2, AVVENTER_HISTORIKK, 2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        assertTilstand(a1, AVVENTER_HISTORIKK, 2)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertTilstand(a1, AVVENTER_SIMULERING, 2)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 2)

        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, AVVENTER_GODKJENNING, 2)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 2)

        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, a1)
        assertTilstand(a1, TIL_UTBETALING, 2)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)

        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, AVVENTER_HISTORIKK, 2)
        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)

        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, AVVENTER_SIMULERING, 2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, AVVENTER_GODKJENNING, 2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, a2)
        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, TIL_UTBETALING, 2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)
        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, AVSLUTTET, 2)
    }

    @Test
    fun `Ghost forlenger annen arbeidsgiver - skal gå fint`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            orgnummer = a1,
            beregnetInntekt = 30000.månedlig,
            refusjon = Inntektsmelding.Refusjon(30000.månedlig, null, emptyList())
        )

        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 30000.månedlig.repeat(3)),
            grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 30000.månedlig.repeat(3))
        )

        val arbeidsforhold = listOf(
            Arbeidsforhold(orgnummer = a1, fom = LocalDate.EPOCH, tom = null),
            Arbeidsforhold(orgnummer = a2, fom = LocalDate.EPOCH, tom = null)
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode, inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 30000.månedlig.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 35000.månedlig.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter),
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(1.februar til 16.februar),
            førsteFraværsdag = 1.februar,
            orgnummer = a2,
            beregnetInntekt = 30000.månedlig,
            refusjon = Inntektsmelding.Refusjon(30000.månedlig, null, emptyList())
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVSLUTTET)
        assertEquals(1.januar, a1.inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(1.januar, a2.inspektør.skjæringstidspunkt(1.vedtaksperiode))
        assertEquals(
            a1.inspektør.vilkårsgrunnlag(1.vedtaksperiode),
            a2.inspektør.vilkårsgrunnlag(1.vedtaksperiode)
        )
    }

    @Test
    fun `Tillater forlengelse av overgang fra infotrygd for flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertTilstand(a1, AVVENTER_HISTORIKK, 2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, AVSLUTTET, 2)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, AVVENTER_HISTORIKK, 2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)
        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, AVSLUTTET, 2)
    }

    @Test
    fun `Tillater forlengelse av overgang fra infotrygd for flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren - påvirker ikke uferdige perioder`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertTilstand(a1, AVVENTER_GODKJENNING, 1)
        assertTilstand(a1, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, 2)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 1)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertTilstand(a1, AVVENTER_GODKJENNING, 1)
        assertTilstand(a1, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, 2)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 1)
    }

    @Test
    fun `Tillater forlengelse av forlengelse av overgang fra infotrygd for flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 11.februar(2021) til 20.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        assertTilstand(a1, AVVENTER_HISTORIKK, 3)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, AVSLUTTET, 3)

        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        assertTilstand(a1, AVSLUTTET, 3)
        assertTilstand(a2, AVVENTER_HISTORIKK, 3)
    }

    @Test
    fun `Plutselig kommer en tredje arbeidsgiver midt i en forlengelse - siste arbeidsgiver og senere forlengelser forkastes pga manglende inntekter for ny arbeidsgiver`() {
        /*
        * Person har to arbeidsgivere og får utbetalt en førstegangsbehandling og forlengelse. Så blir personen
        * syk for en ny arbeidgiver vi ikke kjente til på skjæringstidspunktet. Da mangler vi inntekt for denne arbeidsgiveren
        * og alle senere perioder hos alle arbeidsgivere. De utbetalte periodene har forsatt basert seg på riktig sykepengegrunnlag og kan gå gjennom.
        * I testen under blir forlengelsen av a1 og a2 ikke ferdig før a3 kommer inn, og derfor går ingen gjennom.
        * Her kommer a3 inn etter at a1 og a2 er ferdig.
        */
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 1.mars(2021) til 31.mars(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        assertTilstand(a1, AVVENTER_HISTORIKK, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, TIL_INFOTRYGD, 3)
        assertTilstand(a2, TIL_INFOTRYGD, 3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP, 1)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        assertTilstand(a1, TIL_INFOTRYGD, 3)
        assertTilstand(a2, TIL_INFOTRYGD, 3)

        håndterSøknad(
            Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        håndterInntektsmelding(
            listOf(forlengelseperiode.start til forlengelseperiode.start.plusDays(16)),
            førsteFraværsdag = forlengelseperiode.start,
            orgnummer = a3
        )
        assertTilstand(a3, AVVENTER_HISTORIKK, 1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a3, utbetalinger = utbetalinger, inntektshistorikk = inntektshistorikk)
        assertTilstand(a3, TIL_INFOTRYGD, 1)
    }

    @Test
    fun `Plutselig kommer en tredje arbeidsgiver midt i en ikke-avsluttet forlengelse - alle arbeidsgivere forkastes pga manglende inntekter for ny arbeidsgiver`() {
        /*
        * Vi støtter ikke at vi plutselig får inn en ny arbeidsgiver med sykdom senere enn skjæringstidspunktet.
        * Alle forlengelsene blir derfor kastet ut når det viser seg at vi ikke har inntekt for alle arbeidsgiverne.
        * I testen over ble forlengelsen av a1 og a2 ferdig før a3 kom inn, og derfor går den gjennom.
        * Her kommer a3 inn før a1 og a2 er ferdig og alle blir kastet ut fordi a3 ikke har inntekt på skjæringstidspunktet.
        * */
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, TIL_INFOTRYGD, 2)
        assertTilstand(a2, TIL_INFOTRYGD, 2)
        assertTilstand(a3, TIL_INFOTRYGD, 1)
    }

    @Test
    fun `Tredje arbeidsgiver som tidligere var ghost blir syk midt i en forlengelse - Alle vedtaksperioder går igjennom`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a3, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 1.mars(2021) til 31.mars(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        assertTilstand(a1, AVVENTER_HISTORIKK, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP, 1)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE, 3)
        assertTilstand(a2, AVVENTER_HISTORIKK, 3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP, 1)

        håndterYtelser(3.vedtaksperiode, orgnummer = a2)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        håndterInntektsmelding(
            listOf(forlengelsesforlengelseperiode.start til forlengelsesforlengelseperiode.start.plusDays(16)),
            førsteFraværsdag = forlengelsesforlengelseperiode.start,
            orgnummer = a3
        )

        assertTilstand(a1, AVVENTER_HISTORIKK, 3)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 3)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE, 1)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a2)
        håndterSimulering(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        håndterSimulering(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a3)

        assertTilstand(a1, AVSLUTTET, 3)
        assertTilstand(a2, AVSLUTTET, 3)
        assertTilstand(a3, AVSLUTTET, 1)
    }

    @Test
    fun `forlenger forkastet periode med flere arbeidsgivere hvor alle arbeidsgivernes perioder blir forlenget`() {
        /*
        * Vi støtter ikke at vi plutselig får inn en ny arbeidsgiver med sykdom senere enn skjæringstidspunktet.
        * Alle forlengelsene blir derfor kastet ut når det viser seg at vi ikke har inntekt for alle arbeidsgiverne.
        * */
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertTilstand(a3, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a3)
        håndterInntektsmelding(listOf(forlengelseperiode.start til forlengelseperiode.endInclusive), forlengelseperiode.start, orgnummer = a3)
        assertTilstand(a3, AVVENTER_HISTORIKK)
        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        assertTilstand(a3, TIL_INFOTRYGD)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 11.februar(2021) til 20.februar(2021)
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP, 2)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterUtbetalingshistorikk(3.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, TIL_INFOTRYGD, 3)
        assertTilstand(a2, TIL_INFOTRYGD, 3)
        assertTilstand(a3, TIL_INFOTRYGD, 2)
    }

    @Test
    fun `Ghost blir sykmeldt i en forlengelse og alle vedtaksperioder forlenges igjen - Alt går fint`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a3, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertTilstand(a3, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(forlengelseperiode.start til forlengelseperiode.start.plusDays(16)),
            førsteFraværsdag = forlengelseperiode.start,
            orgnummer = a3
        )
        assertTilstand(a3, AVVENTER_HISTORIKK)
        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        håndterSimulering(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a3)
        assertTilstand(a3, AVSLUTTET)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 1.mars(2021) til 20.mars(2021)
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE, 3)
        assertTilstand(a2, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 3)
        assertTilstand(a3, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        håndterYtelser(3.vedtaksperiode, orgnummer = a2)
        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        håndterYtelser(2.vedtaksperiode, orgnummer = a3)

        assertTilstand(a1, AVVENTER_HISTORIKK, 3)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE, 3)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE, 2)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a2)
        håndterSimulering(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a3)
        håndterSimulering(2.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a3)

        assertTilstand(a1, AVSLUTTET, 3)
        assertTilstand(a2, AVSLUTTET, 3)
        assertTilstand(a3, AVSLUTTET, 2)
    }

    @Test
    fun `Tillater forlengelse av flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a1
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2020) til 1.desember(2020) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Forlengelsen starter her
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        val sykmeldingHendelseId = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1, id = sykmeldingHendelseId)
        håndterSøknad(
            Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1,
            id = søknadHendelseId
        )
        assertTilstand(a1, AVVENTER_HISTORIKK, 2)
        assertHendelseIder(sykmeldingHendelseId, søknadHendelseId, orgnummer = a1)
    }

    @Test
    fun `Periode som forlenger annen arbeidsgiver, men ikke seg selv, kastes ut fordi den mangler inntekt på skjæringstidspunkt`() {
        val periode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 1.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 1.januar(2021), 31.januar(2021), 100.prosent, INNTEKT)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        val periode2 = 1.mars(2021) til 31.mars(2021)
        håndterSykmelding(Sykmeldingsperiode(periode2.start, periode2.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 30.april(2021), 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.april(2021), 30.april(2021), 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.april(2021) til 30.april(2021)), førsteFraværsdag = 1.april(2021), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertTilstand(a1, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, 2)
        assertTilstand(a2, TIL_INFOTRYGD, 1)

        håndterSøknad(Sykdom(periode2.start, periode2.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(2.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        assertTilstand(a1, AVSLUTTET, 2)
        assertTilstand(a2, TIL_INFOTRYGD, 1)
    }
}