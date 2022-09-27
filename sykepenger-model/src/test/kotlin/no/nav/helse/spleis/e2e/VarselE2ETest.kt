package no.nav.helse.spleis.e2e

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.OverstyrArbeidsforhold.ArbeidsforholdOverstyrt
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK
import no.nav.helse.person.Varselkode
import no.nav.helse.person.Varselkode.RV_AY_3
import no.nav.helse.person.Varselkode.RV_AY_4
import no.nav.helse.person.Varselkode.RV_AY_5
import no.nav.helse.person.Varselkode.RV_AY_6
import no.nav.helse.person.Varselkode.RV_AY_7
import no.nav.helse.person.Varselkode.RV_AY_8
import no.nav.helse.person.Varselkode.RV_AY_9
import no.nav.helse.person.Varselkode.RV_IM_1
import no.nav.helse.person.Varselkode.RV_IM_2
import no.nav.helse.person.Varselkode.RV_IM_3
import no.nav.helse.person.Varselkode.RV_IM_4
import no.nav.helse.person.Varselkode.RV_IM_5
import no.nav.helse.person.Varselkode.RV_IT_1
import no.nav.helse.person.Varselkode.RV_IT_4
import no.nav.helse.person.Varselkode.RV_IV_1
import no.nav.helse.person.Varselkode.RV_IV_2
import no.nav.helse.person.Varselkode.RV_MV_1
import no.nav.helse.person.Varselkode.RV_MV_2
import no.nav.helse.person.Varselkode.RV_OS_2
import no.nav.helse.person.Varselkode.RV_OS_3
import no.nav.helse.person.Varselkode.RV_OV_1
import no.nav.helse.person.Varselkode.RV_RE_1
import no.nav.helse.person.Varselkode.RV_RV_1
import no.nav.helse.person.Varselkode.RV_SI_1
import no.nav.helse.person.Varselkode.RV_SV_1
import no.nav.helse.person.Varselkode.RV_SV_2
import no.nav.helse.person.Varselkode.RV_SØ_1
import no.nav.helse.person.Varselkode.RV_SØ_10
import no.nav.helse.person.Varselkode.RV_SØ_2
import no.nav.helse.person.Varselkode.RV_SØ_3
import no.nav.helse.person.Varselkode.RV_SØ_4
import no.nav.helse.person.Varselkode.RV_SØ_5
import no.nav.helse.person.Varselkode.RV_SØ_7
import no.nav.helse.person.Varselkode.RV_SØ_8
import no.nav.helse.person.Varselkode.RV_SØ_9
import no.nav.helse.person.Varselkode.RV_VV_1
import no.nav.helse.person.Varselkode.RV_VV_2
import no.nav.helse.person.Varselkode.RV_VV_4
import no.nav.helse.person.Varselkode.RV_VV_8
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class VarselE2ETest: AbstractEndToEndTest() {

    @Test
    fun `varsel - Søknaden inneholder permittering, Vurder om permittering har konsekvens for rett til sykepenger`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            permittert = true
        )
        assertVarsel(RV_SØ_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Minst én dag er avslått på grunn av foreldelse, Vurder å sende vedtaksbrev fra Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), mottatt = 1.januar(2019).atStartOfDay())
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.januar(2019))
        assertVarsel(RV_SØ_2)
    }

    @Test
    fun `varsel - Sykmeldingen er tilbakedatert, vurder fra og med dato for utbetaling`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            merknaderFraSykmelding = listOf(Søknad.Merknad("UGYLDIG_TILBAKEDATERING"))
        )
        assertVarsel(RV_SØ_3, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Utdanning oppgitt i perioden i søknaden`() {
        nyttVedtak(1.januar, 31.januar, 100.prosent)
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            Søknad.Søknadsperiode.Utdanning(20.januar, 31.januar)
        )
        assertVarsel(RV_SØ_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Søknaden inneholder Permisjonsdager utenfor sykdomsvindu`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            Søknad.Søknadsperiode.Permisjon(1.desember(2017), 31.desember(2017)),
        )
        assertVarsel(RV_SØ_5, 1.vedtaksperiode.filter())
    }

    @Test
    fun `søknad med arbeidsdager mellom to perioder bridger ikke de to periodene`(){
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.februar, 28.februar, 100.prosent),
            Søknad.Søknadsperiode.Arbeid(20.januar, 31.januar)
        )
        assertVarsel(RV_SØ_7, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Søknad med utenlandsopphold og studieopphold gir warning`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(3.januar, 26.januar, 100.prosent),
            Søknad.Søknadsperiode.Utlandsopphold(11.januar, 15.januar)
        )
        assertVarsel(RV_SØ_8, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Det er oppgitt annen inntektskilde i søknaden, Vurder inntekt`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            andreInntektskilder = listOf(Søknad.Inntektskilde(true, "ANNET")),
        )
        assertVarsel(RV_SØ_9, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Den sykmeldte har oppgitt å ha andre arbeidsforhold med sykmelding i søknaden`() {
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            andreInntektskilder = listOf(Søknad.Inntektskilde(true, "ANDRE_ARBEIDSFORHOLD"))
        )
        assertVarsel(RV_SØ_10, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Vi har mottatt en inntektsmelding i en løpende sykmeldingsperiode med oppgitt første - bestemmende fraværsdag som er ulik tidligere fastsatt skjæringstidspunkt`() {
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 10.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 23.januar)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(11.januar, 31.januar, 100.prosent))
        assertVarsel(RV_IM_1, 2.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Første fraværsdag i inntektsmeldingen er ulik skjæringstidspunktet, Kontrollér at inntektsmeldingen er knyttet til riktig periode`() {
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 23.januar)
        assertVarsel(RV_IM_2, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Inntektsmeldingen og vedtaksløsningen er uenige om beregningen av arbeidsgiverperioden, Undersøk hva som er riktig arbeidsgiverperiode`() {
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 15.januar, 17.januar til 18.januar))
        assertVarsel(RV_IM_3, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Mottatt flere inntektsmeldinger - den første inntektsmeldingen som ble mottatt er lagt til grunn, Utbetal kun hvis det blir korrekt`() {
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertVarsel(RV_IM_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Den sykmeldte har skiftet arbeidsgiver, og det er beregnet at den nye arbeidsgiveren mottar refusjon lik forrige, kontroller at dagsatsen blir riktig`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, ansattFom = LocalDate.EPOCH, ansattTom = 31.desember(2017)),
                Arbeidsforhold(a2, ansattFom = 1.januar, ansattTom = null)
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertVarsel(RV_VV_8, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Sykmeldte har oppgitt ferie første dag i arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            Søknad.Søknadsperiode.Ferie(1.januar, 20.januar),
            Søknad.Søknadsperiode.Ferie(25.januar, 31.januar)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertForventetFeil(
            nå = {
                assertIngenVarsel(RV_IM_5, 1.vedtaksperiode.filter(ORGNUMMER))
            },
            ønsket = {
                // TODO: https://trello.com/c/92DhehGa
                assertVarsel(RV_IM_5, 1.vedtaksperiode.filter(ORGNUMMER))
            }
        )
    }

    @Test
    fun `varsel - Arbeidsgiver er ikke registrert i Aa-registeret`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            arbeidsforhold = emptyList()
        )
        assertVarsel(RV_VV_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Flere arbeidsgivere, ulikt starttidspunkt for sykefraværet eller ikke fravær fra alle arbeidsforhold`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(4.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(4.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.januar,
            beregnetInntekt = 10000.månedlig,
            orgnummer = a1
        )
        håndterInntektsmelding(
            listOf(4.januar til 19.januar),
            førsteFraværsdag = 4.januar,
            beregnetInntekt = 19000.månedlig,
            orgnummer = a2
        )
        val inntekter = listOf(
            grunnlag(
                a1, finnSkjæringstidspunkt(
                    a1, 1.vedtaksperiode
                ), 10000.månedlig.repeat(3)
            ),
            grunnlag(
                a2, finnSkjæringstidspunkt(
                    a1, 1.vedtaksperiode
                ), 20000.månedlig.repeat(3)
            )
        )
        val arbeidsforhold = listOf(
            Arbeidsforhold(orgnummer = a1, ansattFom = LocalDate.EPOCH, ansattTom = null),
            Arbeidsforhold(orgnummer = a2, ansattFom = LocalDate.EPOCH, ansattTom = null)
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 10000.månedlig.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), 20000.månedlig.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntekter,
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertVarsel(RV_VV_2, 1.vedtaksperiode.filter(a1))
    }

    @Test
    fun `varsel - Minst én dag uten utbetaling på grunn av sykdomsgrad under 20 %, Vurder å sende vedtaksbrev fra Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 19.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 19.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertVarsel(RV_VV_4, 1.vedtaksperiode.filter(a1))
    }

    @Test
    @Disabled
    fun `varsel - Fant ikke refusjonsgrad for perioden - Undersøk oppgitt refusjon før du utbetaler`() {
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 25.februar)
        nyPeriode(1.januar til 10.januar)
        nyPeriode(11.januar til 31.januar)
        nyPeriode(1.februar til 28.februar)
        håndterUtbetalingshistorikk(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        assertVarsel(RV_RE_1, 2.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Det er utbetalt en periode i Infotrygd etter perioden du skal behandle nå - Undersøk at antall forbrukte dager og grunnlag i Infotrygd er riktig`() {
        nyttVedtak(1.januar, 31.januar)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.januar, Dagtype.Sykedag, 80)))
        håndterYtelser(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.mars, 31.mars, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.mars, INNTEKT, true))
        )
        assertVarsel(RV_IT_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Perioden er avslått på grunn av manglende opptjening`() {
        nyPeriode(1.januar til 31.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser()
        håndterVilkårsgrunnlag(arbeidsforhold = listOf(Arbeidsforhold(ORGNUMMER, ansattFom = 31.desember(2017), ansattTom = null)))
        assertVarsel(RV_OV_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel revurdering - Perioden er avslått på grunn av manglende opptjening`() {
        nyPeriode(1.januar til 31.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser()
        håndterVilkårsgrunnlag(
            arbeidsforhold = listOf(Arbeidsforhold(ORGNUMMER, 31.desember(2017), null), Arbeidsforhold(a2, LocalDate.EPOCH, 5.januar)),
            inntektsvurdering = Inntektsvurdering(listOf(sammenligningsgrunnlag(a2, 1.januar, INNTEKT.repeat(12)))),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(grunnlag(a2, 1.januar, 1000.månedlig.repeat(3))),
                arbeidsforhold = emptyList()
            ),
        )
        håndterYtelser()
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()
        assertIngenVarsel(RV_OV_1, 1.vedtaksperiode.filter())
        håndterOverstyrArbeidsforhold(1.januar, listOf(ArbeidsforholdOverstyrt(a2, true, "forklaring")))
        assertVarsel(RV_OV_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Vurder lovvalg og medlemskap`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser()
        håndterVilkårsgrunnlag(medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.VetIkke)

        assertVarsel(RV_MV_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Perioden er avslått på grunn av at den sykmeldte ikke er medlem av Folketrygden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser()
        håndterVilkårsgrunnlag(medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Nei)

        assertVarsel(RV_MV_2, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Bruker har flere inntektskilder de siste tre månedene enn arbeidsforhold som er oppdaget i Aa-registeret`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, orgnummer = a1)
        håndterYtelser(inntektshistorikk = emptyList(), orgnummer = a1)
        håndterVilkårsgrunnlag(orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt 1000.månedlig
                }
            }
        ))
        håndterYtelser(inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSimulering(orgnummer = a1)
        håndterUtbetalingsgodkjenning(utbetalingGodkjent = true, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertVarsel(RV_IV_1, 1.vedtaksperiode.filter(a1))
    }

    @Test
    fun `varsel - Utbetaling i Infotrygd overlapper med vedtaksperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(besvart = LocalDateTime.now().minusYears(1))
        håndterVilkårsgrunnlag()
        håndterYtelser(besvart = LocalDateTime.now().minusYears(1))
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterOverstyrTidslinje((20.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, 1000.daglig),
            inntektshistorikk = listOf(
                Inntektsopplysning(ORGNUMMER, 17.januar, INNTEKT, true)
            )
        )
        assertVarsel(Varselkode.RV_IT_3, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Det er registrert utbetaling på nødnummer`() {
        val nødnummer = "973626108"
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(besvart = LocalDateTime.now().minusYears(1))
        håndterVilkårsgrunnlag()
        håndterYtelser(besvart = LocalDateTime.now().minusYears(1))
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterOverstyrTidslinje((20.januar til 26.januar).map { manuellFeriedag(it) })

        håndterYtelser(1.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(nødnummer, 1.februar, 15.februar, 100.prosent, INNTEKT))
        assertVarsel(RV_IT_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Mangler inntekt for første utbetalingsdag i en av infotrygdperiodene`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(besvart = 31.januar.atStartOfDay())
        håndterVilkårsgrunnlag()
        håndterYtelser(besvart = 31.januar.atStartOfDay())
        håndterSimulering()
        håndterUtbetalingsgodkjenning()
        håndterUtbetalt()

        håndterOverstyrTidslinje(overstyringsdager = listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)))

        håndterYtelser(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.november(2017), 30.november(2017), 100.prosent, 32000.månedlig),
            inntektshistorikk = listOf(
                Inntektsopplysning(
                    orgnummer = ORGNUMMER, sykepengerFom = 1.november, 32000.månedlig, refusjonTilArbeidsgiver = true
                )
            )
        )

        assertVarsel(Varselkode.RV_IT_5, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 100.månedlig)
        håndterYtelser()
        håndterVilkårsgrunnlag(inntekt = 100.månedlig)
        håndterYtelser()
        assertVarsel(RV_SV_1)
    }

    @Test
    fun `varsel - Minst en arbeidsgiver inngår ikke i sykepengegrunnlaget`() {
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Feriedag)), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertVarsel(RV_SV_2)
    }

    @Test
    fun `varsel - Feil under simulering`() {
        nyttVedtak(1.januar, 31.januar)

        håndterOverstyrTidslinje(listOf(manuellFeriedag(18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode, simuleringOK = false)
        assertVarsel(RV_SI_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Simulering av revurdert utbetaling feilet, Utbetalingen må annulleres`() {
        nyttVedtak(1.januar, 31.januar)

        håndterOverstyrTidslinje(listOf(manuellFeriedag(18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode, simuleringOK = false)
        assertVarsel(Varselkode.RV_SI_2, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Har mer enn 25 prosent avvik`() {
        nyPeriode(1.januar til 31.januar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser()
        håndterVilkårsgrunnlag(inntekt = INNTEKT * 2)
        assertIngenVarsel(RV_IV_2, 1.vedtaksperiode.filter())
        assertFunksjonellFeil("Har mer enn 25 % avvik", 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Har mer enn 25 prosent avvik - Dette støttes foreløpig ikke i Speil - Du må derfor annullere periodene`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrInntekt(inntekt = INNTEKT * 2, skjæringstidspunkt = 1.januar)
        håndterYtelser()
        assertIngenFunksjonelleFeil(1.vedtaksperiode.filter())
        assertVarsel(RV_IV_2, 1.vedtaksperiode.filter())
    }

    @Test
    fun `varsel - Denne perioden var tidligere regnet som innenfor arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 16.februar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 16.februar, 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        håndterInntektsmelding(listOf(31.januar til 15.februar))
        assertVarsel(RV_RV_1)
    }

    @Test
    fun `varsel - Endrer tidligere oppdrag, Kontroller simuleringen`(){
        nyttVedtak(3.januar, 26.januar)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((20.januar til 22.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((23.januar til 23.januar).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        assertVarsel(RV_OS_3)
    }

    @Test
    fun `varsel - Utbetalingens fra og med-dato er endret, Kontroller simuleringen`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(
            Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent),
            Søknad.Søknadsperiode.Ferie(17.januar, 18.januar)
        )
        håndterYtelser(1.vedtaksperiode)
        assertVarsel(RV_OS_2)
    }

    @Test
    fun `varsel - Bruker har mottatt AAP innenfor 6 måneder før skjæringstidspunktet - Kontroller at brukeren har rett til sykepenger`() {
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1, arbeidsavklaringspenger = listOf(1.desember(2017) til 15.desember(2017)))

        assertVarsel(RV_AY_3)
    }

    @Test
    fun `varsel - Bruker har mottatt dagpenger innenfor 4 uker før skjæringstidspunktet - Kontroller om bruker er dagpengemottaker - Kombinerte ytelser støttes foreløpig ikke av systemet`() {
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1, dagpenger = listOf(1.desember(2017) til 15.desember(2017)))

        assertVarsel(RV_AY_4)
    }

    @Test
    fun `varsel - Det er mottatt foreldrepenger i samme periode`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode, foreldrepenger = 1.januar til 31.januar)

        assertVarsel(RV_AY_5)
    }

    @Test
    fun `varsel - Det er utbetalt pleiepenger i samme periode`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode, pleiepenger = listOf(1.januar til 31.januar))

        assertVarsel(RV_AY_6)
    }

    @Test
    fun `varsel - Det er utbetalt omsorgspenger i samme periode`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode, omsorgspenger = listOf(1.januar til 31.januar))

        assertVarsel(RV_AY_7)
    }

    @Test
    fun `varsel - Det er utbetalt opplæringspenger i samme periode`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode, opplæringspenger = listOf(1.januar til 31.januar))

        assertVarsel(RV_AY_8)
    }

    @Test
    fun `varsel - Det er institusjonsopphold i perioden - Vurder retten til sykepenger`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Feriedag)))
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(Institusjonsopphold.Institusjonsoppholdsperiode(1.januar, 31.januar)))

        assertVarsel(RV_AY_9)
    }
}