package no.nav.helse.spleis.e2e.revurdering

import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Utdanning
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.Varselkode.RV_OS_2
import no.nav.helse.person.Varselkode.RV_SØ_10
import no.nav.helse.person.Varselkode.RV_SØ_13
import no.nav.helse.person.Varselkode.RV_SØ_15
import no.nav.helse.person.Varselkode.RV_SØ_4
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikk
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Endringskode.UEND
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevurderKorrigertSoknadTest : AbstractEndToEndTest() {

    @Test
    fun `Avsluttet periode får en korrigert søknad med perfekt overlapp - skal sette i gang en revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        håndterYtelser(1.vedtaksperiode)
        assertTrue(inspektør.sykdomstidslinje[17.januar] is Feriedag)
        assertTrue(inspektør.sykdomstidslinje[18.januar] is Feriedag)
        assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[17.januar] is Fridag)
        assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[18.januar] is Fridag)
    }

    @Test
    fun `Avsluttet periode får en overlappende søknad som strekker seg etter vedtaksperiode - skal ikke sette i gang revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSykmelding(Sykmeldingsperiode(15.januar, 15.februar, 100.prosent))
        håndterSøknad(Sykdom(15.januar, 15.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `Avsluttet periode får en overlappende søknad som strekker seg før vedtaksperiode - skal ikke sette i gang revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSykmelding(Sykmeldingsperiode(15.desember(2017), 15.januar, 100.prosent))
        håndterSøknad(Sykdom(15.desember(2017), 15.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `Avsluttet periode får en overlappende søknad som starter etter og slutter før vedtaksperiode - skal sette i gang revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSykmelding(Sykmeldingsperiode(17.januar, 25.januar, 50.prosent))
        håndterSøknad(Sykdom(17.januar, 25.januar, 50.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertEquals(1.januar til 31.januar, inspektør.sykdomstidslinje.periode())
        håndterYtelser(1.vedtaksperiode)
        (1..16).forEach {
            assertEquals(0.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (17..25).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (26..31).forEach {
            assertEquals(100.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Korrigerende søknad med ferie`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        assertEquals(Sykedag::class, inspektør.sykdomstidslinje[17.januar]::class)
        assertEquals(Sykedag::class, inspektør.sykdomstidslinje[18.januar]::class)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(Feriedag::class, inspektør.sykdomstidslinje[17.januar]::class)
        assertEquals(Feriedag::class, inspektør.sykdomstidslinje[18.januar]::class)

        assertVarsel(RV_OS_2)

        val arbeidsgiverOppdrag = inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag
        assertEquals(2, arbeidsgiverOppdrag.size)
        arbeidsgiverOppdrag[0].inspektør.let { utbetalingslinjeInspektør ->
            assertEquals(17.januar, utbetalingslinjeInspektør.fom)
            assertEquals(31.januar, utbetalingslinjeInspektør.tom)
            assertEquals(ENDR, utbetalingslinjeInspektør.endringskode)
            assertEquals(17.januar, utbetalingslinjeInspektør.datoStatusFom)
        }
        arbeidsgiverOppdrag[1].inspektør.let { utbetalingslinjeInspektør ->
            assertEquals(19.januar, utbetalingslinjeInspektør.fom)
            assertEquals(31.januar, utbetalingslinjeInspektør.tom)
            assertEquals(NY, utbetalingslinjeInspektør.endringskode)
            assertNull(utbetalingslinjeInspektør.datoStatusFom)
        }
    }

    @Test
    fun `Korrigerende søknad setter i gang en revurdering på siste skjæringstidspunkt`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent), Ferie(15.mars, 16.mars))
        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

        assertEquals(20, inspektør.sykdomstidslinje.subset(1.mars til 31.mars).inspektør.dagteller[Sykedag::class])
        assertEquals(2, inspektør.sykdomstidslinje.subset(1.mars til 31.mars).inspektør.dagteller[Feriedag::class])
        assertEquals(9, inspektør.sykdomstidslinje.subset(1.mars til 31.mars).inspektør.dagteller[SykHelgedag::class])
    }

    @Test
    fun `Avsluttet periode med en forlengelse får en korrigerende søknad - skal sette i gang revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))

        assertTilstand(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertEquals(21, inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[Sykedag::class])
        assertEquals(2, inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[Feriedag::class])
        assertEquals(8, inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[SykHelgedag::class])
    }

    @Test
    fun `Avsluttet forlengelse får en overlappende søknad - skal sette i gang revurdering`() {
        nyttVedtak(1.januar, 31.januar, 50.prosent)
        forlengVedtak(1.februar, 28.februar, 50.prosent)
        håndterSykmelding(Sykmeldingsperiode(5.februar, 20.februar, 100.prosent))
        håndterSøknad(Sykdom(5.februar, 20.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        håndterYtelser(2.vedtaksperiode)
        (1..4).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
        (5..20).forEach {
            assertEquals(100.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
        (21..28).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Overlappende søknad treffer førstegangsbehandling og forlengelse`() {
        nyttVedtak(1.januar, 31.januar, 50.prosent)
        forlengVedtak(1.februar, 28.februar, 50.prosent)
        håndterSykmelding(Sykmeldingsperiode(15.januar, 15.februar, 100.prosent))
        håndterSøknad(Sykdom(15.januar, 15.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVSLUTTET)
        assertFunksjonellFeil(RV_SØ_13, 2.vedtaksperiode.filter())
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)

    }

    @Test
    fun `Avsluttet forlengelse får en overlappende søknad som slutter etter`() {
        nyttVedtak(1.januar, 31.januar, 50.prosent)
        forlengVedtak(1.februar, 28.februar, 50.prosent)

        håndterSykmelding(Sykmeldingsperiode(15.februar, 15.mars, 100.prosent))
        håndterSøknad(Sykdom(15.februar, 15.mars, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVSLUTTET)
        assertForkastetPeriodeTilstander(3.vedtaksperiode, START, TIL_INFOTRYGD)
        assertFunksjonellFeil(RV_SØ_13, 2.vedtaksperiode.filter())

    }

    @Test
    fun `Avsluttet periode med forlengelse får en overlappende søknad som starter før`() {
        nyttVedtak(1.januar, 31.januar, 50.prosent)
        forlengVedtak(1.februar, 28.februar, 50.prosent)
        håndterSykmelding(Sykmeldingsperiode(15.desember(2017), 15.januar, 100.prosent))
        håndterSøknad(Sykdom(15.desember(2017), 15.januar, 100.prosent))

        assertFunksjonellFeil(RV_SØ_13, 1.vedtaksperiode.filter())
        assertTilstand(3.vedtaksperiode, TIL_INFOTRYGD)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerGodkjenningRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)

        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstand(1.vedtaksperiode, AVSLUTTET)

        (17..21).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (27..31).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (22..26).forEach {
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Fridag)
        }
    }
    @Test
    fun `Korrigerende søknad for periode i AvventerSimuleringRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)

        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstand(1.vedtaksperiode, AVSLUTTET)

        (17..21).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (27..31).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (22..26).forEach {
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Fridag)
        }
    }
    @Test
    fun `Korrigerende søknad for periode i AvventerHistorikkRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)

        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertTilstand(1.vedtaksperiode, AVSLUTTET)

        (17..21).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (27..31).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar].økonomi.inspektør.grad)
        }
        (22..26).forEach {
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Fridag)
        }
    }
    @Test
    fun `Korrigerende søknad for periode i AvventerRevurdering - setter i gang en overstyring av revurderingen`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        nyttVedtak(15.februar, 15.mars, 100.prosent)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(18.januar, Dagtype.Feriedag)))

        assertTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)

        håndterSøknad(Sykdom(15.februar, 15.mars, 100.prosent, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        (15..28).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
        (1..15).forEach {
            assertEquals(50.prosent, inspektør.utbetalingstidslinjer(2.vedtaksperiode)[it.mars].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Korrigerende søknad i AvventerVilkårsprøvingRevurdering - setter i gang en overstyring av revurderingen`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 16.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 16.februar, 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)

        håndterInntektsmelding(listOf(31.januar til 15.februar))

        håndterYtelser(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING_REVURDERING)

        håndterSøknad(Sykdom(31.januar, 16.februar, 100.prosent))
        håndterYtelser(1.vedtaksperiode)

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        (16..16).forEach {
            assertEquals(100.prosent, inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.februar].økonomi.inspektør.grad)
        }
    }

    @Test
    fun `Korrigerende søknad for førstegangsbehandling med forlengelse i avventerGodkjenning - setter i gang en revurdering`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), Ferie(22.januar, 26.januar))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `Korrigerende søknad i AvventerGjennomførtRevurdering - setter i gang en revurdering`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        forlengVedtak(1.februar, 28.februar, 100.prosent)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 18.januar))

        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(
            2.vedtaksperiode,
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

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)

        assertEquals(2, inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[Feriedag::class])
        (17..18).forEach {
            assertTrue(inspektør.utbetalingstidslinjer(1.vedtaksperiode)[it.januar] is Fridag)
        }
    }

    @Test
    fun `Korrigerende søknad for periode i Avsluttet med utdanning gir varsel`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Utdanning(20.januar, 31.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertVarsel(RV_SØ_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Korrigerende søknad for periode i AvventerGodkjenningRevurdering med utdanning gir varsel`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Utdanning(20.januar, 31.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertVarsel(RV_SØ_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Korrigert søknad med friskmelding for avsluttet periode setter ikke i gang revurdering`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(20.januar, 31.januar))

        assertRevurderingUtenEndring(1.vedtaksperiode) {
            håndterYtelser(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
            assertVarsel(RV_SØ_15)
        }

        assertNull(inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[Arbeidsdag::class])
    }

    @Test
    fun `Korrigert søknad med friskmelding for periode i AvventerGodkjenningRevurdering - setter ikke i gang revurdering`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent, 20.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING)
        nullstillTilstandsendringer()

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(20.januar, 31.januar))
        håndterYtelser(1.vedtaksperiode)
        assertNull(inspektør.sykdomstidslinje.subset(1.januar til 31.januar).inspektør.dagteller[Arbeidsdag::class])

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
        assertVarsel(RV_SØ_15)
    }

    @Test
    fun `andre inntektskilder i avsluttet på førstegangsbehandling - skal gi error`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent, 20.prosent), andreInntektskilder = true)

        assertForventetFeil(
            forklaring = "Produkteier ønsker warning, legal ønsker error. for øyeblikket warning",
            nå = {
                assertVarsel(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            },
            ønsket = {
                assertFunksjonellFeil(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
            }
        )
    }

    @Test
    fun `andre inntektskilder til godkjenning på førstegangsbehandling - skal gi error`() {
        tilGodkjenning(1.januar, 31.januar, ORGNUMMER)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent, 20.prosent), andreInntektskilder = true)
        assertForventetFeil(
            forklaring = "Produkteier ønsker warning, legal ønsker error. for øyeblikket warning",
            nå = {
                assertVarsel(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
            },
            ønsket = {
                assertFunksjonellFeil(RV_SØ_10)
                assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
            }
        )
    }

    private fun assertRevurderingUtenEndring(vedtakperiodeId: IdInnhenter, orgnummer: String = ORGNUMMER, block:() -> Unit) {
        val sykdomsHistorikkElementerFør = inspektør(orgnummer).sykdomshistorikk.inspektør.elementer()
        val utbetalingerFør = inspektør(orgnummer).utbetalinger(vedtakperiodeId)
        block()
        val utbetalingerEtter = inspektør(orgnummer).utbetalinger(vedtakperiodeId)
        val sykdomsHistorikkElementerEtter = inspektør(orgnummer).sykdomshistorikk.inspektør.elementer()
        assertEquals(1, utbetalingerEtter.size - utbetalingerFør.size) { "Forventet at det skal være opprettet en utbetaling" }
        assertEquals(UEND, utbetalingerEtter.last().inspektør.arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(0, utbetalingerEtter.last().inspektør.personOppdrag.size)
        assertEquals(sykdomsHistorikkElementerFør, sykdomsHistorikkElementerEtter) { "Forventet at sykdomshistorikken skal være uendret" }
    }
}