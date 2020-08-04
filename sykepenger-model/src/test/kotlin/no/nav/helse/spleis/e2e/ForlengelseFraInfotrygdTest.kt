package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Permisjon
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.ForlengelseFraInfotrygd
import no.nav.helse.person.TilstandType.*
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.utbetalte
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class ForlengelseFraInfotrygdTest : AbstractEndToEndTest() {

    @Test
    internal fun `forlenger vedtaksperiode som har gått til infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 26.januar, 1000, 100, ORGNUMMER)
        håndterUtbetalingshistorikk(0, historikk) // <-- TIL_INFOTRYGD

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(29.januar, 23.februar, 100))
        håndterUtbetalingshistorikk(0, historikk)
        håndterYtelser(0, historikk)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, historikk)
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
        assertEquals(3.januar, inspektør.førsteFraværsdag(0)) {
            "Første fraværsdag settes til den første utbetalte dagen fordi " +
                "vi ikke er i stand til å regne den ut selv ennå. " +
                "Bør regnes ut riktig når vi har én sykdomstidslinje på arbeidsgiver-nivå"
        }
        assertEquals(ForlengelseFraInfotrygd.JA, inspektør.forlengelseFraInfotrygd(0))
    }

    @Test
    internal fun `forlenger ikke vedtaksperiode som har gått til infotrygd, der utbetaling ikke er gjort`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 25.januar, 1000, 100, ORGNUMMER)
        håndterUtbetalingshistorikk(0, historikk)  // <-- TIL_INFOTRYGD
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(29.januar, 23.februar, 100))
        håndterYtelser(0, historikk)
        håndterInntektsmeldingMedValidering(
            0,
            listOf(Periode(29.januar, 13.februar)),
            førsteFraværsdag = 29.januar
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, historikk)

        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_INNTEKTSMELDING_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
        assertEquals(ForlengelseFraInfotrygd.NEI, inspektør.forlengelseFraInfotrygd(0))
    }

    @Test
    fun `forlengelsesperiode der refusjon opphører`() {
        håndterSykmelding(Sykmeldingsperiode(13.mars(2020), 29.mars(2020), 100))
        håndterInntektsmeldingMedValidering(
            0,
            listOf(Periode(13.mars(2020), 28.mars(2020))),
            førsteFraværsdag = 13.mars(2020),
            refusjon = Triple(31.mars(2020), INNTEKT, emptyList())
        )
        håndterSykmelding(Sykmeldingsperiode(30.mars(2020), 14.april(2020), 100))
        håndterSøknad(Sykdom(13.mars(2020), 29.mars(2020), 100))
        håndterSøknad(Sykdom(30.mars(2020), 14.april(2020), 100))
        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(13.mars(2020), 29.mars(2020), 1000, 100, ORGNUMMER),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(13.mars(2020),
                    INNTEKT, ORGNUMMER, true, 31.mars(2020))
            )
        )
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(1, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `når en periode går Til Infotrygd avsluttes påfølgende, tilstøtende perioder som bare har mottatt sykmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100))
        håndterSykmelding(Sykmeldingsperiode(14.mars, 31.mars, 100))
        håndterUtbetalingshistorikk(
            0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
                1.januar,
                31.januar,
                1000,
                100,
                ORGNUMMER
            )
        )  // <-- TIL_INFOTRYGD
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, TIL_INFOTRYGD)
        assertTilstander(0, START, MOTTATT_SYKMELDING_UFERDIG_GAP, MOTTATT_SYKMELDING_FERDIG_GAP)
    }

    @Test
    fun `avdekker tilstøtende periode i Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknad(Sykdom(29.januar, 23.februar, 100))
        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 26.januar, 1000, 100, ORGNUMMER)
        )
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
        assertEquals(3.januar, inspektør.førsteFraværsdag(0)) {
            "Første fraværsdag settes til den første utbetalte dagen fordi " +
                "vi ikke er i stand til å regne den ut selv ennå. " +
                "Bør regnes ut riktig når vi har én sykdomstidslinje på arbeidsgiver-nivå"
        }
    }

    @Test
    fun `avdekker tilstøtende periode i Infotrygd uten at vi har Inntektsopplysninger`() {
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknad(Sykdom(29.januar, 23.februar, 100))
        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 26.januar, 1000, 100, ORGNUMMER),
            inntektshistorikk = emptyList()
        )
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `når en periode går Til Infotrygd avsluttes påfølgende, tilstøtende perioder også`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))
        håndterSykmelding(Sykmeldingsperiode(14.mars, 31.mars, 100))
        håndterSøknad(Sykdom(14.mars, 31.mars, 100))
        håndterUtbetalingshistorikk(
            0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
                1.januar,
                31.januar,
                1000,
                100,
                ORGNUMMER
            )
        )  // <-- TIL_INFOTRYGD
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            TIL_INFOTRYGD
        )
        assertTilstander(0, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP, AVVENTER_GAP)
    }

    @Test
    fun `når en periode går Til Infotrygd avsluttes påfølgende, tilstøtende perioder også (out of order)`() {
        håndterSykmelding(Sykmeldingsperiode(14.mars, 31.mars, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100))
        håndterSøknad(Sykdom(14.mars, 31.mars, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterUtbetalingshistorikk(
            0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
                1.januar,
                31.januar,
                1000,
                100,
                ORGNUMMER
            )
        )  // <-- TIL_INFOTRYGD
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(1, START, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(2, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `tidligere utbetalinger i spleis som er forkastet blir tatt med som en del av utbetalingshistorikken`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(1.januar, 16.januar)), 1.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.låstePerioder.also {
            assertEquals(1, it.size)
            assertEquals(Periode(1.januar, 31.januar), it.first())
        }

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100), Permisjon(10.februar, 20.februar))    // <-- TIL_INFOTRYGD
        inspektør.låstePerioder.also {
            assertEquals(0, it.size)
        }

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.Utbetaling(1.februar, 28.februar, 1000, 100, ORGNUMMER))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, Utbetalingshistorikk.Periode.Utbetaling(1.februar, 28.februar, 1000, 100, ORGNUMMER))
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.låstePerioder.also {
            assertEquals(1, it.size)
            assertEquals(Periode(1.mars, 31.mars), it.first())
        }

        assertForkastetPeriodeTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertForkastetPeriodeTilstander(1, START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, TIL_INFOTRYGD)
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertEquals(inspektør.maksdato(0), inspektør.forkastetMaksdato(0))
    }

    @Test
    fun `lager ikke ny arbeidsgiverperiode når det er tilstøtende historikk`() {
        håndterSykmelding(Sykmeldingsperiode(18.februar(2020), 3.mars(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(18.februar(2020), 3.mars(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(4.mars(2020), 17.mars(2020), 100))
        håndterSøknad(Sykdom(4.mars(2020), 17.mars(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(18.mars(2020), 15.april(2020), 70))
        håndterSøknad(Sykdom(18.mars(2020), 15.april(2020), 70))
        håndterInntektsmelding(listOf(Periode(18.februar(2020), 4.mars(2020))), 18.februar(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(1)
        håndterSimulering(1)
        håndterPåminnelse(1, AVVENTER_GODKJENNING, LocalDateTime.now().minusDays(3)) // <-- TIL_INFOTRYGD
        håndterSykmelding(Sykmeldingsperiode(16.april(2020), 7.mai(2020), 50))
        håndterSøknad(Sykdom(16.april(2020), 7.mai(2020), 50))
        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(5.mars(2020), 17.mars(2020), 1000, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(18.mars(2020), 15.april(2020), 1000, 100, ORGNUMMER),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(5.mars(2020),
                    INNTEKT, ORGNUMMER, true)
            )
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(5.mars(2020), 17.mars(2020), 1000, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(18.mars(2020), 15.april(2020), 1000, 100, ORGNUMMER),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(5.mars(2020),
                    INNTEKT, ORGNUMMER, true)
            )
        )
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertForkastetPeriodeTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertForkastetPeriodeTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_INFOTRYGD
        )
        assertForkastetPeriodeTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            TIL_INFOTRYGD
        )
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        inspektør.utbetalinger.utbetalte().also { utbetalinger ->
            assertEquals(1, utbetalinger.size)
            UtbetalingstidslinjeInspektør(utbetalinger.first().utbetalingstidslinje()).also {
                assertEquals(0, it.arbeidsgiverperiodeDagTeller)
                assertEquals(16, it.navDagTeller)
            }
        }
    }

    @Test
    internal fun `setter forlengelse-flagget likt som forrige periode - forlengelse fra infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 26.januar, 1000, 100, ORGNUMMER)
        håndterUtbetalingshistorikk(0, historikk) // <-- TIL_INFOTRYGD

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(29.januar, 23.februar, 100))
        håndterUtbetalingshistorikk(0, historikk)
        håndterYtelser(0, historikk)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, historikk)
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(24.februar, 28.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(24.februar, 28.februar, 100))
        håndterYtelser(1, historikk)

        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(1, START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)

        assertEquals(ForlengelseFraInfotrygd.JA, inspektør.forlengelseFraInfotrygd(0))
        assertEquals(inspektør.forlengelseFraInfotrygd(0), inspektør.forlengelseFraInfotrygd(1))
    }

    @Test
    fun `setter forlengelse-flagget likt som forrige periode - ikke forlengelse fra infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 25.januar, 1000, 100, ORGNUMMER)
        håndterUtbetalingshistorikk(0, historikk) // <-- TIL_INFOTRYGD

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(29.januar, 23.februar, 100))
        håndterUtbetalingshistorikk(0, historikk)
        håndterYtelser(0, historikk)
        håndterInntektsmeldingMedValidering(
            0,
            listOf(Periode(29.januar, 13.februar)),
            førsteFraværsdag = 29.januar
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, historikk)
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.låstePerioder.also {
            assertEquals(1, it.size)
            assertEquals(Periode(29.januar, 23.februar), it.first())
        }
        håndterSykmelding(Sykmeldingsperiode(24.februar, 28.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(24.februar, 28.februar, 100))
        håndterYtelser(1, historikk)

        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_INNTEKTSMELDING_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(1, START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)

        assertEquals(ForlengelseFraInfotrygd.NEI, inspektør.forlengelseFraInfotrygd(0))
        assertEquals(inspektør.forlengelseFraInfotrygd(0), inspektør.forlengelseFraInfotrygd(1))
    }

    @Test
    fun `Forlengelse av søknad uten utbetaling med opphold betalt i Infotrygd`() {
        // Inspirert av et case i P der en overlappende sykmelding ble kastet
        håndterSykmelding(Sykmeldingsperiode(26.mai(2020), 2.juni(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(26.mai(2020), 2.juni(2020), 100))
        håndterInntektsmelding(listOf(Periode(26.mai(2020), 2.juni(2020))), førsteFraværsdag = 26.mai(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)

        håndterSykmelding(Sykmeldingsperiode(22.juni(2020), 11.juli(2020), 100))
        håndterSøknad(Sykdom(22.juni(2020), 11.juli(2020), 100))
        håndterYtelser(
            1,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(11.juni(2020), 21.juni(2020), 1000, 100, ORGNUMMER),
            inntektshistorikk = listOf(Utbetalingshistorikk.Inntektsopplysning(26.mai(2020), INNTEKT, ORGNUMMER, true))
        )
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(
            1,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(11.juni(2020), 21.juni(2020), 1000, 100, ORGNUMMER),
            inntektshistorikk = listOf(Utbetalingshistorikk.Inntektsopplysning(26.mai(2020), INNTEKT, ORGNUMMER, true))
        )
        inspektør.apply {
            assertTrue(etterspurteBehov(0, Aktivitetslogg.Aktivitet.Behov.Behovtype.Simulering))
            assertTrue(
                utbetalingstidslinjer(vedtaksperiodeId(0))
                    .filterIsInstance<Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag>().isEmpty()
            )
        }
    }

    @Test
    fun `Forlengelse av søknad med utbetaling med opphold betalt i Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(10.mai(2020), 2.juni(2020), 100))
        håndterSøknad(Sykdom(10.mai(2020), 2.juni(2020), 100))
        håndterInntektsmelding(listOf(Periode(10.mai(2020), 25.mai(2020))), førsteFraværsdag = 10.mai(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(22.juni(2020), 11.juli(2020), 100))
        håndterSøknad(Sykdom(22.juni(2020), 11.juli(2020), 100))
        håndterYtelser(
            1,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.juni(2020), 21.juni(2020), 1000, 100, ORGNUMMER),
            inntektshistorikk = listOf(Utbetalingshistorikk.Inntektsopplysning(10.mai(2020), INNTEKT, ORGNUMMER, true))
        )
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(
            1,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.juni(2020), 21.juni(2020), 1000, 100, ORGNUMMER),
            inntektshistorikk = listOf(Utbetalingshistorikk.Inntektsopplysning(10.mai(2020), INNTEKT, ORGNUMMER, true))
        )
        assertForkastetPeriodeTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
        inspektør.apply {
            assertTrue(etterspurteBehov(0, Aktivitetslogg.Aktivitet.Behov.Behovtype.Simulering))
            assertTrue(
                utbetalingstidslinjer(vedtaksperiodeId(0))
                    .filterIsInstance<Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag>().isEmpty()
            )
            assertTrue(
                utbetalingstidslinjer(forkastetVedtaksperiodeId(0))
                    .filterIsInstance<Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag>().isNotEmpty()
            )
        }
    }
}
