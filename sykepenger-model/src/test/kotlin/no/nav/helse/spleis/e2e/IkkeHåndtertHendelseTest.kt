package no.nav.helse.spleis.e2e

import no.nav.helse.desember
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class IkkeHåndtertHendelseTest : AbstractEndToEndTest() {
    @Test
    fun `håndterer hendelse_ikke_håndtert ved korrigerende søknad med friskmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar), førsteFraværsdag = 3.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        val søknadId = håndterSøknad(Sykdom(3.januar, 26.januar, 100.prosent), Arbeid(20.januar, 26.januar))

        val hendelseIkkeHåndtert = observatør.hendelseIkkeHåndtert(søknadId)
        assertNotNull(hendelseIkkeHåndtert)
        assertEquals(
            listOf("Mottatt flere søknader for perioden - siste søknad inneholder arbeidsdag"),
            hendelseIkkeHåndtert?.årsaker
        )
    }

    @Test
    fun `oppretter ikke sykmeldingsperiode dersom sykmelding er for gammel`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100.prosent), mottatt = 5.desember.atStartOfDay())
        assertEquals(0, observatør.hendelseIkkeHåndtertEventer.size)
        assertEquals(emptyList<Periode>(), inspektør.sykmeldingsperioder())
        assertFunksjonellFeil("Søknadsperioden kan ikke være eldre enn 6 måneder fra mottattidspunkt")
    }

    @Test
    fun `tar bare med errors som er relatert til hendelse`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 50.prosent))
        håndterSøknad(Sykdom(3.januar, 26.januar, 50.prosent))
        håndterInntektsmelding(listOf(3.januar til 18.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        person.håndter(ytelser(1.vedtaksperiode)) // for å legge på en feil som ikke skal være med i hendelse_ikke_håndtert
        håndterUtbetalt()

        val søknadId = håndterSøknad(Sykdom(3.januar, 26.januar, 50.prosent, 20.prosent))

        val hendelseIkkeHåndtert = observatør.hendelseIkkeHåndtert(søknadId)
        assertNotNull(hendelseIkkeHåndtert)
        assertEquals(
            listOf("Bruker har oppgitt at de har jobbet mindre enn sykmelding tilsier"),
            hendelseIkkeHåndtert?.årsaker
        )
    }
}