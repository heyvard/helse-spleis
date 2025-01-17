package no.nav.helse.person

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.person.Varselkode.RV_IT_16
import no.nav.helse.person.Varselkode.RV_VV_11
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement.Companion.skjæringstidspunktperioder
import no.nav.helse.person.builders.VedtakFattetBuilder
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.inntekt.ManglerRefusjonsopplysning
import no.nav.helse.person.inntekt.Sammenligningsgrunnlag
import no.nav.helse.person.inntekt.Sykepengegrunnlag
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.Arbeidsgiverperiode
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Prosent
import no.nav.helse.økonomi.Økonomi

internal class VilkårsgrunnlagHistorikk private constructor(private val historikk: MutableList<Innslag>) {

    internal constructor() : this(mutableListOf())

    private fun sisteInnlag() = historikk.firstOrNull()

    private fun forrigeInnslag() = historikk.elementAtOrNull(1)

    internal fun accept(visitor: VilkårsgrunnlagHistorikkVisitor) {
        visitor.preVisitVilkårsgrunnlagHistorikk()
        historikk.forEach { it.accept(visitor) }
        visitor.postVisitVilkårsgrunnlagHistorikk()
    }

    internal fun lagre(vararg vilkårsgrunnlag: VilkårsgrunnlagElement) {
        if (vilkårsgrunnlag.isEmpty()) return
        val siste = sisteInnlag()
        val nytt = Innslag(siste, vilkårsgrunnlag.toList())
        if (nytt == siste) return
        historikk.add(0, nytt)
    }

    internal fun sisteId() = sisteInnlag()!!.id

    internal fun build(skjæringstidspunkt: LocalDate, builder: VedtakFattetBuilder) {
        vilkårsgrunnlagFor(skjæringstidspunkt)?.build(builder)
    }

    internal fun oppdaterHistorikk(aktivitetslogg: IAktivitetslogg, skjæringstidspunkter: List<LocalDate>) {
        val nyttInnslag = sisteInnlag()?.oppdaterHistorikk(aktivitetslogg, skjæringstidspunkter) ?: return
        if (nyttInnslag == sisteInnlag()) return
        historikk.add(0, nyttInnslag)
    }

    internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
        sisteInnlag()?.vilkårsgrunnlagFor(skjæringstidspunkt)

    internal fun vilkårsgrunnlagIdFor(skjæringstidspunkt: LocalDate) =
        sisteInnlag()?.vilkårsgrunnlagIdFor(skjæringstidspunkt)

    internal fun avvisInngangsvilkår(tidslinjer: List<Utbetalingstidslinje>) {
        sisteInnlag()?.avvis(tidslinjer)
    }

    internal fun skjæringstidspunkterFraSpleis() = sisteInnlag()?.skjæringstidspunkterFraSpleis() ?: emptySet()

    internal fun erRelevant(organisasjonsnummer: String, skjæringstidspunkter: List<LocalDate>) =
        sisteInnlag()?.erRelevant(organisasjonsnummer, skjæringstidspunkter) ?: false

    internal fun medInntekt(organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?, regler: ArbeidsgiverRegler, subsumsjonObserver: SubsumsjonObserver) =
        sisteInnlag()!!.medInntekt(organisasjonsnummer, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver)

    internal fun medUtbetalingsopplysninger(organisasjonsnummer: String, dato: LocalDate, økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?, regler: ArbeidsgiverRegler, subsumsjonObserver: SubsumsjonObserver, manglerRefusjonsopplysning: ManglerRefusjonsopplysning) =
        sisteInnlag()!!.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver, manglerRefusjonsopplysning)

    internal fun utenInntekt(dato: LocalDate, økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?) =
        sisteInnlag()!!.utenInntekt(dato, økonomi, arbeidsgiverperiode)

    internal fun blitt6GBegrensetSidenSist(skjæringstidspunkt: LocalDate): Boolean {
        if (sisteInnlag()?.vilkårsgrunnlagFor(skjæringstidspunkt)?.er6GBegrenset() == false) return false
        return forrigeInnslag()?.vilkårsgrunnlagFor(skjæringstidspunkt)?.er6GBegrenset() == false
    }

    internal fun ghostPeriode(skjæringstidspunkt: LocalDate, organisasjonsnummer: String, periode: Periode): GhostPeriode? {
        return vilkårsgrunnlagFor(skjæringstidspunkt)?.ghostPeriode(sisteId(), organisasjonsnummer, periode)
    }

    internal class Innslag private constructor(
        internal val id: UUID,
        private val opprettet: LocalDateTime,
        private val vilkårsgrunnlag: MutableMap<LocalDate, VilkårsgrunnlagElement>
    ) {
        internal constructor(vilkårsgrunnlag: Map<LocalDate, VilkårsgrunnlagElement>) : this(
            UUID.randomUUID(),
            LocalDateTime.now(),
            vilkårsgrunnlag.toMutableMap()
        )

        internal constructor(other: Innslag?, elementer: List<VilkårsgrunnlagElement>) : this(other?.vilkårsgrunnlag?.toMap()?: emptyMap()) {
            elementer.forEach { it.add(this) }
        }

        internal fun accept(visitor: VilkårsgrunnlagHistorikkVisitor) {
            visitor.preVisitInnslag(this, id, opprettet)
            vilkårsgrunnlag.forEach { (_, element) ->
                element.accept(visitor)
            }
            visitor.postVisitInnslag(this, id, opprettet)
        }

        internal fun add(skjæringstidspunkt: LocalDate, vilkårsgrunnlagElement: VilkårsgrunnlagElement) {
            vilkårsgrunnlag[skjæringstidspunkt] = vilkårsgrunnlagElement
        }

        internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) =
            vilkårsgrunnlag[skjæringstidspunkt]

        internal fun vilkårsgrunnlagIdFor(skjæringstidspunkt: LocalDate) =
            vilkårsgrunnlag[skjæringstidspunkt]?.id()

        internal fun skjæringstidspunkterFraSpleis() = vilkårsgrunnlag
            .filterValues { it is Grunnlagsdata }
            .keys

        internal fun erRelevant(organisasjonsnummer: String, skjæringstidspunkter: List<LocalDate>) =
            skjæringstidspunkter.mapNotNull(vilkårsgrunnlag::get)
                .any { it.erRelevant(organisasjonsnummer) }

        internal fun avvis(tidslinjer: List<Utbetalingstidslinje>) {
            val skjæringstidspunktperioder = skjæringstidspunktperioder(vilkårsgrunnlag.values)
            vilkårsgrunnlag.forEach { (skjæringstidspunkt, element) ->
                val periode = checkNotNull(skjæringstidspunktperioder.singleOrNull { it.start == skjæringstidspunkt })
                element.avvis(tidslinjer, periode)
            }
        }

        internal fun medInntekt(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            arbeidsgiverperiode: Arbeidsgiverperiode?,
            regler: ArbeidsgiverRegler,
            subsumsjonObserver: SubsumsjonObserver
        ) = VilkårsgrunnlagElement.medInntekt(vilkårsgrunnlag.values, organisasjonsnummer, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver)

        internal fun medUtbetalingsopplysninger(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            arbeidsgiverperiode: Arbeidsgiverperiode?,
            regler: ArbeidsgiverRegler,
            subsumsjonObserver: SubsumsjonObserver,
            manglerRefusjonsopplysning: ManglerRefusjonsopplysning
        ) = VilkårsgrunnlagElement.medUtbetalingsopplysninger(vilkårsgrunnlag.values, organisasjonsnummer, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver, manglerRefusjonsopplysning)

        internal fun utenInntekt(
            dato: LocalDate,
            økonomi: Økonomi,
            arbeidsgiverperiode: Arbeidsgiverperiode?
        ): Økonomi {
            return VilkårsgrunnlagElement.utenInntekt(vilkårsgrunnlag.values, dato, økonomi, arbeidsgiverperiode)
        }

        override fun hashCode(): Int {
            return this.vilkårsgrunnlag.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Innslag) return false
            return this.vilkårsgrunnlag == other.vilkårsgrunnlag
        }

        internal fun oppdaterHistorikk(aktivitetslogg: IAktivitetslogg, skjæringstidspunkter: List<LocalDate>): Innslag {
            val gyldigeVilkårsgrunnlag = vilkårsgrunnlag.filter { (key, _)-> key in skjæringstidspunkter }
            val diff = this.vilkårsgrunnlag.size - gyldigeVilkårsgrunnlag.size
            if (diff > 0) aktivitetslogg.info("Fjernet $diff vilkårsgrunnlagselementer")
            return Innslag(gyldigeVilkårsgrunnlag)
        }

        internal companion object {
            fun gjenopprett(
                id: UUID,
                opprettet: LocalDateTime,
                elementer: Map<LocalDate, VilkårsgrunnlagElement>
            ): Innslag = Innslag(id, opprettet, elementer.toMutableMap())
        }
    }

    internal abstract class VilkårsgrunnlagElement(
        protected val vilkårsgrunnlagId: UUID,
        protected val skjæringstidspunkt: LocalDate,
        protected val sykepengegrunnlag: Sykepengegrunnlag,
        private val opptjening: Opptjening?
    ) : Aktivitetskontekst {
        internal fun add(innslag: Innslag) {
            innslag.add(skjæringstidspunkt, this)
        }

        internal fun valider(aktivitetslogg: IAktivitetslogg, organisasjonsnummer: List<String>, erForlengelse: Boolean): Boolean {
            sykepengegrunnlag.validerInntekt(aktivitetslogg, organisasjonsnummer)
            valider(aktivitetslogg, erForlengelse)
            return !aktivitetslogg.harFunksjonelleFeilEllerVerre()
        }

        protected open fun valider(aktivitetslogg: IAktivitetslogg, erForlengelse: Boolean) {}

        internal abstract fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor)
        internal open fun inngårISammenligningsgrunnlaget(organisasjonsnummer: String): Boolean = false
        internal fun build(builder: VedtakFattetBuilder) {
            sykepengegrunnlag.build(builder)
        }

        internal fun erRelevant(organisasjonsnummer: String): Boolean {
            return sykepengegrunnlag.erRelevant(organisasjonsnummer) || inngårISammenligningsgrunnlaget(organisasjonsnummer)
        }
        internal fun inntektskilde() = sykepengegrunnlag.inntektskilde()

        internal fun id() = vilkårsgrunnlagId

        internal open fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode) {}

        final override fun toSpesifikkKontekst() = SpesifikkKontekst(
            kontekstType = "vilkårsgrunnlag",
            kontekstMap = mapOf(
                "vilkårsgrunnlagId" to vilkårsgrunnlagId.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                "vilkårsgrunnlagtype" to vilkårsgrunnlagtype()
            )
        )

        internal fun overstyrArbeidsgiveropplysninger(hendelse: OverstyrArbeidsgiveropplysninger, subsumsjonObserver: SubsumsjonObserver): VilkårsgrunnlagElement? {
            val sykepengegrunnlag = sykepengegrunnlag.overstyrArbeidsgiveropplysninger(hendelse, opptjening, subsumsjonObserver) ?: return null
            return kopierMed(hendelse, sykepengegrunnlag, opptjening, subsumsjonObserver)
        }
        protected abstract fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonObserver: SubsumsjonObserver
        ): VilkårsgrunnlagElement

        abstract fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonObserver: SubsumsjonObserver
        ): VilkårsgrunnlagElement?

        internal fun nyeRefusjonsopplysninger(inntektsmelding: Inntektsmelding): VilkårsgrunnlagElement? {
            val sykepengegrunnlag = sykepengegrunnlag.nyeRefusjonsopplysninger(inntektsmelding) ?: return null
            return kopierMed(inntektsmelding, sykepengegrunnlag, opptjening, SubsumsjonObserver.NullObserver)
        }

        protected abstract fun vilkårsgrunnlagtype(): String
        private fun medInntekt(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            arbeidsgiverperiode: Arbeidsgiverperiode?,
            regler: ArbeidsgiverRegler,
            subsumsjonObserver: SubsumsjonObserver
        ) = sykepengegrunnlag.medInntekt(organisasjonsnummer, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver)

        private fun medUtbetalingsopplysninger(
            organisasjonsnummer: String,
            dato: LocalDate,
            økonomi: Økonomi,
            arbeidsgiverperiode: Arbeidsgiverperiode?,
            regler: ArbeidsgiverRegler,
            subsumsjonObserver: SubsumsjonObserver,
            manglerRefusjonsopplysning: ManglerRefusjonsopplysning
        ) = sykepengegrunnlag.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver, manglerRefusjonsopplysning)

        private fun utenInntekt(økonomi: Økonomi, arbeidsgiverperiode: Arbeidsgiverperiode?): Økonomi {
            return sykepengegrunnlag.utenInntekt(økonomi, arbeidsgiverperiode)
        }

        internal fun er6GBegrenset() = sykepengegrunnlag.er6GBegrenset()

        internal fun harNødvendigInntektForVilkårsprøving(organisasjonsnummer: String) =
            sykepengegrunnlag.harNødvendigInntektForVilkårsprøving(organisasjonsnummer)

        internal fun refusjonsopplysninger(organisasjonsnummer: String) =
            sykepengegrunnlag.refusjonsopplysninger(organisasjonsnummer)

        internal abstract fun ghostPeriode(sisteId: UUID, organisasjonsnummer: String, periode: Periode): GhostPeriode?

        internal fun inntekt(organisasjonsnummer: String): Inntekt? =
            sykepengegrunnlag.inntekt(organisasjonsnummer)

        internal companion object {
            internal fun skjæringstidspunktperioder(elementer: Collection<VilkårsgrunnlagElement>): List<Periode> {
                val skjæringstidspunkter = elementer
                    .map { it.skjæringstidspunkt }
                    .sorted()
                return skjæringstidspunkter
                    .mapIndexed { index, skjæringstidspunkt ->
                        val sisteDag = skjæringstidspunkter.elementAtOrNull(index + 1)?.forrigeDag ?: LocalDate.MAX
                        skjæringstidspunkt til sisteDag
                    }
            }

            internal fun medInntekt(
                elementer: Collection<VilkårsgrunnlagElement>,
                organisasjonsnummer: String,
                dato: LocalDate,
                økonomi: Økonomi,
                arbeidsgiverperiode: Arbeidsgiverperiode?,
                regler: ArbeidsgiverRegler,
                subsumsjonObserver: SubsumsjonObserver
            ): Økonomi {
                return finnVilkårsgrunnlag(elementer, dato)?.medInntekt(
                    organisasjonsnummer,
                    dato,
                    økonomi,
                    arbeidsgiverperiode,
                    regler,
                    subsumsjonObserver
                ) ?: utenInntekt(elementer, dato, økonomi, arbeidsgiverperiode)
            }

            internal fun medUtbetalingsopplysninger(
                elementer: Collection<VilkårsgrunnlagElement>,
                organisasjonsnummer: String,
                dato: LocalDate,
                økonomi: Økonomi,
                arbeidsgiverperiode: Arbeidsgiverperiode?,
                regler: ArbeidsgiverRegler,
                subsumsjonObserver: SubsumsjonObserver,
                manglerRefusjonsopplysning: ManglerRefusjonsopplysning
            ): Økonomi {
                val vilkårsgrunnlag = checkNotNull(finnVilkårsgrunnlag(elementer, dato)) {
                    "Fant ikke vilkårsgrunnlag for $dato. Må ha et vilkårsgrunnlag for å legge til utbetalingsopplysninger. Har vilkårsgrunnlag på skjæringstidspunktene ${elementer.map { it.skjæringstidspunkt }}"
                }
                return vilkårsgrunnlag.medUtbetalingsopplysninger(organisasjonsnummer, dato, økonomi, arbeidsgiverperiode, regler, subsumsjonObserver, manglerRefusjonsopplysning)
            }

            internal fun utenInntekt(
                elementer: Collection<VilkårsgrunnlagElement>,
                dato: LocalDate,
                økonomi: Økonomi,
                arbeidsgiverperiode: Arbeidsgiverperiode?
            ): Økonomi {
                return finnVilkårsgrunnlag(elementer, dato)?.utenInntekt(økonomi, arbeidsgiverperiode) ?: økonomi.inntekt(
                    aktuellDagsinntekt = INGEN,
                    dekningsgrunnlag = INGEN,
                    skjæringstidspunkt = dato,
                    `6G` = INGEN,
                    arbeidsgiverperiode = arbeidsgiverperiode,
                    refusjonsbeløp = INGEN
                )
            }

            private fun finnVilkårsgrunnlag(
                elementer: Collection<VilkårsgrunnlagElement>,
                dato: LocalDate
            ): VilkårsgrunnlagElement? {
                return elementer
                    .filter { it.skjæringstidspunkt <= dato }
                    .maxByOrNull { it.skjæringstidspunkt }
            }
        }
    }

    internal class Grunnlagsdata(
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        private val sammenligningsgrunnlag: Sammenligningsgrunnlag,
        private val avviksprosent: Prosent?,
        internal val opptjening: Opptjening,
        private val medlemskapstatus: Medlemskapsvurdering.Medlemskapstatus,
        private val vurdertOk: Boolean,
        private val meldingsreferanseId: UUID?,
        vilkårsgrunnlagId: UUID
    ) : VilkårsgrunnlagElement(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag, opptjening) {
        override fun valider(aktivitetslogg: IAktivitetslogg, erForlengelse: Boolean) {
            sykepengegrunnlag.validerAvvik(aktivitetslogg, sammenligningsgrunnlag)
        }

        override fun ghostPeriode(sisteId: UUID, organisasjonsnummer: String, periode: Periode): GhostPeriode? {
            return sykepengegrunnlag.ghostPeriode(sisteId, vilkårsgrunnlagId, organisasjonsnummer, periode)
        }

        override fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor) {
            vilkårsgrunnlagHistorikkVisitor.preVisitGrunnlagsdata(
                skjæringstidspunkt,
                this,
                sykepengegrunnlag,
                sammenligningsgrunnlag,
                avviksprosent,
                opptjening,
                vurdertOk,
                meldingsreferanseId,
                vilkårsgrunnlagId,
                medlemskapstatus
            )
            sykepengegrunnlag.accept(vilkårsgrunnlagHistorikkVisitor)
            sammenligningsgrunnlag.accept(vilkårsgrunnlagHistorikkVisitor)
            opptjening.accept(vilkårsgrunnlagHistorikkVisitor)
            vilkårsgrunnlagHistorikkVisitor.postVisitGrunnlagsdata(
                skjæringstidspunkt,
                this,
                sykepengegrunnlag,
                sammenligningsgrunnlag,
                avviksprosent,
                medlemskapstatus,
                vurdertOk,
                meldingsreferanseId,
                vilkårsgrunnlagId
            )
        }

        override fun inngårISammenligningsgrunnlaget(organisasjonsnummer: String) =
            sammenligningsgrunnlag.erRelevant(organisasjonsnummer)

        override fun avvis(tidslinjer: List<Utbetalingstidslinje>, skjæringstidspunktperiode: Periode) {
            sykepengegrunnlag.avvis(tidslinjer, skjæringstidspunktperiode)
            val begrunnelser = mutableListOf<Begrunnelse>()
            if (medlemskapstatus == Medlemskapsvurdering.Medlemskapstatus.Nei) begrunnelser.add(Begrunnelse.ManglerMedlemskap)
            if (!opptjening.erOppfylt()) begrunnelser.add(Begrunnelse.ManglerOpptjening)
            if (begrunnelser.isEmpty()) return
            Utbetalingstidslinje.avvis(tidslinjer, listOf(skjæringstidspunktperiode), begrunnelser)
        }

        override fun vilkårsgrunnlagtype() = "Spleis"

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonObserver: SubsumsjonObserver
        ) = kopierMed(hendelse, sykepengegrunnlag.overstyrArbeidsforhold(hendelse, subsumsjonObserver), opptjening.overstyrArbeidsforhold(hendelse, subsumsjonObserver), subsumsjonObserver)

        override fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonObserver: SubsumsjonObserver
        ): VilkårsgrunnlagElement {
            val sykepengegrunnlagOk = sykepengegrunnlag.valider(hendelse)
            val opptjeningOk = opptjening?.valider(hendelse)
            return Grunnlagsdata(
                skjæringstidspunkt = skjæringstidspunkt,
                sykepengegrunnlag = sykepengegrunnlag,
                sammenligningsgrunnlag = sammenligningsgrunnlag,
                avviksprosent = sykepengegrunnlag.avviksprosent(this.sammenligningsgrunnlag.sammenligningsgrunnlag, subsumsjonObserver),
                opptjening = opptjening ?: this.opptjening,
                medlemskapstatus = medlemskapstatus,
                vurdertOk = vurdertOk && sykepengegrunnlagOk && (opptjeningOk ?: true),
                meldingsreferanseId = meldingsreferanseId,
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        }

        internal fun harInntektFraAOrdningen(): Boolean = sykepengegrunnlag.harInntektFraAOrdningen()
    }

    internal class InfotrygdVilkårsgrunnlag(
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlag: Sykepengegrunnlag,
        vilkårsgrunnlagId: UUID = UUID.randomUUID()
    ) : VilkårsgrunnlagElement(vilkårsgrunnlagId, skjæringstidspunkt, sykepengegrunnlag, null) {

        override fun ghostPeriode(sisteId: UUID, organisasjonsnummer: String, periode: Periode) = null

        override fun valider(aktivitetslogg: IAktivitetslogg, erForlengelse: Boolean) {
            if (erForlengelse) {
                aktivitetslogg.info("Perioden har opphav i Infotrygd, men saken beholdes i Spleis fordi det er utbetalt i Spleis tidligere.")
                return
            }
            aktivitetslogg.funksjonellFeil(RV_IT_16)
        }

        override fun overstyrArbeidsforhold(
            hendelse: OverstyrArbeidsforhold,
            subsumsjonObserver: SubsumsjonObserver
        ): Grunnlagsdata? {
            hendelse.funksjonellFeil(RV_VV_11)
            return null
        }

        override fun kopierMed(
            hendelse: IAktivitetslogg,
            sykepengegrunnlag: Sykepengegrunnlag,
            opptjening: Opptjening?,
            subsumsjonObserver: SubsumsjonObserver
        ): InfotrygdVilkårsgrunnlag {
            return InfotrygdVilkårsgrunnlag(
                skjæringstidspunkt = skjæringstidspunkt,
                sykepengegrunnlag = sykepengegrunnlag,
                vilkårsgrunnlagId = UUID.randomUUID()
            )
        }

        override fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor) {
            vilkårsgrunnlagHistorikkVisitor.preVisitInfotrygdVilkårsgrunnlag(
                this,
                skjæringstidspunkt,
                sykepengegrunnlag,
                vilkårsgrunnlagId
            )
            sykepengegrunnlag.accept(vilkårsgrunnlagHistorikkVisitor)
            vilkårsgrunnlagHistorikkVisitor.postVisitInfotrygdVilkårsgrunnlag(
                this,
                skjæringstidspunkt,
                sykepengegrunnlag,
                vilkårsgrunnlagId
            )
        }

        override fun vilkårsgrunnlagtype() = "Infotrygd"

        override fun equals(other: Any?): Boolean {
            if (other !is InfotrygdVilkårsgrunnlag) return false
            return skjæringstidspunkt == other.skjæringstidspunkt && sykepengegrunnlag == other.sykepengegrunnlag
        }

        override fun hashCode(): Int {
            var result = skjæringstidspunkt.hashCode()
            result = 31 * result + sykepengegrunnlag.hashCode()
            return result
        }
    }

    internal companion object {
        internal fun ferdigVilkårsgrunnlagHistorikk(innslag: List<Innslag>) =
            VilkårsgrunnlagHistorikk(innslag.toMutableList())
    }
}
