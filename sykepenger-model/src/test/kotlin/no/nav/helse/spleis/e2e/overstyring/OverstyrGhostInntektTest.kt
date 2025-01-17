package no.nav.helse.spleis.e2e.overstyring

import java.time.LocalDate
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag.Arbeidsforhold
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.november
import no.nav.helse.person.Inntektskilde.FLERE_ARBEIDSGIVERE
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.Varselkode.RV_IV_2
import no.nav.helse.person.inntekt.IkkeRapportert
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.SkattComposite
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertInntektForDato
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterOverstyrInntekt
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.spleis.e2e.sammenligningsgrunnlag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class OverstyrGhostInntektTest : AbstractEndToEndTest() {

    private companion object {
        const val a1 = "987654321"
        const val a2 = "654321987"
    }

    @Test
    fun `Overstyrer ghost-inntekt -- happy case`() {
        tilOverstyring(
            sammenligningsgrunnlag = mapOf(a1 to 30000.månedlig, a2 to 1000.månedlig),
            sykepengegrunnlag = mapOf(a1 to 30000.månedlig, a2 to 1000.månedlig),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH, null),
                Arbeidsforhold(a2, 1.november(2017), null)
            )
        )
        håndterOverstyrInntekt(500.månedlig, a2, 1.januar)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
        val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

        assertEquals(378000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(378000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(372000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(2, vilkårsgrunnlag.avviksprosent?.roundToInt())
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(500.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
            assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
        }

        assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(SkattComposite::class, it.inntektsopplysning::class)
        }
        sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(SkattComposite::class, it.inntektsopplysning::class)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, orgnummer = a1)
    }

    @Test
    fun `Ved overstyring av inntekt til under krav til minste sykepengegrunnlag skal vi lage en utbetaling uten utbetaling`() {
        tilOverstyring(
            sammenligningsgrunnlag = mapOf(a1 to 3750.månedlig, a2 to 416.månedlig),
            sykepengegrunnlag = mapOf(a1 to 3750.månedlig, a2 to 416.månedlig),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH, null),
                Arbeidsforhold(a2, 1.november(2017), null)
            ),
            beregnetInntekt = 3750.månedlig
        )
        håndterOverstyrInntekt(INGEN, a2, 1.januar)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        val utbetalinger = inspektør.utbetalinger
        assertEquals(2, utbetalinger.size)
        assertEquals(0, utbetalinger.last().inspektør.arbeidsgiverOppdrag.nettoBeløp())
        Assertions.assertTrue(utbetalinger.last().erAvsluttet())
        Assertions.assertTrue(utbetalinger.first().inspektør.erForkastet)

    }

    @Test
    fun `Overstyr ghost-inntekt -- ghost har ingen inntekt fra før av`() {
        tilOverstyring(
            sammenligningsgrunnlag = mapOf(a1 to 30000.månedlig),
            sykepengegrunnlag = mapOf(a1 to 30000.månedlig),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH, null),
                Arbeidsforhold(a2, 1.desember(2017), null)
            )
        )
        håndterOverstyrInntekt(500.månedlig, a2, 1.januar)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
        val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

        assertEquals(378000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(378000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(360000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(5, vilkårsgrunnlag.avviksprosent?.roundToInt())
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(500.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
            assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
        }

        assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(SkattComposite::class, it.inntektsopplysning::class)
        }
        sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(IkkeRapportert::class, it.inntektsopplysning::class)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, orgnummer = a1)
    }

    @Test
    fun `Kan ikke overstyre ghost-inntekt for en forlengelse som allerede har tidligere utbetalinger`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH, null),
                Arbeidsforhold(a2, 1.desember(2017), null)
            )
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = true)
        håndterUtbetalt()
        // ny periode
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        val skjæringstidspunkt = inspektør.skjæringstidspunkt(2.vedtaksperiode)
        assertEquals(listOf(a1, a2).toList(), person.relevanteArbeidsgivere(skjæringstidspunkt).toList())
        nullstillTilstandsendringer()
        håndterOverstyrInntekt(30000.månedlig, a2, skjæringstidspunkt)
        assertEquals(listOf(a1, a2), person.relevanteArbeidsgivere(skjæringstidspunkt))
        assertInntektForDato(INNTEKT, 1.januar, inspektør = inspektør(a1))
        assertInntektForDato(30000.månedlig, 1.januar, inspektør = inspektør(a2))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `overstyring av ghostinntekt som fører til 25 prosent avvik skal gi error`() {
        tilOverstyring(
            sammenligningsgrunnlag = mapOf(a1 to 30000.månedlig, a2 to 30000.månedlig),
            sykepengegrunnlag = mapOf(a1 to 30000.månedlig, a2 to 30000.månedlig),
            arbeidsforhold = listOf(
                Arbeidsforhold(a1, LocalDate.EPOCH, null),
                Arbeidsforhold(a2, 1.november(2017), null)
            )
        )
        håndterOverstyrInntekt(500.månedlig, a2, 1.januar)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
        val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

        assertEquals(378000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
        assertEquals(378000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
        assertEquals(720000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
        assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
        assertEquals(FLERE_ARBEIDSGIVERE, inspektør(a1).inntektskilde(1.vedtaksperiode))
        assertEquals(48, vilkårsgrunnlag.avviksprosent?.roundToInt())
        assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(500.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
            assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
        }

        assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
        sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(SkattComposite::class, it.inntektsopplysning::class)
        }
        sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(30000.månedlig, it.inntektsopplysning.rapportertInntekt())
            assertEquals(SkattComposite::class, it.inntektsopplysning::class)
        }

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, orgnummer = a1)

        assertVarsel(RV_IV_2)
        assertIngenFunksjonelleFeil()
    }

    private fun tilOverstyring(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        sammenligningsgrunnlag: Map<String, Inntekt>,
        sykepengegrunnlag: Map<String, Inntekt>,
        arbeidsforhold: List<Arbeidsforhold>,
        beregnetInntekt: Inntekt = INNTEKT
    ) {
        håndterSykmelding(Sykmeldingsperiode(fom, tom, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(fom, tom, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(fom til fom.plusDays(15)), beregnetInntekt = beregnetInntekt, orgnummer = a1)

        val inntektsvurdering = sammenligningsgrunnlag.keys.map { orgnummer ->
            sammenligningsgrunnlag(orgnummer, fom, sammenligningsgrunnlag[orgnummer]!!.repeat(12))
        }
        val inntektForSykepengegrunnlag = sykepengegrunnlag.keys.map { orgnummer ->
            grunnlag(orgnummer, fom, sykepengegrunnlag[orgnummer]!!.repeat(3))
        }
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(inntektsvurdering),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = inntektForSykepengegrunnlag, arbeidsforhold = emptyList()),
            arbeidsforhold = arbeidsforhold,
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
    }
}
