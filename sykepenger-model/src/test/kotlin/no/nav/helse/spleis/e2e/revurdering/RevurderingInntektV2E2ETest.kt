package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding.Refusjon
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.TilstandType.UTBETALING_FEILET
import no.nav.helse.person.Varselkode.RV_IV_2
import no.nav.helse.person.Varselkode.RV_OS_1
import no.nav.helse.person.Varselkode.RV_SV_1
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.AktivitetsloggFilter
import no.nav.helse.spleis.e2e.assertHarHendelseIder
import no.nav.helse.spleis.e2e.assertHarIkkeHendelseIder
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengTilGodkjentVedtak
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikk
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.manuellFeriedag
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.spleis.e2e.sammenligningsgrunnlag
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RevurderingInntektV2E2ETest : AbstractEndToEndTest() {

    @Test
    fun `revurdere enslig periode`() {
        val forventetEndring = 200.daglig
        val overstyrtInntekt = INNTEKT + forventetEndring
        nyttVedtak(1.januar, 31.januar)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(19.januar, 1431.daglig, INGEN, INNTEKT)
        håndterOverstyrInntekt(overstyrtInntekt, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        assertDag<Sykedag, NavDag>(
            dato = 17.januar,
            arbeidsgiverbeløp = 1431.daglig,
            personbeløp = forventetEndring,
            aktuellDagsinntekt = overstyrtInntekt
        )
        assertDiff(2200)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
    }


    @Test
    fun `skal kunne overstyre inntekt i utkast til revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertDiff(-5588)

        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        inspektør.utbetalinger(1.vedtaksperiode).also { utbetalinger ->
            assertEquals(2, utbetalinger.size)
            assertEquals(Utbetaling.Forkastet, utbetalinger.last().inspektør.tilstand)
        }
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertDiff(-3047)

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i utkast til revurdering, også når det er snakk om flere perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertDiff(-15748)

        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        inspektør.utbetalinger(2.vedtaksperiode).also { utbetalinger ->
            assertEquals(2, utbetalinger.size)
            assertEquals(Utbetaling.Forkastet, utbetalinger.last().inspektør.tilstand)
        }
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertDiff(-8587)

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING
        )

        assertTilstander(2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNNNNHH NNNNNHH NNNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i AvventerHistorikkRevurdering`() {
        nyttVedtak(1.januar, 31.januar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )
        assertDiff(-3047)

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i AvventerSimuleringRevurdering`() {
        nyttVedtak(1.januar, 31.januar)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 20000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )
        assertDiff(-3047)

        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())
    }

    @Test
    fun `skal kunne overstyre inntekt i utkast til revurdering ved revurdering av tidslinje`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(
            dato = 22.januar,
            arbeidsgiverbeløp = 1431.daglig,
            personbeløp = INGEN,
            aktuellDagsinntekt = INNTEKT
        )

        håndterOverstyrTidslinje((20.januar til 29.januar).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(
            dato = 22.januar,
            arbeidsgiverbeløp = INGEN,
            personbeløp = INGEN,
            aktuellDagsinntekt = INNTEKT
        )
        håndterSimulering(2.vedtaksperiode)

        val overstyrtInntekt = 20000.månedlig
        håndterOverstyrInntekt(inntekt = overstyrtInntekt, skjæringstidspunkt = 1.januar)
        håndterYtelser(2.vedtaksperiode)
        assertDag<Sykedag, NavDag>(
            dato = 17.januar,
            arbeidsgiverbeløp = overstyrtInntekt.rundTilDaglig(),
            personbeløp = INGEN,
            aktuellDagsinntekt = overstyrtInntekt
        )
        håndterSimulering(2.vedtaksperiode)
        assertDiff(-21286)

        // 23075 = round((20000 * 12) / 260) * 25 (25 nav-dager i januar + februar 2018)
        assertEquals(23075, inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.totalbeløp())
        assertEquals("SSSSSHH SSSSSHH SSSSSFF FFFFFFF FSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        assertEquals("PPPPPPP PPPPPPP PPNNNFF FFFFFFF FNNNNHH NNNNNHH NNNNNHH NNNNNHH NNN", inspektør.sisteUtbetalingUtbetalingstidslinje().toString().trim())

        assertTilstander(1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING
        )

        assertTilstander(2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )
    }

    @Test
    fun `Ved overstyring av revurdering av inntekt til under krav til minste sykepengegrunnlag skal vi opphøre den opprinnelige utbetalingen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, beregnetInntekt = 50000.årlig)
        val inntekter = listOf(grunnlag(ORGNUMMER, 1.januar, 50000.årlig.repeat(3)))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(ORGNUMMER, 1.januar, 50000.årlig.repeat(12)),
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = inntekter, arbeidsforhold = emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrInntekt(46000.årlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        val utbetalinger = inspektør.utbetalinger
        assertTrue(utbetalinger[0].inspektør.erUtbetalt)
        assertTrue(utbetalinger[1].inspektør.erUbetalt)
        assertEquals(1, utbetalinger.map { it.inspektør.arbeidsgiverOppdrag.fagsystemId() }.toSet().size)
        assertDiff(-2112)
    }

    @Test
    fun `revurder inntekt ukjent skjæringstidspunkt`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nullstillTilstandsendringer()
        assertThrows<IllegalStateException> { håndterOverstyrInntekt(inntekt = 32000.månedlig, skjæringstidspunkt = 2.januar) }
        assertIngenFunksjonelleFeil(AktivitetsloggFilter.person())
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertEquals(1, inspektør.utbetalinger.size)
    }

    @Test
    fun `revurder inntekt tidligere skjæringstidspunkt - med AGP imellom`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nyttVedtak(1.mars, 31.mars, 100.prosent)
        nullstillTilstandsendringer()

        val korrelasjonsIdPåUtbetaling1 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId

        håndterOverstyrInntekt(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )

        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING
        )

        assertEquals(3, inspektør.utbetalinger.size)
        val korrelasjonsIdPåUtbetaling2 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId
        val korrelasjonsIdPåUtbetaling3 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.korrelasjonsId

        assertEquals(korrelasjonsIdPåUtbetaling1, korrelasjonsIdPåUtbetaling2)
        assertNotEquals(korrelasjonsIdPåUtbetaling2, korrelasjonsIdPåUtbetaling3)
        assertDiff(506)

        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        nullstillTilstandsendringer()

        håndterYtelser(2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET)

        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)

        assertEquals(4, inspektør.utbetalinger.size)
        val korrelasjonsIdPåUtbetaling4 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId
        val korrelasjonsIdPåUtbetaling5 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.korrelasjonsId

        assertEquals(korrelasjonsIdPåUtbetaling3, korrelasjonsIdPåUtbetaling5)
        assertNotEquals(korrelasjonsIdPåUtbetaling4, korrelasjonsIdPåUtbetaling5)
        assertDiff(0)
    }

    @Test
    fun `revurder inntekt tidligere skjæringstidspunkt - med samme AGP`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nyttVedtak(2.februar, 28.februar, 100.prosent)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 32000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING
        )

        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING
        )

        assertEquals(3, inspektør.utbetalinger.size)

        val korrelasjonsIdPåUtbetaling1 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(1.vedtaksperiode).inspektør.korrelasjonsId
        val korrelasjonsIdPåUtbetaling2 = inspektør.sisteAvsluttedeUtbetalingForVedtaksperiode(2.vedtaksperiode).inspektør.korrelasjonsId

        assertEquals(korrelasjonsIdPåUtbetaling1, korrelasjonsIdPåUtbetaling2)
        assertDiff(506)
        inspektør.utbetaling(2).inspektør.also { revurdering ->
            val januarutbetaling = inspektør.utbetaling(0).inspektør
            val februarutbetaling = inspektør.utbetaling(1).inspektør
            assertEquals(januarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)
            assertEquals(februarutbetaling.korrelasjonsId, revurdering.korrelasjonsId)

            assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNNANHH NNNNNHH NNNNNHH NNNNNHH NNN", revurdering.utbetalingstidslinje.toString().trim())
            assertEquals(17.januar til 31.januar, januarutbetaling.periode)
            assertEquals(17.januar til 28.februar, februarutbetaling.periode)
            assertEquals(17.januar til 28.februar, revurdering.periode)
            assertEquals(1, revurdering.personOppdrag.size)
            assertEquals(2, revurdering.arbeidsgiverOppdrag.size)
            assertEquals(Endringskode.UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
            assertEquals(0, revurdering.arbeidsgiverOppdrag.inspektør.nettoBeløp)
            revurdering.arbeidsgiverOppdrag.inspektør.also { arbeidsgiveroppdrag ->
                assertEquals(17.januar, arbeidsgiveroppdrag.fom(0))
                assertEquals(31.januar, arbeidsgiveroppdrag.tom(0))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(0))
                assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(0))

                assertEquals(2.februar, arbeidsgiveroppdrag.fom(1))
                assertEquals(28.februar, arbeidsgiveroppdrag.tom(1))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(1))
                assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(1))
            }
            assertEquals(506, revurdering.personOppdrag.inspektør.nettoBeløp)
            revurdering.personOppdrag.inspektør.also { personoppdrag ->
                assertEquals(17.januar, personoppdrag.fom(0))
                assertEquals(31.januar, personoppdrag.tom(0))
                assertEquals(46, personoppdrag.beløp(0))
                assertEquals(Endringskode.NY, personoppdrag.endringskode(0))
            }
        }
    }

    @Test
    fun `revurder inntekt avvik over 25 prosent reduksjon`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(inntekt = 7000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)

        assertVarsel(RV_IV_2, AktivitetsloggFilter.person())
        assertEquals(2, inspektør.utbetalinger.size)
    }

    @Test
    fun `revurder inntekt avvik over 25 prosent økning`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nullstillTilstandsendringer()

        håndterOverstyrInntekt(inntekt = 70000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)

        assertVarsel(RV_IV_2, AktivitetsloggFilter.person())
        assertEquals(2, inspektør.utbetalinger.size)
    }

    @Test
    fun `revurder inntekt til under krav til minste sykepengegrunnlag`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(inntekt = 3000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )

        val utbetalingTilRevurdering = inspektør.utbetalinger.last()
        assertEquals(2, inspektør.utbetalinger.size)
        assertDiff(-15741)

        assertVarsel(RV_IV_2, AktivitetsloggFilter.person())
        assertVarsel(RV_SV_1, AktivitetsloggFilter.person())
        assertFalse(utbetalingTilRevurdering.utbetalingstidslinje().harUtbetalinger())
    }

    @Test
    fun `revurder inntekt til under krav til minste sykepengegrunnlag slik at utbetaling opphører, og så revurder igjen til over krav til minste sykepengegrunnlag`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent, beregnetInntekt = 5000.månedlig)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(inntekt = 3000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING
        )

        val utbetalingTilRevurdering = inspektør.utbetalinger.last()
        assertEquals(2, inspektør.utbetalinger.size)
        assertDiff(-2541)

        assertVarsel(RV_IV_2, AktivitetsloggFilter.person())
        assertVarsel(RV_SV_1, AktivitetsloggFilter.person())
        assertFalse(utbetalingTilRevurdering.utbetalingstidslinje().harUtbetalinger())
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterOverstyrInntekt(5000.månedlig, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)

        val utbetalinger = inspektør.utbetalinger
        var opprinneligFagsystemId: String?
        utbetalinger[0].inspektør.arbeidsgiverOppdrag.apply {
            assertEquals(Endringskode.NY, inspektør.endringskode)
            opprinneligFagsystemId = fagsystemId()
            assertEquals(1, size)
            first().inspektør.apply {
                assertEquals(Endringskode.NY, endringskode)
                assertEquals(1, delytelseId)
                assertEquals(null, refDelytelseId)
                assertEquals(null, refFagsystemId)
            }
        }
        utbetalinger[1].inspektør.arbeidsgiverOppdrag.apply {
            assertEquals(Endringskode.ENDR, inspektør.endringskode)
            assertEquals(opprinneligFagsystemId, fagsystemId())
            assertEquals(1, size)
            first().inspektør.apply {
                assertEquals(Endringskode.ENDR, endringskode)
                assertEquals(1, delytelseId)
                assertEquals(null, refDelytelseId)
                assertEquals(null, refFagsystemId)
                assertEquals(17.januar, datoStatusFom)
            }
        }
        utbetalinger[2].inspektør.arbeidsgiverOppdrag.apply {
            assertEquals(Endringskode.ENDR, inspektør.endringskode)
            assertEquals(opprinneligFagsystemId, fagsystemId())
            assertEquals(1, size)
            first().inspektør.apply {
                assertEquals(Endringskode.NY, endringskode)
                assertEquals(2, delytelseId)
                assertEquals(1, refDelytelseId)
                assertEquals(fagsystemId(), refFagsystemId)
            }
        }
        assertDiff(2541)
        assertVarsel(RV_OS_1, AktivitetsloggFilter.person())
    }

    @Test
    fun `revurdering av inntekt delegeres til den første perioden som har en utbetalingstidslinje - arbeidsgiversøknad først`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 15.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 15.januar, 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        håndterSykmelding(Sykmeldingsperiode(16.januar, 15.februar, 100.prosent))
        håndterSøknad(Sykdom(16.januar, 15.februar, 100.prosent))
        håndterInntektsmelding(
            listOf(Periode(1.januar, 16.januar))
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        nullstillTilstandsendringer()
        håndterOverstyrInntekt(skjæringstidspunkt = 1.januar, inntekt = 30000.månedlig)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertDiff(-1012)
    }

    @Test
    fun `revurdering av inntekt delegeres til den første perioden som har en utbetalingstidslinje - periode uten utbetaling først`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Søknad.Søknadsperiode.Ferie(1.januar, 31.januar))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        nullstillTilstandsendringer()

        håndterOverstyrInntekt(skjæringstidspunkt = 1.januar, inntekt = 30000.månedlig)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(2, inspektør.utbetalinger.size)
        assertEquals(0, inspektør.utbetalinger(1.vedtaksperiode).size)
        assertEquals(2, inspektør.utbetalinger(2.vedtaksperiode).size)
        assertEquals(Utbetaling.Utbetalt, inspektør.utbetalingtilstand(0))
        assertEquals(Utbetaling.Utbetalt, inspektør.utbetalingtilstand(1))
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertTilstander(
            2.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertDiff(-920)
    }

    @Test
    fun `Perioder med aktuelt skjæringstidspunkt skal være stemplet med hendelseId`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        forlengVedtak(1.april, 30.april)
        val overstyrInntektHendelseId = UUID.randomUUID()
        håndterOverstyrInntekt(skjæringstidspunkt = 1.mars, meldingsreferanseId = overstyrInntektHendelseId)

        assertHarIkkeHendelseIder(1.vedtaksperiode, overstyrInntektHendelseId)
        assertHarHendelseIder(2.vedtaksperiode, overstyrInntektHendelseId)
        assertHarHendelseIder(3.vedtaksperiode, overstyrInntektHendelseId)
    }

    @Test
    fun `revurdere inntekt slik at det blir brukerutbetaling`() {
        nyttVedtak(1.januar, 31.januar)
        håndterInntektsmelding(
            listOf(Periode(1.januar, 16.januar)), refusjon = Refusjon(
                INGEN,
                null,
                emptyList()
            )
        )
        håndterOverstyrInntekt(inntekt = INNTEKT, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertDiff(0)
        assertTrue(inspektør.utbetaling(1).inspektør.personOppdrag.harUtbetalinger())
        assertTrue(inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.harUtbetalinger()) // opphører arbeidsgiveroppdraget
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).inspektør.personOppdrag))
        assertEquals(17.januar, inspektør.utbetaling(1).arbeidsgiverOppdrag().first().inspektør.datoStatusFom)

        assertEquals(15741, inspektør.utbetaling(1).personOppdrag().nettoBeløp())
        assertEquals(-15741, inspektør.utbetaling(1).arbeidsgiverOppdrag().nettoBeløp())
    }

    @Test
    fun `revurdere inntekt slik at det blir delvis refusjon`() {
        nyttVedtak(1.januar, 31.januar)
        håndterInntektsmelding(
            listOf(Periode(1.januar, 16.januar)), refusjon = Refusjon(
                25000.månedlig,
                null,
                emptyList()
            )
        )
        håndterOverstyrInntekt(inntekt = INNTEKT, skjæringstidspunkt = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertDiff(0)
        assertTrue(inspektør.utbetaling(1).inspektør.personOppdrag.harUtbetalinger())
        assertTrue(inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag.harUtbetalinger()) // opphører arbeidsgiveroppdraget
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).inspektør.personOppdrag))
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).inspektør.arbeidsgiverOppdrag))

        assertEquals(3047, inspektør.utbetaling(1).personOppdrag().nettoBeløp())
        assertEquals(-3047, inspektør.utbetaling(1).arbeidsgiverOppdrag().nettoBeløp())
    }

    @Test
    fun `revurdere mens en periode er til utbetaling`() {
        nyttVedtak(1.januar, 31.januar)
        forlengTilGodkjentVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        assertEquals(1, observatør.vedtakFattetEvent.size)
        assertNull(observatør.vedtakFattetEvent[2.vedtaksperiode.id(ORGNUMMER)])
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(
            2.vedtaksperiode,
            TIL_UTBETALING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING
        )
        assertEquals(2, observatør.vedtakFattetEvent.size)
        assertNotNull(observatør.vedtakFattetEvent[2.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdere mens en periode har feilet i utbetaling`() {
        nyttVedtak(1.januar, 31.januar)
        forlengTilGodkjentVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        håndterUtbetalt(status = Oppdragstatus.FEIL)
        håndterOverstyrInntekt(30000.månedlig, skjæringstidspunkt = 1.januar)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, UTBETALING_FEILET)
    }


    @Test
    fun `refusjonsopplysninger er uendret etter revurdert inntekt`() {
        nyttVedtak(1.januar, 31.januar)
        val refusjonsopplysningerFørRevurdering = inspektør.refusjonsopplysningerFraVilkårsgrunnlag(1.januar).inspektør.refusjonsopplysninger
        håndterOverstyrInntekt(inntekt = 25000.månedlig, skjæringstidspunkt = 1.januar)
        val refusjonsopplysningerEtterRevurdering = inspektør.refusjonsopplysningerFraVilkårsgrunnlag(1.januar).inspektør.refusjonsopplysninger
        assertEquals(refusjonsopplysningerFørRevurdering, refusjonsopplysningerEtterRevurdering)
    }


    private inline fun <reified D: Dag, reified UD: Utbetalingsdag>assertDag(dato: LocalDate, arbeidsgiverbeløp: Inntekt, personbeløp: Inntekt = INGEN, aktuellDagsinntekt: Inntekt = INGEN) {
        inspektør.sykdomshistorikk.inspektør.tidslinje(0)[dato].let {
            assertTrue(it is D) { "Forventet ${D::class.simpleName} men var ${it::class.simpleName}"}
        }
        inspektør.sisteUtbetalingUtbetalingstidslinje()[dato].let {
            assertTrue(it is UD) { "Forventet ${UD::class.simpleName} men var ${it::class.simpleName}"}
            assertEquals(arbeidsgiverbeløp, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(personbeløp, it.økonomi.inspektør.personbeløp)
            assertEquals(aktuellDagsinntekt, it.økonomi.inspektør.aktuellDagsinntekt)
        }
    }

    private fun assertDiff(diff: Int) {
        assertEquals(diff, inspektør.utbetalinger.last().inspektør.nettobeløp)
    }
}