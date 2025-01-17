package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Grunnbeløp
import no.nav.helse.april
import no.nav.helse.desember
import no.nav.helse.erHelg
import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.AbstractPersonTest
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.etterlevelse.SubsumsjonObserver.Companion.NullObserver
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger.RefusjonsopplysningerBuilder
import no.nav.helse.spleis.e2e.AbstractEndToEndTest.Companion.INNTEKT
import no.nav.helse.sykepengegrunnlag
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.NAVDAGER
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.Alder.Companion.alder
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.properties.Delegates

internal class SykepengegrunnlagTest {
    private companion object {
        private val fødseldato67år =  1.februar(1954)
    }

    @Test
    fun equality() {
        val sykepengegrunnlag = INNTEKT.sykepengegrunnlag
        assertEquals(sykepengegrunnlag, sykepengegrunnlag)
        assertEquals(sykepengegrunnlag, INNTEKT.sykepengegrunnlag)
        assertEquals(INNTEKT.sykepengegrunnlag, INNTEKT.sykepengegrunnlag)
        assertNotEquals(INNTEKT.sykepengegrunnlag, INNTEKT.sykepengegrunnlag("annet orgnr"))
        assertNotEquals(INNTEKT.sykepengegrunnlag, INNTEKT.sykepengegrunnlag(31.desember))
    }

    @Test
    fun `minimum inntekt tom 67 år - må være 0,5 G`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val halvG = Grunnbeløp.halvG.beløp(skjæringstidspunkt)

        var observer = MinsteinntektSubsumsjonObservatør()
        val sykepengegrunnlag = halvG.sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var aktivitetslogg = Aktivitetslogg()
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(halvG, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(observer.`§ 8-3 ledd 2 punktum 1`)

        observer = MinsteinntektSubsumsjonObservatør()
        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (halvG - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(halvG, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
        assertFalse(observer.`§ 8-3 ledd 2 punktum 1`)
    }

    @Test
    fun `minimum inntekt etter 67 år - må være 2 G`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 2.februar(2021)
        val `2G` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)

        var observer = MinsteinntektSubsumsjonObservatør()
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)

        observer = MinsteinntektSubsumsjonObservatør()
        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
        assertFalse(observer.`§ 8-51 ledd 2`)
    }

    @Test
    fun `kan ikke avvise dager på tidslinje som er før`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val forLitenInntekt = Grunnbeløp.halvG.beløp(skjæringstidspunkt) - 1.daglig
        val sykepengegrunnlag = (forLitenInntekt).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = 1.januar)
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX)
        assertEquals(0, tidslinje.inspektør.avvistDagTeller)
    }

    @Test
    fun `minimum inntekt før fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val forLitenInntekt = Grunnbeløp.halvG.beløp(skjæringstidspunkt) - 1.daglig

        val sykepengegrunnlag = (forLitenInntekt).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX)
        assertEquals(41, tidslinje.inspektør.avvistDagTeller)
        assertTrue((1.januar(2021) til 1.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                tidslinje.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntekt
            }
        )
        assertTrue((2.februar(2021) til 28.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                tidslinje.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
            }
        )
    }

    @Test
    fun `minimum inntekt etter fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.mars(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(20.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX)
        assertEquals(20, tidslinje.inspektør.avvistDagTeller)
        assertTrue(tidslinje.inspektør.avvistedatoer.all { dato ->
            tidslinje.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
        })
    }

    @Test
    fun `minimum inntekt ved overgang til 67 år - var innenfor før fylte 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX)
        assertEquals(27, tidslinje.inspektør.avvistDagTeller)
        assertTrue(tidslinje.inspektør.avvistedatoer.all { dato ->
            tidslinje.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
        })
    }

    @Test
    fun `avviser ikke dager utenfor skjæringstidspunktperioden`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.januar(2021)
        val `1G` = Grunnbeløp.`1G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`1G`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, NullObserver)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAVDAGER, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til 31.januar(2021))
        assertEquals(0, tidslinje.inspektør.avvistDagTeller)
    }

    @Test
    fun `mindre enn 2G, men skjæringstidspunkt er før virkningen av minsteinntekt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 23.mai(2021)
        val `2G_2021` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        val `2G_2020` = Grunnbeløp.`2G`.beløp(30.april(2021))

        val observer = MinsteinntektSubsumsjonObservatør()
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G_2021`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertEquals(`2G_2020`, Grunnbeløp.`2G`.minsteinntekt(skjæringstidspunkt))
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2020`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)

        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G_2021` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt, observer)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertTrue(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2020`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())
        assertTrue(observer.`§ 8-51 ledd 2`)
    }
    @Test
    fun `mindre enn 2G, og skjæringstidspunkt er etter virkningen av minsteinntekt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 24.mai(2021)
        val `2G_2021` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        var aktivitetslogg = Aktivitetslogg()
        val sykepengegrunnlag = (`2G_2021`).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)
        var validert = sykepengegrunnlag.valider(aktivitetslogg)
        assertEquals(`2G_2021`, Grunnbeløp.`2G`.minsteinntekt(skjæringstidspunkt))
        assertTrue(sykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2021`, sykepengegrunnlag.inspektør.minsteinntekt)
        assertTrue(validert)
        assertFalse(aktivitetslogg.harVarslerEllerVerre())

        aktivitetslogg = Aktivitetslogg()
        val forLiteSykepengegrunnlag = (`2G_2021` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)
        validert = forLiteSykepengegrunnlag.valider(aktivitetslogg)
        assertFalse(forLiteSykepengegrunnlag.inspektør.oppfyllerMinsteinntektskrav)
        assertEquals(`2G_2021`, forLiteSykepengegrunnlag.inspektør.minsteinntekt)
        assertFalse(validert)
        assertTrue(aktivitetslogg.harVarslerEllerVerre())
    }

    @Test
    fun `begrunnelse tom 67 år - minsteinntekt oppfylt`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val inntekt = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)
        val sykepengegrunnlag = (inntekt).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(31.NAV, 28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX)
        assertEquals(0, tidslinje.inspektør.avvistDagTeller)
    }

    @Test
    fun `begrunnelse tom 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 1.februar(2021)
        val halvG = Grunnbeløp.halvG.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (halvG - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(28.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX)
        assertEquals(20, tidslinje.inspektør.avvistDagTeller)
        assertTrue((1.februar(2021) til 1.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                tidslinje.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntekt
            }
        )
        assertTrue((2.februar(2021) til 28.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                tidslinje.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
            }
        )
    }
    @Test
    fun `begrunnelse etter 67 år`() {
        val alder = fødseldato67år.alder
        val skjæringstidspunkt = 2.februar(2021)
        val `2G` = Grunnbeløp.`2G`.beløp(skjæringstidspunkt)

        val sykepengegrunnlag = (`2G` - 1.daglig).sykepengegrunnlag(alder, "orgnr", skjæringstidspunkt)

        val tidslinje = tidslinjeOf(27.NAV, startDato = skjæringstidspunkt, skjæringstidspunkter = listOf(skjæringstidspunkt))
        sykepengegrunnlag.avvis(listOf(tidslinje), skjæringstidspunkt til LocalDate.MAX)
        assertEquals(19, tidslinje.inspektør.avvistDagTeller)
        assertTrue((2.februar(2021) til 28.februar(2021))
            .filterNot { it.erHelg() }
            .all { dato ->
                tidslinje.inspektør.begrunnelse(dato).single() == Begrunnelse.MinimumInntektOver67
            }
        )
    }

    @Test
    fun `justerer grunnbeløpet`() {
        val sykepengegrunnlag = 60000.månedlig.sykepengegrunnlag("orgnr", 1.mai(2020), 1.mai(2020))
        val justert = sykepengegrunnlag.justerGrunnbeløp()
        assertNotEquals(sykepengegrunnlag, justert)
        assertNotEquals(sykepengegrunnlag.inspektør.sykepengegrunnlag, justert.inspektør.sykepengegrunnlag)
        assertNotEquals(sykepengegrunnlag.inspektør.`6G`, justert.inspektør.`6G`)
        assertTrue(sykepengegrunnlag.inspektør.`6G` < justert.inspektør.`6G`)
        assertTrue(sykepengegrunnlag.inspektør.sykepengegrunnlag < justert.inspektør.sykepengegrunnlag)
        assertNull(sykepengegrunnlag.inspektør.skjønnsmessigFastsattÅrsinntekt)
    }

    @Test
    fun `sykepengegrunnlaget skal ikke rundes av - 6g-begrenset`() {
        val `6G` = Grunnbeløp.`6G`.beløp(1.januar)
        val sykepengegrunnlag = `6G`.sykepengegrunnlag
        assertEquals(`6G`, sykepengegrunnlag.inspektør.sykepengegrunnlag)
    }
    @Test
    fun `sykepengegrunnlaget skal ikke rundes av - under 6`() {
        val daglig = 255.5.daglig
        val sykepengegrunnlag = daglig.sykepengegrunnlag
        assertEquals(daglig, sykepengegrunnlag.inspektør.sykepengegrunnlag)
    }

    @Test
    fun `overstyrt sykepengegrunnlag`() {
        val inntekt = 10000.månedlig
        val overstyrt = 15000.månedlig
        val sykepengegrunnlag = Sykepengegrunnlag(
            alder = AbstractPersonTest.UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = 1.januar,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning("orgnr", Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), inntekt), Refusjonsopplysninger())
            ),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false,
            skjønnsmessigFastsattBeregningsgrunnlag = overstyrt
        )
        assertNotEquals(inntekt, sykepengegrunnlag.inspektør.sykepengegrunnlag)
        assertEquals(overstyrt, sykepengegrunnlag.inspektør.sykepengegrunnlag)
        assertEquals(overstyrt, sykepengegrunnlag.inspektør.skjønnsmessigFastsattÅrsinntekt)
    }

    @Test
    fun `overstyre inntekt og refusjon - endre til samme`() {
        val opprinnelig = listOf(
            ArbeidsgiverInntektsopplysning("a1", Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 25000.månedlig), RefusjonsopplysningerBuilder().apply {
                leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
            }.build()),
            ArbeidsgiverInntektsopplysning("a2", Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig), RefusjonsopplysningerBuilder().apply {
                leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
            }.build()),
        )
        val overstyring = Sykepengegrunnlag.SaksbehandlerOverstyringer(opprinnelig, null, NullObserver)
        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", Saksbehandler(1.januar, UUID.randomUUID(), 25000.månedlig, "", null, LocalDateTime.now()), RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build())
        overstyring.leggTilInntekt(endretOpplysning)

        assertNull(overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - endrer kun refusjon`() {
        val a1Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 25000.månedlig)
        val a2Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig)
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.SaksbehandlerOverstyringer(opprinnelig, null, NullObserver)
        val a1EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 2000.månedlig), LocalDateTime.now())
        }.build()
        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", Saksbehandler(1.januar, UUID.randomUUID(), 25000.månedlig, "", null, LocalDateTime.now()), a1EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysning)

        val forventetOpplysning = ArbeidsgiverInntektsopplysning("a1", a1Inntektsopplysning, a1EndretRefusjonsopplysninger)
        assertEquals(listOf(forventetOpplysning, a2Opplysning), overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - endrer kun inntekt`() {
        val a1Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 25000.månedlig)
        val a2Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig)
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.SaksbehandlerOverstyringer(opprinnelig, null, NullObserver)

        val a1EndretInntektsopplysning = Saksbehandler(1.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val a1EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()

        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", a1EndretInntektsopplysning, a1EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysning)

        val forventetOpplysning = ArbeidsgiverInntektsopplysning("a1", a1EndretInntektsopplysning, a1Refusjonsopplysninger)
        assertEquals(listOf(forventetOpplysning, a2Opplysning), overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - forsøker å endre Infotrygd-inntekt`() {
        val a1Inntektsopplysning = Infotrygd(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig)
        val a2Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig)
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.SaksbehandlerOverstyringer(opprinnelig, null, NullObserver)

        val a1EndretInntektsopplysning = Saksbehandler(1.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val a1EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()

        val endretOpplysning = ArbeidsgiverInntektsopplysning("a1", a1EndretInntektsopplysning, a1EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysning)

        assertNull(overstyring.resultat())
    }
    @Test
    fun `overstyre inntekt og refusjon - forsøker å legge til ny arbeidsgiver`() {
        val a1Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 25000.månedlig)
        val a2Inntektsopplysning = Inntektsmelding(UUID.randomUUID(), 1.januar, UUID.randomUUID(), 5000.månedlig)
        val a2Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, INGEN), LocalDateTime.now())
        }.build()
        val a1Refusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()
        val a1Opplysning = ArbeidsgiverInntektsopplysning("a1", a1Inntektsopplysning, a1Refusjonsopplysninger)
        val a2Opplysning = ArbeidsgiverInntektsopplysning("a2", a2Inntektsopplysning, a2Refusjonsopplysninger)
        val opprinnelig = listOf(a1Opplysning, a2Opplysning)

        val overstyring = Sykepengegrunnlag.SaksbehandlerOverstyringer(opprinnelig, null, NullObserver)

        val a3EndretInntektsopplysning = Saksbehandler(1.januar, UUID.randomUUID(), 20000.månedlig, "", null, LocalDateTime.now())
        val a3EndretRefusjonsopplysninger = RefusjonsopplysningerBuilder().apply {
            leggTil(Refusjonsopplysning(UUID.randomUUID(), 1.januar, null, 25000.månedlig), LocalDateTime.now())
        }.build()

        val endretOpplysning = ArbeidsgiverInntektsopplysning("a3", a3EndretInntektsopplysning, a3EndretRefusjonsopplysninger)
        overstyring.leggTilInntekt(endretOpplysning)

        assertEquals("De nye arbeidsgiveropplysningene inneholder arbeidsgivere som ikke er en del av sykepengegrunnlaget.", assertThrows<IllegalStateException> { overstyring.resultat() }.message)
    }

    @Test
    fun equals() {
        val inntektID = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()
        val tidsstempel = LocalDateTime.now()
        val sykepengegrunnlag1 = Sykepengegrunnlag(
            alder = AbstractPersonTest.UNG_PERSON_FØDSELSDATO.alder,
            skjæringstidspunkt = 1.januar,
            arbeidsgiverInntektsopplysninger = listOf(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = "orgnummer",
                    inntektsopplysning = Infotrygd(
                        id = inntektID,
                        dato = 1.januar,
                        hendelseId = hendelseId,
                        beløp = 25000.månedlig,
                        tidsstempel = tidsstempel
                    ),
                    refusjonsopplysninger = Refusjonsopplysninger()
                )
            ),
            deaktiverteArbeidsforhold = emptyList(),
            vurdertInfotrygd = false
        )

        assertEquals(
            sykepengegrunnlag1,
            Sykepengegrunnlag(
                alder = AbstractPersonTest.UNG_PERSON_FØDSELSDATO.alder,
                skjæringstidspunkt = 1.januar,
                arbeidsgiverInntektsopplysninger = listOf(
                    ArbeidsgiverInntektsopplysning(
                        orgnummer = "orgnummer",
                        inntektsopplysning = Infotrygd(
                            id = inntektID,
                            dato = 1.januar,
                            hendelseId = hendelseId,
                            beløp = 25000.månedlig,
                            tidsstempel = tidsstempel
                        ),
                        refusjonsopplysninger = Refusjonsopplysninger()
                    )
                ),
                deaktiverteArbeidsforhold = emptyList(),
                vurdertInfotrygd = false,
                skjønnsmessigFastsattBeregningsgrunnlag = 25000.månedlig
            )
        )
        assertEquals(sykepengegrunnlag1, sykepengegrunnlag1.justerGrunnbeløp()) { "grunnbeløpet trenger ikke justering" }
        assertNotEquals(
            sykepengegrunnlag1,
            Sykepengegrunnlag(
                alder = AbstractPersonTest.UNG_PERSON_FØDSELSDATO.alder,
                skjæringstidspunkt = 1.januar,
                arbeidsgiverInntektsopplysninger = emptyList(),
                deaktiverteArbeidsforhold = emptyList(),
                vurdertInfotrygd = false,
                skjønnsmessigFastsattBeregningsgrunnlag = 25000.månedlig
            )
        )
        assertNotEquals(
            sykepengegrunnlag1,
            Sykepengegrunnlag(
                alder = AbstractPersonTest.UNG_PERSON_FØDSELSDATO.alder,
                skjæringstidspunkt = 1.januar,
                arbeidsgiverInntektsopplysninger = listOf(
                    ArbeidsgiverInntektsopplysning(
                        orgnummer = "orgnummer",
                        inntektsopplysning = Infotrygd(
                            id = inntektID,
                            dato = 1.januar,
                            hendelseId = hendelseId,
                            beløp = 25000.månedlig,
                            tidsstempel = tidsstempel
                        ),
                        refusjonsopplysninger = Refusjonsopplysninger()
                    )
                ),
                deaktiverteArbeidsforhold = emptyList(),
                vurdertInfotrygd = false,
                skjønnsmessigFastsattBeregningsgrunnlag = 20000.månedlig
            )
        )
        assertNotEquals(
            sykepengegrunnlag1,
            Sykepengegrunnlag(
                alder = AbstractPersonTest.UNG_PERSON_FØDSELSDATO.alder,
                skjæringstidspunkt = 1.januar,
                arbeidsgiverInntektsopplysninger = listOf(
                    ArbeidsgiverInntektsopplysning(
                        orgnummer = "orgnummer",
                        inntektsopplysning = Infotrygd(
                            id = inntektID,
                            dato = 1.januar,
                            hendelseId = hendelseId,
                            beløp = 25000.månedlig,
                            tidsstempel = tidsstempel
                        ),
                        refusjonsopplysninger = Refusjonsopplysninger()
                    )
                ),
                deaktiverteArbeidsforhold = emptyList(),
                vurdertInfotrygd = true,
                skjønnsmessigFastsattBeregningsgrunnlag = 25000.månedlig
            )
        )
        assertNotEquals(
            sykepengegrunnlag1,
            Sykepengegrunnlag(
                alder = AbstractPersonTest.UNG_PERSON_FØDSELSDATO.alder,
                skjæringstidspunkt = 1.januar,
                arbeidsgiverInntektsopplysninger = listOf(
                    ArbeidsgiverInntektsopplysning(
                        orgnummer = "orgnummer",
                        inntektsopplysning = Infotrygd(
                            id = inntektID,
                            dato = 1.januar,
                            hendelseId = hendelseId,
                            beløp = 25000.månedlig,
                            tidsstempel = tidsstempel
                        ),
                        refusjonsopplysninger = Refusjonsopplysninger()
                    )
                ),
                deaktiverteArbeidsforhold = listOf(
                    ArbeidsgiverInntektsopplysning(
                        orgnummer = "orgnummer",
                        inntektsopplysning = Infotrygd(
                            id = inntektID,
                            dato = 1.januar,
                            hendelseId = hendelseId,
                            beløp = 25000.månedlig,
                            tidsstempel = tidsstempel
                        ),
                        refusjonsopplysninger = Refusjonsopplysninger()
                    )
                ),
                vurdertInfotrygd = false
            )
        )
    }

    private class MinsteinntektSubsumsjonObservatør : SubsumsjonObserver {
        var `§ 8-3 ledd 2 punktum 1` by Delegates.notNull<Boolean>()
        var `§ 8-51 ledd 2` by Delegates.notNull<Boolean>()

        override fun `§ 8-3 ledd 2 punktum 1`(
            oppfylt: Boolean,
            skjæringstidspunkt: LocalDate,
            beregningsgrunnlag: Inntekt,
            minimumInntekt: Inntekt
        ) {
            this.`§ 8-3 ledd 2 punktum 1` = oppfylt
        }

        override fun `§ 8-51 ledd 2`(
            oppfylt: Boolean,
            skjæringstidspunkt: LocalDate,
            alderPåSkjæringstidspunkt: Int,
            beregningsgrunnlag: Inntekt,
            minimumInntekt: Inntekt
        ) {
           this.`§ 8-51 ledd 2` = oppfylt
        }
    }
}