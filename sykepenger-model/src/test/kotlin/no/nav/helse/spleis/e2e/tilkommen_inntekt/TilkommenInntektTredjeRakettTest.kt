package no.nav.helse.spleis.e2e.tilkommen_inntekt

import no.nav.helse.Toggle
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.Arbeidstakerkilde
import no.nav.helse.dsl.INNTEKT
import no.nav.helse.dsl.a1
import no.nav.helse.dsl.a2
import no.nav.helse.dsl.assertInntektsgrunnlag
import no.nav.helse.dsl.nyttVedtak
import no.nav.helse.februar
import no.nav.helse.fredag
import no.nav.helse.hendelser.Søknad.InntektFraNyttArbeidsforhold
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Permisjon
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Tilkommen inntekt som støttes`
import no.nav.helse.person.beløp.BeløpstidslinjeTest.Companion.assertBeløpstidslinje
import no.nav.helse.torsdag
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TilkommenInntektTredjeRakettTest : AbstractDslTest() {

    @Test
    fun `Oppretter vedtaksperiode for tilkommen inntekt og legger til inntekt som inntektsendring på behandlingsendring`() = Toggle.TilkommenInntektV3.enable {
        a1 {
            nyttVedtak(januar)
            håndterSøknad(februar, inntekterFraNyeArbeidsforhold = listOf(InntektFraNyttArbeidsforhold(1.februar, 28.februar, a2, 1000)))
            assertEquals(2, inspektør.vedtaksperiodeTeller)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
        }
        a2 {
            assertEquals(1, inspektør.vedtaksperiodeTeller)
            assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            assertBeløpstidslinje(inspektør(1.vedtaksperiode).inntektsendringer, februar, 50.daglig)
        }
        a1 {
            håndterYtelser(2.vedtaksperiode)
            assertVarsler(2.vedtaksperiode, `Tilkommen inntekt som støttes`)
            assertUtbetalingsbeløp(2.vedtaksperiode, 1382, 1431)
        }
    }

    @Test
    fun `Inntekt skal avkortes av tilkommen inntekt før det 6G justeres`() = Toggle.TilkommenInntektV3.enable {
        a1 {
            nyttVedtak(januar, beregnetInntekt = INNTEKT * 3)
            assertUtbetalingsbeløp(1.vedtaksperiode, forventetArbeidsgiverbeløp = 2161, forventetArbeidsgiverRefusjonsbeløp = 4292, subset = 17.januar til 31.januar)
            håndterSøknad(februar, inntekterFraNyeArbeidsforhold = listOf(InntektFraNyttArbeidsforhold(1.februar, 28.februar, a2, 1000)))
            assertEquals(2, inspektør.vedtaksperiodeTeller)
            assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
        }
        a2 {
            assertEquals(1, inspektør.vedtaksperiodeTeller)
            assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
            assertBeløpstidslinje(inspektør(1.vedtaksperiode).inntektsendringer, februar, 50.daglig)
        }
        a1 {
            håndterYtelser(2.vedtaksperiode)
            assertVarsler(2.vedtaksperiode, `Tilkommen inntekt som støttes`)
            assertUtbetalingsbeløp(2.vedtaksperiode, forventetArbeidsgiverbeløp = 2136, forventetArbeidsgiverRefusjonsbeløp = 4292)
        }
    }

    @Test
    fun `Tilkommen inntekt på førstegangsbehandling`() = Toggle.TilkommenInntektV3.enable {
        a1 {
            håndterSøknad(januar, inntekterFraNyeArbeidsforhold = listOf(InntektFraNyttArbeidsforhold(fom = 18.januar, tom = 31.januar, orgnummer = a2, 10_000)))
            /** TODO:  Lolzi, den "fangSisteVedtaksperiode"-tingen om skal håndtere dette for oss i testene har jo nå plukket opp a2 sin vedtaksperiode
            Det kan man sikkert fikse der, men det gadd jeg ikke nå */
            håndterUtbetalingshistorikk(1.vedtaksperiode)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            assertVarsler(1.vedtaksperiode, `Tilkommen inntekt som støttes`)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
            assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, subset = 17.januar.somPeriode()) // Syk og ingen tilkommen her
            assertUtbetalingsbeløp(1.vedtaksperiode, 842, 1431, subset = 18.januar til 31.januar)
            assertInntektsgrunnlag(1.januar, 2) {
                assertInntektsgrunnlag(a1, forventetkilde = Arbeidstakerkilde.Arbeidsgiver, forventetFaktaavklartInntekt = INNTEKT, deaktivert = false)
                assertInntektsgrunnlag(a2, forventetkilde = Arbeidstakerkilde.AOrdningen, forventetFaktaavklartInntekt = INGEN, deaktivert = true)
            }
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }

        a2 {
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            assertBeløpstidslinje(inspektør(1.vedtaksperiode).inntektsendringer, 18.januar til 31.januar, 1000.daglig)
        }
    }

    @Test
    fun `Litt ferie og permisjon endrer smøringen`() = Toggle.TilkommenInntektV3.enable {
        a1 {
            håndterSøknad(
                Sykdom(1.januar, 31.januar, 100.prosent),
                Ferie(torsdag(18.januar), fredag(19.januar)),
                Permisjon(torsdag(25.januar), fredag(26.januar)),
                inntekterFraNyeArbeidsforhold = listOf(InntektFraNyttArbeidsforhold(fom = 18.januar, tom = 31.januar, orgnummer = a2, 10_000))
            )
            /** TODO:  Lolzi, den "fangSisteVedtaksperiode"-tingen om skal håndtere dette for oss i testene har jo nå plukket opp a2 sin vedtaksperiode
            Det kan man sikkert fikse der, men det gadd jeg ikke nå */
            håndterUtbetalingshistorikk(1.vedtaksperiode)
            håndterInntektsmelding(listOf(1.januar til 16.januar))
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            assertVarsler(1.vedtaksperiode, `Tilkommen inntekt som støttes`)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)

            assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, subset = 17.januar.somPeriode()) // Syk og ingen tilkommen her
            assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, subset = 18.januar til 19.januar) // Ferie 🏝️
            assertUtbetalingsbeløp(1.vedtaksperiode, 661, 1431, subset = 20.januar til 24.januar) // Syk OG tilkommen
            assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, subset = 25.januar til 26.januar) // Permisjon 🕵
            assertUtbetalingsbeløp(1.vedtaksperiode, 661, 1431, subset = 27.januar til 31.januar) // Syk OG tilkommen igjen
            assertInntektsgrunnlag(1.januar, 2) {
                assertInntektsgrunnlag(a1, INNTEKT, forventetkilde = Arbeidstakerkilde.Arbeidsgiver, deaktivert = false)
                assertInntektsgrunnlag(a2, INGEN, forventetkilde = Arbeidstakerkilde.AOrdningen, deaktivert = true)
            }
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
        }
        a2 {
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            assertBeløpstidslinje(inspektør(1.vedtaksperiode).inntektsendringer, 18.januar til 31.januar, 1666.daglig)
        }
    }

    @Test
    fun `Sykmeldte oppfatter a2 som tilkommen, men a1 mener (uten å vite det) at a2 er en ghost`() = Toggle.TilkommenInntektV3.enable {
        a1 {
            håndterSøknad(januar, inntekterFraNyeArbeidsforhold = listOf(InntektFraNyttArbeidsforhold(fom = 5.januar, tom = 31.januar, orgnummer = a2, 10_000)))
            /** TODO:  Lolzi, den "fangSisteVedtaksperiode"-tingen om skal håndtere dette for oss i testene har jo nå plukket opp a2 sin vedtaksperiode
            Det kan man sikkert fikse der, men det gadd jeg ikke nå */
            håndterUtbetalingshistorikk(1.vedtaksperiode)
            assertEquals(1.januar, inspektør.vedtaksperioder(1.vedtaksperiode).skjæringstidspunkt)
            assertEquals(januar, inspektør.vedtaksperioder(1.vedtaksperiode).periode)

        }
        a2 {
            assertEquals(1.januar, inspektør.vedtaksperioder(1.vedtaksperiode).skjæringstidspunkt)
            assertEquals(5.januar til 31.januar, inspektør.vedtaksperioder(1.vedtaksperiode).periode)
            assertBeløpstidslinje(inspektør(1.vedtaksperiode).inntektsendringer, 5.januar til 31.januar, 526.daglig)

        }
        a1 {
            håndterInntektsmelding(listOf(5.januar til 20.januar))
            assertEquals(5.januar, inspektør.vedtaksperioder(1.vedtaksperiode).skjæringstidspunkt)
            håndterVilkårsgrunnlagFlereArbeidsgivere(1.vedtaksperiode, a1, a2)// Flex har jo funnet arbeidsgivceren 5.januar, så da finner jo vi og det
            // Så nå er jo plutselig a2 en ghost med inntektsendringer på seg
            assertInntektsgrunnlag(5.januar, 2) {
                assertInntektsgrunnlag(a1, forventetkilde = Arbeidstakerkilde.Arbeidsgiver, forventetFaktaavklartInntekt = INNTEKT)
                assertInntektsgrunnlag(a2, forventetkilde = Arbeidstakerkilde.AOrdningen, forventetFaktaavklartInntekt = INNTEKT)
            }
        }
        a2 {
            assertBeløpstidslinje(inspektør(1.vedtaksperiode).inntektsendringer, 5.januar til 31.januar, 526.daglig)
        }
        a1 {
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
            assertVarsler(1.vedtaksperiode, `Tilkommen inntekt som støttes`)
        }
    }
}
