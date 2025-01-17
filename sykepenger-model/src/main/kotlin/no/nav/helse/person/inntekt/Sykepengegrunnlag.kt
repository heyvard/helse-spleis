package no.nav.helse.person.inntekt

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.Grunnbeløp
import no.nav.helse.Grunnbeløp.Companion.`2G`
import no.nav.helse.Grunnbeløp.Companion.halvG
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.til
import no.nav.helse.person.GhostPeriode
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.Inntektskilde
import no.nav.helse.person.Opptjening
import no.nav.helse.person.SykepengegrunnlagVisitor
import no.nav.helse.person.Varselkode
import no.nav.helse.person.Varselkode.RV_IV_2
import no.nav.helse.person.Varselkode.RV_SV_1
import no.nav.helse.person.Varselkode.RV_SV_2
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.aktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.build
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.deaktiver
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.erOverstyrt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.harInntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.inneholderAlleArbeidsgivereI
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.inntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.medInntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.medUtbetalingsopplysninger
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.nyeRefusjonsopplysninger
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.omregnetÅrsinntekt
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.overstyrInntekter
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.refusjonsopplysninger
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.subsummer
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning.Companion.valider
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.person.inntekt.Sykepengegrunnlag.Begrensning.ER_6G_BEGRENSET
import no.nav.helse.person.inntekt.Sykepengegrunnlag.Begrensning.ER_IKKE_6G_BEGRENSET
import no.nav.helse.person.inntekt.Sykepengegrunnlag.Begrensning.VURDERT_I_INFOTRYGD
import no.nav.helse.utbetalingstidslinje.Alder
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Prosent
import no.nav.helse.økonomi.Økonomi

internal class Sykepengegrunnlag(
    private val alder: Alder,
    private val skjæringstidspunkt: LocalDate,
    private val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
    private val deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>,
    private val vurdertInfotrygd: Boolean,
    private val skjønnsmessigFastsattBeregningsgrunnlag: Inntekt? = null,
    `6G`: Inntekt? = null
) : Comparable<Inntekt> {
    private val `6G`: Inntekt = `6G` ?: Grunnbeløp.`6G`.beløp(skjæringstidspunkt, LocalDate.now())
    private val beregningsgrunnlag: Inntekt = skjønnsmessigFastsattBeregningsgrunnlag ?: arbeidsgiverInntektsopplysninger.omregnetÅrsinntekt()
    private val sykepengegrunnlag = beregningsgrunnlag.coerceAtMost(this.`6G`)
    private val begrensning = if (vurdertInfotrygd) VURDERT_I_INFOTRYGD else if (beregningsgrunnlag > this.`6G`) ER_6G_BEGRENSET else ER_IKKE_6G_BEGRENSET

    private val forhøyetInntektskrav = alder.forhøyetInntektskrav(skjæringstidspunkt)
    private val minsteinntekt = (if (forhøyetInntektskrav) `2G` else halvG).minsteinntekt(skjæringstidspunkt)
    private val oppfyllerMinsteinntektskrav = beregningsgrunnlag >= minsteinntekt

    internal constructor(
        alder: Alder,
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        skjæringstidspunkt: LocalDate,
        subsumsjonObserver: SubsumsjonObserver,
        vurdertInfotrygd: Boolean = false
    ) : this(alder, skjæringstidspunkt, arbeidsgiverInntektsopplysninger, emptyList(), vurdertInfotrygd) {
        subsumsjonObserver.apply {
            arbeidsgiverInntektsopplysninger.subsummer(this)
            `§ 8-10 ledd 2 punktum 1`(
                erBegrenset = begrensning == ER_6G_BEGRENSET,
                maksimaltSykepengegrunnlag = `6G`,
                skjæringstidspunkt = skjæringstidspunkt,
                beregningsgrunnlag = beregningsgrunnlag
            )
            if (alder.forhøyetInntektskrav(skjæringstidspunkt))
                `§ 8-51 ledd 2`(oppfyllerMinsteinntektskrav, skjæringstidspunkt, alder.alderPåDato(skjæringstidspunkt), beregningsgrunnlag, minsteinntekt)
            else
                `§ 8-3 ledd 2 punktum 1`(oppfyllerMinsteinntektskrav, skjæringstidspunkt, beregningsgrunnlag, minsteinntekt)
        }
    }

    internal companion object {
        private val varsel: IAktivitetslogg.(Varselkode) -> Unit = IAktivitetslogg::varsel
        private val funksjonellFeil: IAktivitetslogg.(Varselkode) -> Unit = IAktivitetslogg::funksjonellFeil

        fun opprett(
            alder: Alder,
            arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
            skjæringstidspunkt: LocalDate,
            subsumsjonObserver: SubsumsjonObserver
        ): Sykepengegrunnlag {
            return Sykepengegrunnlag(alder, arbeidsgiverInntektsopplysninger, skjæringstidspunkt, subsumsjonObserver)
        }
    }

    internal fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode) {
        val tidslinjeperiode = Utbetalingstidslinje.periode(tidslinjer)
        if (tidslinjeperiode.starterEtter(skjæringstidspunktperiode) || tidslinjeperiode.endInclusive < skjæringstidspunkt) return

        val avvisningsperiode = skjæringstidspunktperiode.start til minOf(tidslinjeperiode.endInclusive, skjæringstidspunktperiode.endInclusive)
        val avvisteDager = avvisningsperiode.filter { dato ->
            val faktor = if (alder.forhøyetInntektskrav(dato)) `2G` else halvG
            beregningsgrunnlag < faktor.minsteinntekt(skjæringstidspunkt)
        }
        if (avvisteDager.isEmpty()) return
        val (avvisteDagerOver67, avvisteDagerTil67) = avvisteDager.partition { alder.forhøyetInntektskrav(it) }
        if (avvisteDagerOver67.isNotEmpty()) Utbetalingstidslinje.avvis(tidslinjer, avvisteDagerOver67.grupperSammenhengendePerioder(), listOf(Begrunnelse.MinimumInntektOver67))
        if (avvisteDagerTil67.isNotEmpty()) Utbetalingstidslinje.avvis(tidslinjer, avvisteDagerTil67.grupperSammenhengendePerioder(), listOf(Begrunnelse.MinimumInntekt))
    }

    internal fun valider(aktivitetslogg: IAktivitetslogg): Boolean {
        arbeidsgiverInntektsopplysninger.valider(aktivitetslogg)
        if (oppfyllerMinsteinntektskrav) aktivitetslogg.info("Krav til minste sykepengegrunnlag er oppfylt")
        else aktivitetslogg.varsel(RV_SV_1)
        return oppfyllerMinsteinntektskrav && !aktivitetslogg.harFunksjonelleFeilEllerVerre()
    }

    internal fun harNødvendigInntektForVilkårsprøving(organisasjonsnummer: String) =
        arbeidsgiverInntektsopplysninger.harInntekt(organisasjonsnummer)

    internal fun validerInntekt(
        aktivitetslogg: IAktivitetslogg,
        organisasjonsnummer: List<String>
    ) {
        val manglerInntekt = organisasjonsnummer.filterNot { harNødvendigInntektForVilkårsprøving(it) }.takeUnless { it.isEmpty() } ?: return
        manglerInntekt.forEach {
            aktivitetslogg.info("Mangler inntekt for $it på skjæringstidspunkt $skjæringstidspunkt")
        }
        valideringstrategi()(aktivitetslogg, RV_SV_2)
    }

    internal fun validerAvvik(aktivitetslogg: IAktivitetslogg, sammenligningsgrunnlag: Sammenligningsgrunnlag) {
        val avvik = avviksprosent(sammenligningsgrunnlag.sammenligningsgrunnlag, SubsumsjonObserver.NullObserver)
        if (harAkseptabeltAvvik(avvik)) aktivitetslogg.info("Har %.0f %% eller mindre avvik i inntekt (%.2f %%)", Prosent.MAKSIMALT_TILLATT_AVVIK_PÅ_ÅRSINNTEKT.prosent(), avvik.prosent())
        else valideringstrategi()(aktivitetslogg, RV_IV_2)
    }

    private fun harAkseptabeltAvvik(avvik: Prosent) = avvik <= Prosent.MAKSIMALT_TILLATT_AVVIK_PÅ_ÅRSINNTEKT

    private fun valideringstrategi(): IAktivitetslogg.(Varselkode) -> Unit {
        return when {
            erOverstyrt() -> varsel
            else -> funksjonellFeil
        }
    }

    private fun erOverstyrt() = deaktiverteArbeidsforhold.isNotEmpty() || arbeidsgiverInntektsopplysninger.erOverstyrt()

    internal fun aktiver(orgnummer: String, forklaring: String, subsumsjonObserver: SubsumsjonObserver) =
        deaktiverteArbeidsforhold.aktiver(arbeidsgiverInntektsopplysninger, orgnummer, forklaring, subsumsjonObserver)
            .let { (deaktiverte, aktiverte) ->
                kopierSykepengegrunnlag(arbeidsgiverInntektsopplysninger = aktiverte, deaktiverteArbeidsforhold = deaktiverte)
            }

    internal fun deaktiver(orgnummer: String, forklaring: String, subsumsjonObserver: SubsumsjonObserver) =
        arbeidsgiverInntektsopplysninger.deaktiver(deaktiverteArbeidsforhold, orgnummer, forklaring, subsumsjonObserver)
            .let { (aktiverte, deaktiverte) ->
                kopierSykepengegrunnlag(arbeidsgiverInntektsopplysninger = aktiverte, deaktiverteArbeidsforhold = deaktiverte)
            }

    internal fun overstyrArbeidsforhold(hendelse: OverstyrArbeidsforhold, subsumsjonObserver: SubsumsjonObserver): Sykepengegrunnlag {
        return hendelse.overstyr(this, subsumsjonObserver)
    }

    internal fun overstyrArbeidsgiveropplysninger(hendelse: OverstyrArbeidsgiveropplysninger, opptjening: Opptjening?, subsumsjonObserver: SubsumsjonObserver): Sykepengegrunnlag? {
        val builder = SaksbehandlerOverstyringer(arbeidsgiverInntektsopplysninger, opptjening, subsumsjonObserver)
        hendelse.overstyr(builder)
        val resultat = builder.resultat() ?: return null
        return kopierSykepengegrunnlag(resultat, deaktiverteArbeidsforhold)
    }

    internal fun refusjonsopplysninger(organisasjonsnummer: String): Refusjonsopplysninger =
        arbeidsgiverInntektsopplysninger.refusjonsopplysninger(organisasjonsnummer)

    internal fun inntekt(organisasjonsnummer: String): Inntekt? =
        arbeidsgiverInntektsopplysninger.inntekt(organisasjonsnummer)

    internal fun nyeRefusjonsopplysninger(inntektsmelding: Inntektsmelding): Sykepengegrunnlag? {
        val builder = NyeRefusjonsopplysninger(arbeidsgiverInntektsopplysninger)
        inntektsmelding.nyeRefusjonsopplysninger(builder)
        val resultat = builder.resultat() ?: return  null // ingen endring
        return kopierSykepengegrunnlag(resultat, deaktiverteArbeidsforhold)
    }

    private fun kopierSykepengegrunnlag(
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        deaktiverteArbeidsforhold: List<ArbeidsgiverInntektsopplysning>
    ) =
        Sykepengegrunnlag(
            alder = alder,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiverInntektsopplysninger = arbeidsgiverInntektsopplysninger,
            deaktiverteArbeidsforhold = deaktiverteArbeidsforhold,
            vurdertInfotrygd = vurdertInfotrygd,
            skjønnsmessigFastsattBeregningsgrunnlag = skjønnsmessigFastsattBeregningsgrunnlag
        )

    internal fun justerGrunnbeløp() = kopierSykepengegrunnlag(arbeidsgiverInntektsopplysninger, deaktiverteArbeidsforhold)

    internal fun accept(visitor: SykepengegrunnlagVisitor) {
        visitor.preVisitSykepengegrunnlag(
            this,
            skjæringstidspunkt,
            sykepengegrunnlag,
            skjønnsmessigFastsattBeregningsgrunnlag,
            beregningsgrunnlag,
            `6G`,
            begrensning,
            vurdertInfotrygd,
            minsteinntekt,
            oppfyllerMinsteinntektskrav
        )
        visitor.preVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektsopplysninger)
        arbeidsgiverInntektsopplysninger.forEach { it.accept(visitor) }
        visitor.postVisitArbeidsgiverInntektsopplysninger(arbeidsgiverInntektsopplysninger)

        visitor.preVisitDeaktiverteArbeidsgiverInntektsopplysninger(deaktiverteArbeidsforhold)
        deaktiverteArbeidsforhold.forEach { it.accept(visitor) }
        visitor.postVisitDeaktiverteArbeidsgiverInntektsopplysninger(deaktiverteArbeidsforhold)
        visitor.postVisitSykepengegrunnlag(
            this,
            skjæringstidspunkt,
            sykepengegrunnlag,
            skjønnsmessigFastsattBeregningsgrunnlag,
            beregningsgrunnlag,
            `6G`,
            begrensning,
            vurdertInfotrygd,
            minsteinntekt,
            oppfyllerMinsteinntektskrav
        )
    }

    internal fun avviksprosent(sammenligningsgrunnlag: Inntekt, subsumsjonObserver: SubsumsjonObserver) = beregningsgrunnlag.avviksprosent(sammenligningsgrunnlag).also { avvik ->
        subsumsjonObserver.`§ 8-30 ledd 2 punktum 1`(Prosent.MAKSIMALT_TILLATT_AVVIK_PÅ_ÅRSINNTEKT, beregningsgrunnlag, sammenligningsgrunnlag, avvik)
    }
    internal fun inntektskilde() = when {
        arbeidsgiverInntektsopplysninger.size > 1 -> Inntektskilde.FLERE_ARBEIDSGIVERE
        else -> Inntektskilde.EN_ARBEIDSGIVER
    }
    internal fun harInntektFraAOrdningen() =
        arbeidsgiverInntektsopplysninger.any { it.harInntektFraAOrdningen() }
    internal fun erRelevant(organisasjonsnummer: String) = arbeidsgiverInntektsopplysninger.any {
        it.gjelder(organisasjonsnummer)
    }

    internal fun utenInntekt(økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?): Økonomi {
        return økonomi.inntekt(
            aktuellDagsinntekt = INGEN,
            dekningsgrunnlag = INGEN,
            skjæringstidspunkt = skjæringstidspunkt,
            `6G` = `6G`,
            arbeidsgiverperiode = arbeidsgiverperiode,
            refusjonsbeløp = INGEN
        )
    }
    internal fun medInntekt(organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?, regler: ArbeidsgiverRegler, subsumsjonObserver: SubsumsjonObserver): Økonomi {
        return arbeidsgiverInntektsopplysninger.medInntekt(organisasjonsnummer, `6G`, skjæringstidspunkt, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver) ?: utenInntekt(økonomi, arbeidsgiverperiode)
    }

    internal fun medUtbetalingsopplysninger(organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?, regler: ArbeidsgiverRegler, subsumsjonObserver: SubsumsjonObserver, manglerRefusjonsopplysning: ManglerRefusjonsopplysning): Økonomi {
        return arbeidsgiverInntektsopplysninger.medUtbetalingsopplysninger(organisasjonsnummer, `6G`, skjæringstidspunkt, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver, manglerRefusjonsopplysning)
    }

    internal fun build(builder: VedtakFattetBuilder) {
        builder
            .sykepengegrunnlag(this.sykepengegrunnlag)
            .beregningsgrunnlag(this.beregningsgrunnlag)
            .begrensning(this.begrensning)
        arbeidsgiverInntektsopplysninger.build(builder)
    }
    override fun equals(other: Any?): Boolean {
        if (other !is Sykepengegrunnlag) return false
        return sykepengegrunnlag == other.sykepengegrunnlag
                 && arbeidsgiverInntektsopplysninger == other.arbeidsgiverInntektsopplysninger
                 && beregningsgrunnlag == other.beregningsgrunnlag
                 && begrensning == other.begrensning
                 && deaktiverteArbeidsforhold == other.deaktiverteArbeidsforhold
    }

    override fun hashCode(): Int {
        var result = sykepengegrunnlag.hashCode()
        result = 31 * result + arbeidsgiverInntektsopplysninger.hashCode()
        result = 31 * result + beregningsgrunnlag.hashCode()
        result = 31 * result + begrensning.hashCode()
        result = 31 * result + deaktiverteArbeidsforhold.hashCode()
        return result
    }
    override fun compareTo(other: Inntekt) = this.sykepengegrunnlag.compareTo(other)
    internal fun er6GBegrenset() = begrensning == ER_6G_BEGRENSET

    internal fun ghostPeriode(sisteId: UUID, vilkårsgrunnlagId: UUID, organisasjonsnummer: String, periode: Periode): GhostPeriode? {
        val opplysning = arbeidsgiverInntektsopplysninger.firstOrNull { it.gjelder(organisasjonsnummer) }
            ?: deaktiverteArbeidsforhold.firstOrNull { it.gjelder(organisasjonsnummer) }
        if (opplysning == null || opplysning.ikkeGhost()) return null
        val erDeaktivert = deaktiverteArbeidsforhold.any { it === opplysning }
        return GhostPeriode(
            fom = periode.start,
            tom = periode.endInclusive,
            skjæringstidspunkt = skjæringstidspunkt,
            vilkårsgrunnlagHistorikkInnslagId = sisteId,
            vilkårsgrunnlagId = vilkårsgrunnlagId,
            deaktivert = erDeaktivert
        )
    }

    enum class Begrensning {
        ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD
    }

    internal class SaksbehandlerOverstyringer(
        private val opprinneligArbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        private val opptjening: Opptjening?,
        private val subsumsjonObserver: SubsumsjonObserver
    ) {
        private val nyeInntektsopplysninger = mutableListOf<ArbeidsgiverInntektsopplysning>()

        internal fun leggTilInntekt(arbeidsgiverInntektsopplysning: ArbeidsgiverInntektsopplysning) {
            nyeInntektsopplysninger.add(arbeidsgiverInntektsopplysning)
        }

        internal fun resultat(): List<ArbeidsgiverInntektsopplysning>? {
            check(opprinneligArbeidsgiverInntektsopplysninger.inneholderAlleArbeidsgivereI(nyeInntektsopplysninger)) {
                "De nye arbeidsgiveropplysningene inneholder arbeidsgivere som ikke er en del av sykepengegrunnlaget."
            }
            return opprinneligArbeidsgiverInntektsopplysninger.overstyrInntekter(opptjening, nyeInntektsopplysninger, subsumsjonObserver).takeUnless { resultat ->
                resultat == opprinneligArbeidsgiverInntektsopplysninger
            }
        }
    }

}
internal class NyeRefusjonsopplysninger(private val opprinneligArbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>) {
    private var nyeOpplysninger = opprinneligArbeidsgiverInntektsopplysninger

    internal fun leggTilRefusjonsopplysninger(organisasjonsnummer: String, refusjonsopplysninger: Refusjonsopplysninger) {
        nyeOpplysninger = nyeOpplysninger.nyeRefusjonsopplysninger(organisasjonsnummer, refusjonsopplysninger)
    }

    internal fun resultat() = nyeOpplysninger.takeUnless { nyeOpplysninger == opprinneligArbeidsgiverInntektsopplysninger }
}
