package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Inntektskilde.FLERE_ARBEIDSGIVERE
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Saksbehandler
import no.nav.helse.person.inntekt.SkattComposite
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.spleis.e2e.sammenligningsgrunnlag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class RevurderGhostInntektTest: AbstractDslTest() {

    @Test
    fun `revurder ghost-inntekt ned`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagMedGhostArbeidsforhold(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertPeriode(17.januar til 31.januar, 1080.daglig)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(0, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
            nullstillTilstandsendringer()
            håndterOverstyrInntekt(
                inntekt = 15000.månedlig,
                skjæringstidspunkt = 1.januar,
                organisasjonsnummer = a2
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            assertPeriode(17.januar til 31.januar, 1431.daglig)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(552000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(552000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(26, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(15000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
        }
    }

    @Test
    fun `revurder ghost-inntekt opp`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagMedGhostArbeidsforhold(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertPeriode(17.januar til 31.januar, 1080.daglig)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(0, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
            nullstillTilstandsendringer()
            håndterOverstyrInntekt(
                inntekt = 40000.månedlig,
                skjæringstidspunkt = 1.januar,
                organisasjonsnummer = a2
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertPeriode(17.januar til 31.januar, 943.daglig)
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(852000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(15, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(40000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
        }
    }

    @Test
    fun `revurder ghost-inntekt til 0 kr`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagMedGhostArbeidsforhold(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertPeriode(17.januar til 31.januar, 1080.daglig)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(0, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
            nullstillTilstandsendringer()
            håndterOverstyrInntekt(
                inntekt = 0.månedlig,
                skjæringstidspunkt = 1.januar,
                organisasjonsnummer = a2
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            assertPeriode(17.januar til 31.januar, 1431.daglig)
            assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(372000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(372000.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(50, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(0.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
        }
    }

    @Test
    fun `revurderer tidligere skjæringstidspunkt`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagMedGhostArbeidsforhold(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            // ny periode med nytt skjæringstidspunlt
            håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
            håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
            håndterInntektsmelding(listOf(1.mars til 16.mars), beregnetInntekt = INNTEKT)
            håndterVilkårsgrunnlagMedGhostArbeidsforhold(2.vedtaksperiode, skjæringstidspunkt = 1.mars)
            håndterYtelser(2.vedtaksperiode)
            håndterSimulering(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            håndterUtbetalt()
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
            assertSisteTilstand(2.vedtaksperiode, AVSLUTTET)
            assertPeriode(17.januar til 31.januar, 1080.daglig)
            assertPeriode(17.mars til 31.mars, 1080.daglig)
            håndterOverstyrInntekt(
                inntekt = 15000.månedlig,
                skjæringstidspunkt = 1.januar,
                organisasjonsnummer = a2
            )
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
            assertSisteTilstand(2.vedtaksperiode, AVVENTER_REVURDERING)
            nullstillTilstandsendringer()
            håndterOverstyrInntekt(
                inntekt = 16000.månedlig,
                skjæringstidspunkt = 1.januar,
                organisasjonsnummer = a2
            )
            assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
            assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            håndterYtelser(2.vedtaksperiode)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode)
            assertPeriode(17.januar til 31.januar, 1425.daglig)
            assertPeriode(17.mars til 31.mars, 1080.daglig)
            (inspektør.vilkårsgrunnlag(1.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(564000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(1.vedtaksperiode))
                assertEquals(24, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(16000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Saksbehandler::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
            (inspektør.vilkårsgrunnlag(2.vedtaksperiode)?.inspektør ?: fail { "finner ikke vilkårsgrunnlag" }).also { vilkårsgrunnlag ->
                val sykepengegrunnlagInspektør = vilkårsgrunnlag.sykepengegrunnlag.inspektør
                val sammenligningsgrunnlagInspektør = vilkårsgrunnlag.sammenligningsgrunnlag1.inspektør

                assertEquals(744000.årlig, sykepengegrunnlagInspektør.beregningsgrunnlag)
                assertEquals(561804.årlig, sykepengegrunnlagInspektør.sykepengegrunnlag)
                assertEquals(744000.årlig, sammenligningsgrunnlagInspektør.sammenligningsgrunnlag)
                assertEquals(FLERE_ARBEIDSGIVERE, sykepengegrunnlagInspektør.inntektskilde)
                assertEquals(FLERE_ARBEIDSGIVERE, inspektør.inntektskilde(2.vedtaksperiode))
                assertEquals(0, vilkårsgrunnlag.avviksprosent?.roundToInt())
                assertEquals(2, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
                }
                sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(31000.månedlig, it.inntektsopplysning.omregnetÅrsinntekt())
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                assertEquals(2, sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
                sammenligningsgrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
                    assertEquals(SkattComposite::class, it.inntektsopplysning::class)
                }
            }
            assertTilstander(
                1.vedtaksperiode,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_REVURDERING,
                AVVENTER_GJENNOMFØRT_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_SIMULERING_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                TIL_UTBETALING,
                AVSLUTTET
            )
            assertTilstander(
                2.vedtaksperiode,
                AVVENTER_REVURDERING,
                AVVENTER_GJENNOMFØRT_REVURDERING,
                AVVENTER_HISTORIKK_REVURDERING,
                AVVENTER_GODKJENNING_REVURDERING,
                AVSLUTTET
            )
        }
    }


    private fun TestPerson.TestArbeidsgiver.assertDag(dato: LocalDate, arbeidsgiverbeløp: Inntekt, personbeløp: Inntekt) {
        inspektør(orgnummer).sisteUtbetalingUtbetalingstidslinje()[dato].let {
            if (it is Utbetalingstidslinje.Utbetalingsdag.NavHelgDag) return
            assertEquals(arbeidsgiverbeløp, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(personbeløp, it.økonomi.inspektør.personbeløp)
        }
    }
    private fun TestPerson.TestArbeidsgiver.assertPeriode(
        periode: Periode,
        arbeidsgiverbeløp: Inntekt,
        personbeløp: Inntekt = Inntekt.INGEN
    ) =
        periode.forEach { assertDag(it, arbeidsgiverbeløp, personbeløp) }


    private fun TestPerson.TestArbeidsgiver.håndterVilkårsgrunnlagMedGhostArbeidsforhold(vedtaksperiode: UUID, skjæringstidspunkt: LocalDate = 1.januar, inntekt: Inntekt = INNTEKT) {
        håndterVilkårsgrunnlag(
            vedtaksperiode,
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH, null),
                Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017), null)
            ),
            inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, skjæringstidspunkt, inntekt.repeat(12)),
                    sammenligningsgrunnlag(a2, skjæringstidspunkt, inntekt.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, skjæringstidspunkt, inntekt.repeat(3)),
                    grunnlag(a2, skjæringstidspunkt, inntekt.repeat(3))
                ),
                arbeidsforhold = emptyList()
            )
        )
    }
}
