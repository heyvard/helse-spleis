package no.nav.helse.spleis.e2e.infotrygd

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Toggle
import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.dsl.TestPerson
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.InfotrygdhistorikkElement
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.readResource
import no.nav.helse.serde.SerialisertPerson
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertSisteForkastetPeriodeTilstand
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterPåminnelse
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikk
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InfotrygdKorrigererE2ETest : AbstractEndToEndTest() {

    @Test
    fun `infotrygd har betalt ut auu-periode - inntektsmelding trigger revurdering`() = Toggle.InntektsmeldingKanTriggeRevurdering.enable {
        createOverlappendeFraInfotrygdPerson()

        håndterInntektsmelding(listOf(1.januar til 16.januar), refusjon = Inntektsmelding.Refusjon(INNTEKT, 31.mai))

        assertForventetFeil(
            forklaring = "Det ligger en AUU periode 1.april-10.april, hvor Infotrygd senere har utbetalt perioden 1.mars - 30.april," +
                    "og gjort at det er ett sammenhengede sykefraværstilfelle." +
                    "Da inntektsmeldingen som skal opphøre refusjon sendes inn, fanges dette opp av den første AUU-perioden som sender ut startRevurdering." +
                    "Revurderingen fanges bl.a. opp av april-perioden, fordi den skal egentlig utbetales." +
                    "Kanskje april-perioden aldri skulle blitt med i revurderingen?",
            nå = {
                assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
                assertSisteTilstand(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
                assertSisteTilstand(3.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
                assertSisteTilstand(4.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
                assertSisteTilstand(5.vedtaksperiode, AVVENTER_REVURDERING)
            },
            ønsket = {
                assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
                assertSisteTilstand(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
                assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
                // enten bør denne perioden stå helt i ro;
                // eller så bør den I ALLE FALL IKKE være den som driver revurderingen av 1., 2. og 3. vedtaksperiode frem;
                // de burde revurderes isolert sett fra 4. og 5. vedtaksperiode.
                assertSisteTilstand(4.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
                assertSisteTilstand(5.vedtaksperiode, AVVENTER_REVURDERING)
            }
        )
    }

    @Test
    fun `infotrygd korrigerer arbeid gjenopptatt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(29.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterSykmelding(Sykmeldingsperiode(1.februar, 16.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 16.februar, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 29.januar, 31.januar, 100.prosent, INNTEKT), inntektshistorikk = listOf(
            Inntektsopplysning(ORGNUMMER, 29.januar, INNTEKT, true)
        ))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteForkastetPeriodeTilstand(ORGNUMMER, 2.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `skjæringstidspunkt endres som følge av infotrygdperiode`() {
        nyPeriode(1.januar til 1.januar, ORGNUMMER)
        håndterUtbetalingshistorikk(1.vedtaksperiode, besvart = LocalDateTime.now().minusDays(2))
        håndterSykmelding(Sykmeldingsperiode(3.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(3.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode, besvart = LocalDateTime.now().minusDays(2))
        håndterSimulering(2.vedtaksperiode)
        nullstillTilstandsendringer()

        håndterPåminnelse(2.vedtaksperiode, AVVENTER_GODKJENNING)
        håndterUtbetalingshistorikk(2.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 2.januar, 2.januar, 100.prosent, INNTEKT))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertForkastetPeriodeTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, TIL_INFOTRYGD)
    }

    @Test
    fun `to sykefraværstilfeller blir til en, starter med AUU`() {
        createDobbelutbetalingPerson()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.januar, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode, besvart = LocalDate.EPOCH.atStartOfDay())
        håndterSimulering(2.vedtaksperiode)
        håndterPåminnelse(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalingshistorikk(2.vedtaksperiode, Friperiode(1.februar, 28.februar))
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)

        assertEquals(3, inspektør.utbetalinger.size)
        val forrigeUtbetaling = inspektør.utbetaling(1)
        val nyUtbetaling = inspektør.utbetaling(2)

        assertEquals(
            forrigeUtbetaling.inspektør.korrelasjonsId,
            nyUtbetaling.inspektør.korrelasjonsId
        )
        assertEquals(
            listOf(Endringskode.UEND, Endringskode.UEND, Endringskode.NY),
            nyUtbetaling.inspektør.arbeidsgiverOppdrag.inspektør.endringskoder()
        )
    }

    @Test
    fun `to sykefraværstilfeller blir til en, starter med Avsluttet`() {
        createAuuBlirMedIRevureringPerson()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.januar, Dagtype.Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)

        inspektør.utbetaling(2).inspektør.also {
            assertEquals(it.korrelasjonsId, inspektør.utbetaling(0).inspektør.korrelasjonsId)
            assertEquals(it.arbeidsgiverOppdrag.inspektør.fagsystemId(), inspektør.utbetaling(0).inspektør.arbeidsgiverOppdrag.fagsystemId())
            assertEquals(3, it.arbeidsgiverOppdrag.size)
            assertEquals(Endringskode.ENDR, it.arbeidsgiverOppdrag[0].inspektør.endringskode)
            assertEquals(17.januar, it.arbeidsgiverOppdrag[0].inspektør.fom)
            assertEquals(18.januar, it.arbeidsgiverOppdrag[0].inspektør.tom)
            assertEquals(Endringskode.NY, it.arbeidsgiverOppdrag[1].inspektør.endringskode)
            assertEquals(20.januar, it.arbeidsgiverOppdrag[1].inspektør.fom)
            assertEquals(31.januar, it.arbeidsgiverOppdrag[1].inspektør.tom)
            assertEquals(Endringskode.NY, it.arbeidsgiverOppdrag[2].inspektør.endringskode)
            assertEquals(1.mars, it.arbeidsgiverOppdrag[2].inspektør.fom)
            assertEquals(19.mars, it.arbeidsgiverOppdrag[2].inspektør.tom)
        }

        inspektør.utbetaling(3).inspektør.also {
            assertEquals(it.korrelasjonsId, inspektør.utbetaling(1).inspektør.korrelasjonsId)
            assertEquals(it.arbeidsgiverOppdrag.inspektør.fagsystemId(), inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.fagsystemId())
            assertEquals(1, it.arbeidsgiverOppdrag.size)
            assertEquals(Endringskode.UEND, it.arbeidsgiverOppdrag[0].inspektør.endringskode)
            assertEquals(17.mai, it.arbeidsgiverOppdrag[0].inspektør.fom)
            assertEquals(31.mai, it.arbeidsgiverOppdrag[0].inspektør.tom)
        }
    }


    private fun createDobbelutbetalingPerson() = createTestPerson { jurist ->
        SerialisertPerson("/personer/dobbelutbetaling.json".readResource()).deserialize(jurist)
    }

    private fun createOverlappendeFraInfotrygdPerson() = createTestPerson { jurist ->
        SerialisertPerson("/personer/infotrygd-overlappende-utbetaling.json".readResource()).deserialize(jurist).also { person ->
            person.håndter(
                Utbetalingshistorikk(
                    UUID.randomUUID(), "", "", ORGNUMMER, UUID.randomUUID().toString(),
                    InfotrygdhistorikkElement.opprett(
                        LocalDateTime.now(),
                        UUID.randomUUID(),
                        listOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.mars, 30.april, 100.prosent, TestPerson.INNTEKT)),
                        listOf(Inntektsopplysning(ORGNUMMER, 1.januar, TestPerson.INNTEKT, true)),
                        emptyMap(),
                        emptyList(),
                        false
                    ),
                ),
            )
        }
    }

    private fun createAuuBlirMedIRevureringPerson() = createTestPerson { jurist ->
        SerialisertPerson("/personer/auu-blir-med-i-revurdering.json".readResource()).deserialize(jurist).also { person ->
            person.håndter(
                Utbetalingshistorikk(
                    UUID.randomUUID(), "", "", ORGNUMMER, UUID.randomUUID().toString(),
                    InfotrygdhistorikkElement.opprett(
                        oppdatert = LocalDateTime.now(),
                        hendelseId = UUID.randomUUID(),
                        perioder = listOf(
                            Friperiode(fom = 1.februar, tom = 28.februar)
                        ),
                        inntekter = listOf(Inntektsopplysning(ORGNUMMER, 1.januar, TestPerson.INNTEKT, true)),
                        arbeidskategorikoder = emptyMap(),
                        ugyldigePerioder = emptyList(),
                        harStatslønn = false
                    ),
                ),
            )
        }
    }
}
