package no.nav.helse.spleis.e2e

import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.*
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class StatslønnWarningTest : AbstractEndToEndTest() {

    @Test
    fun `Ikke warning ved statslønn når det ikke er overgang`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 18.februar)
        håndterYtelser(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, statslønn = true)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(vedtaksperiodeIdInnhenter = 1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertTrue(person.personLogg.warn().isEmpty())
    }

    @Test
    fun `Warning ved statslønn`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 18.februar)
        val historikk = arrayOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.desember(2017),  31.desember(2017), 100.prosent, 15000.daglig))
        håndterYtelser(1.vedtaksperiode, *historikk, statslønn = true)

        assertTrue(person.personLogg.hasErrorsOrWorse())
    }

    @Test
    fun `Error for statslønn ved forlengelse av forkastet periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        val inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.desember(2017), INNTEKT, false))
        val perioder1 = arrayOf(
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.desember(2017), 31.desember(2017), 100.prosent, INNTEKT)
        )
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            utbetalinger = perioder1,
            inntektshistorikk = inntektshistorikk,
            statslønn = true,
            besvart = LocalDate.EPOCH.atStartOfDay()
        )
        håndterYtelser(
            1.vedtaksperiode,
            utbetalinger = perioder1,
            inntektshistorikk = inntektshistorikk,
            statslønn = true,
            besvart = LocalDate.EPOCH.atStartOfDay()
        )

        val perioder2 = perioder1 + ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar, 31.januar, 100.prosent, INNTEKT)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterUtbetalingshistorikk(
            2.vedtaksperiode,
            utbetalinger = perioder2,
            inntektshistorikk = inntektshistorikk,
            statslønn = true,
            besvart = LocalDate.EPOCH.atStartOfDay()
        )
        håndterYtelser(
            2.vedtaksperiode,
            utbetalinger = perioder2,
            inntektshistorikk = inntektshistorikk,
            statslønn = true,
            besvart = LocalDate.EPOCH.atStartOfDay()
        )

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD
        )
        assertError("Det er lagt inn statslønn i Infotrygd, undersøk at utbetalingen blir riktig.", 1.vedtaksperiode.filter())

        assertForventetFeil(
            forklaring = "Spleis sjekker ikke statslønn-feltet ved forlengelse fra Infotrygd",
            nå = {
                assertTilstander(
                    2.vedtaksperiode,
                    START,
                    MOTTATT_SYKMELDING_FERDIG_GAP,
                    AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
                    AVVENTER_HISTORIKK,
                    AVVENTER_SIMULERING
                )
                assertNoErrors(2.vedtaksperiode.filter())
            },
            ønsket = {
                assertForkastetPeriodeTilstander(
                    2.vedtaksperiode,
                    START,
                    MOTTATT_SYKMELDING_FERDIG_GAP,
                    AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
                    AVVENTER_HISTORIKK,
                    TIL_INFOTRYGD
                )
                assertError("Det er lagt inn statslønn i Infotrygd, undersøk at utbetalingen blir riktig.", 2.vedtaksperiode.filter())
            }
        )
    }
}
