package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson
import no.nav.helse.dsl.lagStandardSammenligningsgrunnlag
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.spleis.e2e.sammenligningsgrunnlag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevurderKorrigertSøknadFlereArbeidsgivereTest : AbstractDslTest() {

    @Test
    fun `Korrigerende søknad hos en arbeidsgiver - setter i gang revurdering`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        a1 {
            håndterSøknad(
                Sykdom(1.januar, 31.januar, 50.prosent),
                Ferie(30.januar, 31.januar)
            )
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

            håndterYtelser(1.vedtaksperiode)
            assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
            assertEquals(21, inspektør.sykdomstidslinje.inspektør.dagteller[Sykedag::class])
            assertEquals(2, inspektør.sykdomstidslinje.inspektør.dagteller[Feriedag::class])
            assertEquals(8, inspektør.sykdomstidslinje.inspektør.dagteller[SykHelgedag::class])
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[17.januar] is NavDag)
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[18.januar] is NavDag)
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `Overlappende søknad som strekker seg forbi vedtaksperioden`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        }
        a2 {
            håndterSøknad(Sykdom(15.januar, 15.februar, 100.prosent))
            håndterSykmelding(Sykmeldingsperiode(15.januar, 15.februar, 100.prosent))
            håndterInntektsmelding(listOf(15.januar til 30.januar), beregnetInntekt = 20000.månedlig)
        }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 20000.månedlig)
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                inntektsvurdering = listOf(a1, a2).lagStandardSammenligningsgrunnlag(20000.månedlig, 1.januar)
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a1 {
            håndterSykmelding(Sykmeldingsperiode(15.januar, 15.februar, 50.prosent))
            håndterSøknad(Sykdom(15.januar, 15.februar, 50.prosent))
            assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
            val utbetalingstidslinje = inspektør.utbetalingstidslinjer(1.vedtaksperiode)
            (17.januar til 31.januar).forEach {
                assertEquals(100.prosent, utbetalingstidslinje[it].økonomi.inspektør.grad)
            }
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
        }
    }

    @Test
    fun `Vedtaksperiodene til a1 og a2 er kant-i-kant og det kommer en korrigerende søknad for a1 - setter i gang revurdering`() {
        val periodeAg1 = 1.januar til 31.januar
        val periodeAg2 = 1.februar til 28.februar
        a1 {
            håndterSykmelding(Sykmeldingsperiode(periodeAg1.start, periodeAg1.endInclusive, 100.prosent))
            håndterSøknad(Sykdom(periodeAg1.start, periodeAg1.endInclusive, 100.prosent))
        }
        a2 {
            håndterSykmelding(Sykmeldingsperiode(periodeAg2.start, periodeAg2.endInclusive, 100.prosent))
            håndterSøknad(Sykdom(periodeAg2.start, periodeAg2.endInclusive, 100.prosent))
            håndterInntektsmelding(listOf(periodeAg2.start til periodeAg2.start.plusDays(15)), beregnetInntekt = 20000.månedlig)
        }
        a1 {
            håndterInntektsmelding(listOf(periodeAg1.start til periodeAg1.start.plusDays(15)), beregnetInntekt = 20000.månedlig)
            håndterVilkårsgrunnlag(
                1.vedtaksperiode,
                inntektsvurdering = listOf(a1, a2).lagStandardSammenligningsgrunnlag(20000.månedlig, periodeAg1.start),
                inntektsvurderingForSykepengegrunnlag = listOf(a1, a2).lagStandardSykepengegrunnlag(20000.månedlig, periodeAg1.start)
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
        }
        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
        }
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }
        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            (17..31).forEach {
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }
        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertTilstand(1.vedtaksperiode, AVSLUTTET)

        }
    }

    @Test
    fun `To arbeidsgivere med ett sykefraværstilfelle og gap over 16 dager på den ene arbeidsgiveren - korrigerende søknad på periode før gap setter i gang revurdering`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        }
        a2 {
            håndterSykmelding(Sykmeldingsperiode(25.januar, 25.februar, 100.prosent))
            håndterSøknad(Sykdom(25.januar, 25.februar, 100.prosent))
        }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = TestPerson.INNTEKT)
            håndterSykmelding(Sykmeldingsperiode(24.februar, 24.mars, 100.prosent))
            håndterSøknad(Sykdom(24.februar, 24.mars, 100.prosent))
            håndterInntektsmelding(listOf(24.februar til 11.mars), beregnetInntekt = TestPerson.INNTEKT)
        }
        a2 {
            håndterInntektsmelding(listOf(25.januar til 9.februar), beregnetInntekt = TestPerson.INNTEKT)
        }
        a1 {
            håndterVilkårsgrunnlag(1.vedtaksperiode,
                arbeidsforhold = listOf(
                    Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null),
                    Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null)
                ),
                inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a1, 1.januar, TestPerson.INNTEKT.repeat(12)),
                        sammenligningsgrunnlag(a2, 1.januar, TestPerson.INNTEKT.repeat(12))
                    )
                ),
                inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                    inntekter = listOf(
                        grunnlag(a1, 1.januar, TestPerson.INNTEKT.repeat(3)),
                        grunnlag(a2, 1.januar, TestPerson.INNTEKT.repeat(3))
                    ),
                    arbeidsforhold = emptyList()
                )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
        }

        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(18.januar, 19.januar))
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
            assertEquals(
                21,
                inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[Sykedag::class]
            )
            assertEquals(
                2,
                inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[Feriedag::class]
            )
            assertEquals(
                8,
                inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[SykHelgedag::class]
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        }
        a1 {
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        }

        a1 {
            assertEquals(6, inspektør.utbetalinger.size)

            inspektør.utbetaling(0).inspektør.also { januarutbetaling ->
                val revurdering = inspektør.utbetaling(3).inspektør
                assertEquals(januarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
                assertEquals(17.januar til 31.januar, januarutbetaling.periode)
                assertEquals(17.januar til 31.januar, revurdering.periode)
                assertEquals(0, revurdering.personOppdrag.size)
                revurdering.arbeidsgiverOppdrag.inspektør.also { arbeidsgiveroppdrag ->
                    assertEquals(2, arbeidsgiveroppdrag.antallLinjer())
                    assertEquals(17.januar, arbeidsgiveroppdrag.fom(0))
                    assertEquals(17.januar, arbeidsgiveroppdrag.tom(0))
                    assertEquals(Endringskode.ENDR, arbeidsgiveroppdrag.endringskode(0))

                    assertEquals(20.januar, arbeidsgiveroppdrag.fom(1))
                    assertEquals(31.januar, arbeidsgiveroppdrag.tom(1))
                    assertEquals(Endringskode.NY, arbeidsgiveroppdrag.endringskode(1))
                }
            }

            inspektør.utbetaling(2).inspektør.also { marsutbetaling ->
                val revurdering = inspektør.utbetaling(5).inspektør
                assertEquals(marsutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
                assertEquals(12.mars til 24.mars, marsutbetaling.periode)
                assertEquals(12.mars til 24.mars, revurdering.periode)
                assertEquals(0, revurdering.personOppdrag.size)
                assertEquals(1, revurdering.arbeidsgiverOppdrag.size)
                assertEquals(Endringskode.UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
            }
        }
        a2 {
            assertEquals(4, inspektør.utbetalinger.size)
            inspektør.utbetaling(1).inspektør.also { februarutbetaling ->
                val revurdering = inspektør.utbetaling(3).inspektør
                assertEquals(februarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
                assertEquals(10.februar til 25.februar, februarutbetaling.periode)
                assertEquals(10.februar til 25.februar, revurdering.periode)
                assertEquals(0, revurdering.personOppdrag.size)
                assertEquals(1, revurdering.arbeidsgiverOppdrag.size)
                assertEquals(Endringskode.UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
                revurdering.arbeidsgiverOppdrag.inspektør.also { arbeidsgiveroppdrag ->
                    assertEquals(10.februar, arbeidsgiveroppdrag.fom(0))
                    assertEquals(23.februar, arbeidsgiveroppdrag.tom(0))
                    assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(0))
                }
            }
        }
    }

    @Test
    fun `Korrigerende søknad for tidligere skjæringstidspunkt`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        listOf(a1, a2).nyeVedtak(1.mars til 31.mars)

        a2 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(18.januar, 19.januar))
        }

        a1 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            assertEquals(Feriedag::class, inspektør.sykdomstidslinje[18.januar]::class)
            assertEquals(Feriedag::class, inspektør.sykdomstidslinje[19.januar]::class)
            val arbeidsgiverOppdrag = inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag
            assertEquals(2, arbeidsgiverOppdrag.size)
            arbeidsgiverOppdrag[0].inspektør.let { utbetalingslinjeInspektør ->
                assertEquals(17.januar, utbetalingslinjeInspektør.fom)
                assertEquals(17.januar, utbetalingslinjeInspektør.tom)
                assertEquals(Endringskode.ENDR, utbetalingslinjeInspektør.endringskode)
                assertNull(utbetalingslinjeInspektør.datoStatusFom)
            }
            arbeidsgiverOppdrag[1].inspektør.let { utbetalingslinjeInspektør ->
                assertEquals(20.januar, utbetalingslinjeInspektør.fom)
                assertEquals(31.januar, utbetalingslinjeInspektør.tom)
                assertEquals(Endringskode.NY, utbetalingslinjeInspektør.endringskode)
                assertNull(utbetalingslinjeInspektør.datoStatusFom)
            }
            assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        }
    }

    @Test
    fun `Korrigerende søknad for førstegangsbehandling med forlengelse - setter i gang en revurdering`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        listOf(a1, a2).forlengVedtak(1.februar til 28.februar)

        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
            assertTilstand(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
            (17..31).forEach {
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
            (17..31).forEach {
                assertEquals(100.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }
    }

    @Test
    fun `Korrigerende søknad for forlengelse - setter i gang en revurdering`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        listOf(a1, a2).forlengVedtak(1.februar til 28.februar)

        a1 {
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(27.februar, 28.februar))
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()

            assertTilstand(2.vedtaksperiode, AVSLUTTET)
            assertTrue(inspektør.utbetalingstidslinjer(2.vedtaksperiode)[27.februar] is Utbetalingsdag.Fridag)
            assertTrue(inspektør.utbetalingstidslinjer(2.vedtaksperiode)[28.februar] is Utbetalingsdag.Fridag)
            assertEquals(18, inspektør.utbetalingstidslinjer(2.vedtaksperiode).inspektør.navDagTeller)
        }

        a2 {
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
        }
    }

    @Test
    fun `Korrigerende søknad for nytt sykefraværstilfelle - setter i gang en revurdering`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        listOf(a1, a2).nyeVedtak(1.mars til 31.mars)

        a1 {
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), Ferie(29.mars, 31.mars))
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()

            assertTilstand(2.vedtaksperiode, AVSLUTTET)
            assertTrue(inspektør.utbetalingstidslinjer(2.vedtaksperiode)[29.mars] is Utbetalingsdag.Fridag)
            assertTrue(inspektør.utbetalingstidslinjer(2.vedtaksperiode)[30.mars] is Utbetalingsdag.Fridag)
            assertEquals(8, inspektør.utbetalingstidslinjer(2.vedtaksperiode).inspektør.navDagTeller)
        }

        a2 {
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
        }
    }

    @Test
    fun `To arbeidsgivere med ett sykefraværstilfelle og gap over 16 dager på den ene arbeidsgiveren - korrigerende søknad på arbeidsgiver uten gap setter i gang revurdering`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        }
        a2 {
            håndterSykmelding(Sykmeldingsperiode(25.januar, 25.februar, 100.prosent))
            håndterSøknad(Sykdom(25.januar, 25.februar, 100.prosent))
        }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = TestPerson.INNTEKT)
            håndterSykmelding(Sykmeldingsperiode(24.februar, 24.mars, 100.prosent))
            håndterSøknad(Sykdom(24.februar, 24.mars, 100.prosent))
            håndterInntektsmelding(listOf(24.februar til 11.mars), beregnetInntekt = TestPerson.INNTEKT)
        }
        a2 {
            håndterInntektsmelding(listOf(25.januar til 9.februar), beregnetInntekt = TestPerson.INNTEKT)
        }
        a1 {
            håndterVilkårsgrunnlag(  1.vedtaksperiode,
                arbeidsforhold = listOf(
                    Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null),
                    Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null)
                ),
                inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a1, 1.januar, TestPerson.INNTEKT.repeat(12)),
                        sammenligningsgrunnlag(a2, 1.januar, TestPerson.INNTEKT.repeat(12))
                    )
                ),
                inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                    inntekter = listOf(
                        grunnlag(a1, 1.januar, TestPerson.INNTEKT.repeat(3)),
                        grunnlag(a2, 1.januar, TestPerson.INNTEKT.repeat(3))
                    ),
                    arbeidsforhold = emptyList()
                )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
        }

        a2 {
            håndterSøknad(Sykdom(25.januar, 25.februar, 50.prosent))
            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            (10..25).forEach {
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
            }
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
        }

        a1 {
            assertEquals(6, inspektør.utbetalinger.size)

            inspektør.utbetaling(0).inspektør.also { januarutbetaling ->
                val revurdering = inspektør.utbetaling(3).inspektør
                assertEquals(januarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
                assertEquals(17.januar til 31.januar, januarutbetaling.periode)
                assertEquals(17.januar til 31.januar, revurdering.periode)
                assertEquals(0, revurdering.personOppdrag.size)
                assertEquals(1, revurdering.arbeidsgiverOppdrag.size)
                assertEquals(Endringskode.UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
            }

            inspektør.utbetaling(2).inspektør.also { marsutbetaling ->
                val revurdering = inspektør.utbetaling(5).inspektør
                assertEquals(marsutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
                assertEquals(12.mars til 24.mars, marsutbetaling.periode)
                assertEquals(12.mars til 24.mars, revurdering.periode)
                assertEquals(0, revurdering.personOppdrag.size)
                assertEquals(1, revurdering.arbeidsgiverOppdrag.size)
                assertEquals(Endringskode.UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
            }
        }

        a2 {
            assertEquals(4, inspektør.utbetalinger.size)

            inspektør.utbetaling(1).inspektør.also { februarutbetaling ->
                val revurdering = inspektør.utbetaling(3).inspektør
                assertEquals(februarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
                assertEquals(10.februar til 25.februar, februarutbetaling.periode)
                assertEquals(10.februar til 25.februar, revurdering.periode)
                assertEquals(0, revurdering.personOppdrag.size)
                assertEquals(1, revurdering.arbeidsgiverOppdrag.size)
                assertEquals(Endringskode.ENDR, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
                revurdering.arbeidsgiverOppdrag.inspektør.also { arbeidsgiveroppdrag ->
                    assertEquals(10.februar, arbeidsgiveroppdrag.fom(0))
                    assertEquals(23.februar, arbeidsgiveroppdrag.tom(0))
                    assertEquals(50, arbeidsgiveroppdrag.grad(0))
                    assertEquals(Endringskode.NY, arbeidsgiveroppdrag.endringskode(0))
                }
            }
        }
    }

    @Test
    fun `To arbeidsgivere med ett sykefraværstilfelle og gap under 16 dager på den ene arbeidsgiveren - korrigerende søknader setter i gang revurdering`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        }
        a2 {
            håndterSykmelding(Sykmeldingsperiode(25.januar, 25.februar, 100.prosent))
            håndterSøknad(Sykdom(25.januar, 25.februar, 100.prosent))
        }
        a1 {
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = TestPerson.INNTEKT)
            håndterSykmelding(Sykmeldingsperiode(10.februar, 10.mars, 100.prosent))
            håndterSøknad(Sykdom(10.februar, 10.mars, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = TestPerson.INNTEKT, 10.februar)
        }
        a2 {
            håndterInntektsmelding(listOf(25.januar til 9.februar), beregnetInntekt = TestPerson.INNTEKT)
        }
        a1 {
            håndterVilkårsgrunnlag(  1.vedtaksperiode,
                arbeidsforhold = listOf(
                    Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null),
                    Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH, null)
                ),
                inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a1, 1.januar, TestPerson.INNTEKT.repeat(12)),
                        sammenligningsgrunnlag(a2, 1.januar, TestPerson.INNTEKT.repeat(12))
                    )
                ),
                inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                    inntekter = listOf(
                        grunnlag(a1, 1.januar, TestPerson.INNTEKT.repeat(3)),
                        grunnlag(a2, 1.januar, TestPerson.INNTEKT.repeat(3))
                    ),
                    arbeidsforhold = emptyList()
                )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
        }

        a2 {
            håndterSøknad(Sykdom(25.januar, 25.februar, 50.prosent))
            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            assertTilstand(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)

            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            (10..25).forEach {
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
            }
        }

        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
            assertTilstand(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
        }

        a1 {
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
            assertTilstand(2.vedtaksperiode, AVSLUTTET)
            (17..31).forEach {
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
            (10..28).forEach {
                assertEquals(100.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
            }
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            assertTilstand(1.vedtaksperiode, AVSLUTTET)
        }
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerGodkjenningRevurdering - setter i gang en overstyring av revurderingen`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        a1 {
            håndterSøknad(
                Sykdom(1.januar, 31.januar, 100.prosent),
                Ferie(30.januar, 31.januar)
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)

            håndterSøknad(
                Sykdom(1.januar, 31.januar, 100.prosent, 50.prosent),
                Ferie(30.januar, 31.januar)
            )
            inspektør.utbetalinger(1.vedtaksperiode).also { utbetalinger ->
                assertEquals(2, utbetalinger.size)
                assertEquals(Utbetaling.Forkastet, utbetalinger.last().inspektør.tilstand)
            }
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstand(1.vedtaksperiode, AVSLUTTET)

            assertEquals(9, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.navDagTeller)
            assertEquals(2, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.fridagTeller)

            (17..29).forEach{
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerSimuleringRevurdering - setter i gang en overstyring av revurderingen`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        a1 {
            håndterSøknad(
                Sykdom(1.januar, 31.januar, 100.prosent),
                Ferie(30.januar, 31.januar)
            )
            håndterYtelser(1.vedtaksperiode)
            assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)

            håndterSøknad(
                Sykdom(1.januar, 31.januar, 100.prosent, 50.prosent),
                Ferie(30.januar, 31.januar)
            )
            inspektør.utbetalinger(1.vedtaksperiode).also { utbetalinger ->
                assertEquals(2, utbetalinger.size)
                assertEquals(Utbetaling.Forkastet, utbetalinger.last().inspektør.tilstand)
            }
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstand(1.vedtaksperiode, AVSLUTTET)

            assertEquals(9, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.navDagTeller)
            assertEquals(2, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.fridagTeller)

            (17..29).forEach{
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerHistorikkRevurdering - setter i gang en overstyring av revurderingen`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        a1 {
            håndterSøknad(
                Sykdom(1.januar, 31.januar, 100.prosent),
                Ferie(30.januar, 31.januar)
            )
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

            håndterSøknad(
                Sykdom(1.januar, 31.januar, 100.prosent, 50.prosent),
                Ferie(30.januar, 31.januar)
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstand(1.vedtaksperiode, AVSLUTTET)

            assertEquals(9, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.navDagTeller)
            assertEquals(2, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.fridagTeller)

            (17..29).forEach{
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }

        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        }
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerRevurdering - setter i gang en overstyring av revurderingen`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        }
        a2 {
            assertTilstand(1.vedtaksperiode, AVVENTER_REVURDERING)
            håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            (17..31).forEach {
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }

        a2 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            (17..31).forEach {
                assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
            }
        }
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerGjennomførtRevurdering - setter i gang en overstyring av revurderingen`() {
        listOf(a1, a2).nyeVedtak(1.januar til 31.januar)
        listOf(a1, a2).forlengVedtak(1.februar til 28.februar)
        nullstillTilstandsendringer()
        a1 {
            håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        }
        a2 {
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        }
        a1 {
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))
            assertTilstander(1.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_GJENNOMFØRT_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_GJENNOMFØRT_REVURDERING)
            assertTilstander(2.vedtaksperiode,
                AVSLUTTET,
                AVVENTER_REVURDERING,
                AVVENTER_GJENNOMFØRT_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_GJENNOMFØRT_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING
            )
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
            (17..18).forEach {
                assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Utbetalingsdag.Fridag)
            }
        }
    }

    private fun TestPerson.TestArbeidsgiver.assertRevurderingUtenEndring(vedtakperiodeId: UUID, block:() -> Unit) {
        val sykdomsHistorikkElementerFør = inspektør.sykdomshistorikk.inspektør.elementer()
        val utbetalingerFør = inspektør.utbetalinger(vedtakperiodeId)
        block()
        val utbetalingerEtter = inspektør.utbetalinger(vedtakperiodeId)
        val sykdomsHistorikkElementerEtter = inspektør.sykdomshistorikk.inspektør.elementer()
        assertEquals(1, utbetalingerEtter.size - utbetalingerFør.size) { "Forventet at det skal være opprettet en utbetaling" }
        assertEquals(Endringskode.UEND, utbetalingerEtter.last().inspektør.arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(0, utbetalingerEtter.last().inspektør.personOppdrag.size)
        assertEquals(sykdomsHistorikkElementerFør, sykdomsHistorikkElementerEtter) { "Forventet at sykdomshistorikken skal være uendret" }
    }
}
