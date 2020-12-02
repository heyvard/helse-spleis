package no.nav.helse.person

import no.nav.helse.Toggles
import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Utbetalingshistorikk.Inntektsopplysning.Companion.lagreInntekter
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Companion.utbetalingshistorikk
import no.nav.helse.person.ForkastetÅrsak.UKJENT
import no.nav.helse.person.Vedtaksperiode.Companion.harInntekt
import no.nav.helse.person.Vedtaksperiode.Companion.medSkjæringstidspunkt
import no.nav.helse.serde.reflection.OppdragReflect
import no.nav.helse.serde.reflection.Utbetalingstatus
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.aktive
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.utbetaltTidslinje
import no.nav.helse.utbetalingslinjer.UtbetalingObserver
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.utbetalingstidslinje.Historie
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt.Companion.summer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class Arbeidsgiver private constructor(
    private val person: Person,
    private val organisasjonsnummer: String,
    private val id: UUID,
    private val inntektshistorikk: Inntektshistorikk,
    private val inntektshistorikkVol2: InntektshistorikkVol2,
    private val sykdomshistorikk: Sykdomshistorikk,
    private val vedtaksperioder: MutableList<Vedtaksperiode>,
    private val forkastede: SortedMap<Vedtaksperiode, ForkastetÅrsak>,
    private val utbetalinger: MutableList<Utbetaling>,
    private val beregnetUtbetalingstidslinjer: MutableList<Triple<String, Utbetalingstidslinje, LocalDateTime>>
) : Aktivitetskontekst, UtbetalingObserver {
    internal constructor(person: Person, organisasjonsnummer: String) : this(
        person = person,
        organisasjonsnummer = organisasjonsnummer,
        id = UUID.randomUUID(),
        inntektshistorikk = Inntektshistorikk(),
        inntektshistorikkVol2 = InntektshistorikkVol2(),
        sykdomshistorikk = Sykdomshistorikk(),
        vedtaksperioder = mutableListOf(),
        forkastede = sortedMapOf(),
        utbetalinger = mutableListOf(),
        beregnetUtbetalingstidslinjer = mutableListOf()
    )

    init {
        utbetalinger.forEach { it.register(this) }
    }

    internal companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

        internal val SENERE_EXCLUSIVE = fun(senereEnnDenne: Vedtaksperiode): VedtaksperioderFilter {
            return fun(vedtaksperiode: Vedtaksperiode) = vedtaksperiode > senereEnnDenne
        }
        internal val ALLE: VedtaksperioderFilter = { true }

        internal fun List<Arbeidsgiver>.grunnlagForSykepengegrunnlag(skjæringstidspunkt: LocalDate, periodeStart: LocalDate) =
            this.mapNotNull { it.inntektshistorikkVol2.grunnlagForSykepengegrunnlag(skjæringstidspunkt, maxOf(skjæringstidspunkt, periodeStart)) }.summer()

        internal fun List<Arbeidsgiver>.inntekt(skjæringstidspunkt: LocalDate) =
            this.mapNotNull { it.inntektshistorikk.inntekt(skjæringstidspunkt) }.summer()

        internal fun List<Arbeidsgiver>.grunnlagForSammenligningsgrunnlag(skjæringstidspunkt: LocalDate) =
            this.mapNotNull { it.inntektshistorikkVol2.grunnlagForSammenligningsgrunnlag(skjæringstidspunkt) }.summer()

        internal fun List<Arbeidsgiver>.harNødvendigInntekt(skjæringstidspunkt: LocalDate) =
            this.all { it.vedtaksperioder.medSkjæringstidspunkt(skjæringstidspunkt).harInntekt() }
    }

    internal fun accept(visitor: ArbeidsgiverVisitor) {
        visitor.preVisitArbeidsgiver(this, id, organisasjonsnummer)
        inntektshistorikk.accept(visitor)
        inntektshistorikkVol2.accept(visitor)
        sykdomshistorikk.accept(visitor)
        visitor.preVisitUtbetalinger(utbetalinger)
        utbetalinger.forEach { it.accept(visitor) }
        visitor.postVisitUtbetalinger(utbetalinger)
        visitor.preVisitPerioder(vedtaksperioder)
        vedtaksperioder.forEach { it.accept(visitor) }
        visitor.postVisitPerioder(vedtaksperioder)
        visitor.preVisitForkastedePerioder(forkastede)
        forkastede.forEach { it.key.accept(visitor) }
        visitor.postVisitForkastedePerioder(forkastede)
        visitor.postVisitArbeidsgiver(this, id, organisasjonsnummer)
    }

    internal fun organisasjonsnummer() = organisasjonsnummer

    internal fun utbetaling() = utbetalinger.lastOrNull()

    internal fun lagUtbetaling(
        aktivitetslogg: IAktivitetslogg,
        fødselsnummer: String,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        periode: Periode
    ): Utbetaling {
        val (organisasjonsnummer, utbetalingstidslinje, _) = beregnetUtbetalingstidslinjer.last()
        return Utbetaling.lagUtbetaling(
            utbetalinger,
            fødselsnummer,
            organisasjonsnummer,
            utbetalingstidslinje,
            periode.endInclusive,
            aktivitetslogg,
            maksdato,
            forbrukteSykedager,
            gjenståendeSykedager
        ).also { nyUtbetaling(it) }
    }

    private fun nyUtbetaling(utbetaling: Utbetaling) {
        utbetalinger.add(utbetaling)
        utbetaling.register(this)
    }

    internal fun utbetalteUtbetalinger() = utbetalinger.aktive()

    internal fun nåværendeTidslinje() =
        beregnetUtbetalingstidslinjer.lastOrNull()?.second ?: throw IllegalStateException("mangler utbetalinger")

    internal fun lagreUtbetalingstidslinjeberegning(organisasjonsnummer: String, utbetalingstidslinje: Utbetalingstidslinje) {
        beregnetUtbetalingstidslinjer.add(Triple(organisasjonsnummer, utbetalingstidslinje, LocalDateTime.now()))
    }

    private fun validerSykdomstidslinjer() = vedtaksperioder.forEach {
        it.validerSykdomstidslinje(sykdomshistorikk.sykdomstidslinje())
    }

    internal fun håndter(sykmelding: Sykmelding) {
        sykmelding.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(sykmelding) }.none { it }) {
            sykmelding.info("Lager ny vedtaksperiode")
            nyVedtaksperiode(sykmelding).håndter(sykmelding)
            vedtaksperioder.sort()
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(søknad: Søknad) {
        søknad.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(søknad) }.none { it }) {
            søknad.error("Forventet ikke søknad. Har nok ikke mottatt sykmelding")
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(søknad: SøknadArbeidsgiver) {
        søknad.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(søknad) }.none { it }) {
            søknad.error("Forventet ikke søknad til arbeidsgiver. Har nok ikke mottatt sykmelding")
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(inntektsmelding: Inntektsmelding) {
        inntektsmelding.kontekst(this)
        if (vedtaksperioder.toList().map { it.håndter(inntektsmelding) }.none { it }) {
            inntektsmelding.error("Forventet ikke inntektsmelding. Har nok ikke mottatt sykmelding")
        } else {
            validerSykdomstidslinjer()
        }
    }

    internal fun håndter(utbetalingshistorikk: Utbetalingshistorikk) {
        utbetalingshistorikk.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(utbetalingshistorikk) }
    }

    internal fun håndter(ytelser: Ytelser) {
        ytelser.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(ytelser) }
    }

    internal fun håndter(utbetalingsgodkjenning: Utbetalingsgodkjenning) {
        utbetalingsgodkjenning.kontekst(this)
        utbetalinger.forEach { it.håndter(utbetalingsgodkjenning) }
        vedtaksperioder.toList().forEach { it.håndter(utbetalingsgodkjenning) }
    }

    internal fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        vilkårsgrunnlag.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(vilkårsgrunnlag) }
    }

    internal fun håndter(simulering: Simulering) {
        simulering.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(simulering) }
    }

    internal fun håndter(utbetaling: UtbetalingOverført) {
        utbetaling.kontekst(this)
        utbetalinger.forEach { it.håndter(utbetaling) }
    }

    internal fun håndter(utbetaling: UtbetalingHendelse) {
        utbetaling.kontekst(this)
        utbetalinger.forEach { it.håndter(utbetaling) }
        vedtaksperioder.forEach { it.håndter(utbetaling) }
    }

    internal fun håndter(påminnelse: Utbetalingpåminnelse) {
        påminnelse.kontekst(this)
        utbetalinger.forEach { it.håndter(påminnelse) }
    }

    internal fun håndter(påminnelse: Påminnelse): Boolean {
        påminnelse.kontekst(this)
        return vedtaksperioder.toList().any { it.håndter(påminnelse) }
    }

    internal fun håndter(hendelse: AnnullerUtbetaling) {
        hendelse.kontekst(this)
        hendelse.info("Håndterer annullering")

        val sisteUtbetalte = Utbetaling.finnUtbetalingForAnnullering(utbetalinger, hendelse) ?: return
        val annullering = sisteUtbetalte.annuller(hendelse) ?: return
        nyUtbetaling(annullering)
        annullering.håndter(hendelse)
        søppelbøtte(hendelse, ALLE)
    }

    internal fun håndter(arbeidsgivere: List<Arbeidsgiver>, hendelse: Grunnbeløpsregulering) {
        hendelse.kontekst(this)
        hendelse.info("Håndterer etterutbetaling")

        val sisteUtbetalte = Utbetaling.finnUtbetalingForJustering(
            utbetalinger = utbetalinger,
            hendelse = hendelse
        ) ?: return hendelse.info("Fant ingen utbetalinger å etterutbetale")

        val periode = LocalDate.of(2020, 5, 1).minusMonths(18) til LocalDate.now()
        if (!hendelse.harHistorikk) return utbetalingshistorikk(hendelse, periode)

        val reberegnetTidslinje = reberegnUtbetalte(hendelse, arbeidsgivere,  Historie(person, hendelse.utbetalingshistorikk()), periode)

        val etterutbetaling = sisteUtbetalte.etterutbetale(hendelse, reberegnetTidslinje)
            ?: return hendelse.info("Utbetalingen for $organisasjonsnummer for perioden ${sisteUtbetalte.periode} er ikke blitt endret. Grunnbeløpsregulering gjennomføres ikke.")

        hendelse.info("Etterutbetaler for $organisasjonsnummer for perioden ${sisteUtbetalte.periode}")
        nyUtbetaling(etterutbetaling)
        etterutbetaling.håndter(hendelse)
    }

    private fun reberegnUtbetalte(aktivitetslogg: IAktivitetslogg, arbeidsgivere: List<Arbeidsgiver>, historie: Historie, periode: Periode): Utbetalingstidslinje {
        val arbeidsgivertidslinjer = arbeidsgivere
            .map { it to it.utbetalinger.utbetaltTidslinje() }
            .filter { it.second.isNotEmpty() }
            .toMap()

        MaksimumUtbetaling(arbeidsgivertidslinjer.values.toList(), aktivitetslogg, historie.skjæringstidspunkter(periode), periode.endInclusive)
            .betal()

        arbeidsgivertidslinjer.forEach { (arbeidsgiver, reberegnetUtbetalingstidslinje) ->
            arbeidsgiver.lagreUtbetalingstidslinjeberegning(organisasjonsnummer, reberegnetUtbetalingstidslinje)
        }

        return nåværendeTidslinje()
    }

    override fun utbetalingUtbetalt(
        id: UUID,
        type: Utbetaling.Utbetalingtype,
        maksdato: LocalDate,
        forbrukteSykedager: Int,
        gjenståendeSykedager: Int,
        arbeidsgiverOppdrag: Oppdrag,
        personOppdrag: Oppdrag,
        ident: String,
        epost: String,
        tidspunkt: LocalDateTime,
        automatiskBehandling: Boolean
    ) {
        person.utbetalingUtbetalt(
            PersonObserver.UtbetalingUtbetaltEvent(
                utbetalingId = id,
                type = type.name,
                maksdato = maksdato,
                forbrukteSykedager = forbrukteSykedager,
                gjenståendeSykedager = gjenståendeSykedager,
                ident = ident,
                epost = epost,
                tidspunkt = tidspunkt,
                automatiskBehandling = automatiskBehandling,
                arbeidsgiverOppdrag = OppdragReflect(arbeidsgiverOppdrag).toMap(),
                personOppdrag = OppdragReflect(personOppdrag).toMap()
            )
        )
    }

    override fun utbetalingEndret(
        id: UUID,
        type: Utbetaling.Utbetalingtype,
        arbeidsgiverOppdrag: Oppdrag,
        personOppdrag: Oppdrag,
        forrigeTilstand: Utbetaling.Tilstand,
        nesteTilstand: Utbetaling.Tilstand
    ) {
        person.utbetalingEndret(
            PersonObserver.UtbetalingEndretEvent(
                utbetalingId = id,
                type = type.name,
                forrigeStatus = Utbetalingstatus.fraTilstand(forrigeTilstand).name,
                gjeldendeStatus = Utbetalingstatus.fraTilstand(nesteTilstand).name,
                arbeidsgiverOppdrag = OppdragReflect(arbeidsgiverOppdrag).toMap(),
                personOppdrag = OppdragReflect(personOppdrag).toMap(),
            )
        )
    }

    override fun utbetalingAnnullert(
        id: UUID,
        oppdrag: Oppdrag,
        hendelse: ArbeidstakerHendelse,
        godkjenttidspunkt: LocalDateTime,
        saksbehandlerEpost: String
    ) {
        person.annullert(
            PersonObserver.UtbetalingAnnullertEvent(
                fødselsnummer = hendelse.fødselsnummer(),
                aktørId = hendelse.aktørId(),
                organisasjonsnummer = hendelse.organisasjonsnummer(),
                fagsystemId = oppdrag.fagsystemId(),
                utbetalingId = id,
                utbetalingslinjer = oppdrag.map {
                    PersonObserver.UtbetalingAnnullertEvent.Utbetalingslinje(
                        fom = requireNotNull(it.datoStatusFom()),
                        tom = it.tom,
                        beløp = it.totalbeløp(),
                        grad = it.grad
                    )
                },
                annullertAvSaksbehandler = godkjenttidspunkt,
                saksbehandlerEpost = saksbehandlerEpost
            )
        )
    }

    internal fun håndter(hendelse: Rollback) {
        hendelse.kontekst(this)
        søppelbøtte(RollbackArbeidsgiver(organisasjonsnummer, hendelse), ALLE)
    }

    internal fun håndter(hendelse: OverstyrTidslinje) {
        hendelse.kontekst(this)
        vedtaksperioder.toList().forEach { it.håndter(hendelse) }
    }

    internal fun oppdaterSykdom(hendelse: SykdomstidslinjeHendelse) = sykdomshistorikk.nyHåndter(hendelse)

    internal fun sykdomstidslinje() = sykdomshistorikk.sykdomstidslinje()

    internal fun grunnlagForSykepengegrunnlag(skjæringstidspunkt: LocalDate, periodeStart: LocalDate) =
        if (Toggles.nyInntekt) inntektshistorikkVol2.grunnlagForSykepengegrunnlag(skjæringstidspunkt, periodeStart) else inntektshistorikk.inntekt(skjæringstidspunkt)

    internal fun addInntekt(inntektsmelding: Inntektsmelding, skjæringstidspunkt: LocalDate) {
        inntektsmelding.addInntekt(inntektshistorikk, skjæringstidspunkt)
    }

    internal fun addInntekt(ytelser: Ytelser) {
        ytelser.addInntekt(organisasjonsnummer, inntektshistorikk)
    }

    internal fun addInntekt(utbetalingshistorikk: Utbetalingshistorikk) {
        utbetalingshistorikk.addInntekt(organisasjonsnummer, inntektshistorikk)
    }

    internal fun addInntektVol2(inntektsmelding: Inntektsmelding, skjæringstidspunkt: LocalDate) {
        inntektsmelding.addInntekt(inntektshistorikkVol2, skjæringstidspunkt)
    }

    internal fun addInntektVol2(inntektsopplysninger: List<Utbetalingshistorikk.Inntektsopplysning>, ytelser: Ytelser) {
        inntektsopplysninger.lagreInntekter(inntektshistorikkVol2, ytelser.meldingsreferanseId())
    }

    internal fun lagreInntekter(
        arbeidsgiverInntekt: Inntektsvurdering.ArbeidsgiverInntekt,
        skjæringstidspunkt: LocalDate,
        vilkårsgrunnlag: Vilkårsgrunnlag
    ) {
        arbeidsgiverInntekt.lagreInntekter(
            inntektshistorikkVol2,
            skjæringstidspunkt,
            vilkårsgrunnlag.meldingsreferanseId()
        )
    }

    internal fun søppelbøtte(
        hendelse: ArbeidstakerHendelse,
        filter: VedtaksperioderFilter,
        sendTilInfotrygd: Boolean = true
    ): List<Vedtaksperiode> {
        return forkast(filter)
            .takeIf { it.isNotEmpty() }
            ?.also { perioder ->
                perioder
                    .forEach {
                        it.ferdig(hendelse, sendTilInfotrygd)
                        sykdomshistorikk.fjernDager(it.periode())
                    }
                if (vedtaksperioder.isEmpty()) sykdomshistorikk.tøm()
                else sykdomshistorikk.fjernDagerFør(vedtaksperioder.first().periode().start)
                gjenopptaBehandling(hendelse)
            }
            ?: listOf()
    }

    private fun forkast(filter: VedtaksperioderFilter) = vedtaksperioder
        .filter(filter)
        .also { perioder ->
            vedtaksperioder.removeAll(perioder)
            forkastede.putAll(perioder.map { it to UKJENT })
        }

    private fun tidligereOgEttergølgende(vedtaksperiode: Vedtaksperiode): MutableList<Vedtaksperiode> {
        var index = vedtaksperioder.indexOf(vedtaksperiode)
        val results = vedtaksperioder.subList(0, index + 1).toMutableList()
        if (results.isEmpty()) return mutableListOf()
        while (vedtaksperioder.last() != results.last()) {
            if (!vedtaksperioder[index].erSykeperiodeRettFør(vedtaksperioder[index + 1])) break
            results.add(vedtaksperioder[index + 1])
            index++
        }
        return results
    }

    internal fun tidligereOgEttergølgende2(segSelv: Vedtaksperiode): VedtaksperioderFilter {
        val medSammeArbeidsgiverperiode = tidligereOgEttergølgende(segSelv)
        return fun(vedtaksperiode: Vedtaksperiode) = vedtaksperiode in medSammeArbeidsgiverperiode
    }

    private fun nyVedtaksperiode(sykmelding: Sykmelding): Vedtaksperiode {
        return Vedtaksperiode(
            person = person,
            arbeidsgiver = this,
            id = UUID.randomUUID(),
            aktørId = sykmelding.aktørId(),
            fødselsnummer = sykmelding.fødselsnummer(),
            organisasjonsnummer = sykmelding.organisasjonsnummer()
        ).also {
            vedtaksperioder.add(it)
        }
    }

    internal fun finnSykeperiodeRettFør(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other -> other.erSykeperiodeRettFør(vedtaksperiode) }

    internal fun finnSykeperiodeRettEtter(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.firstOrNull { other -> vedtaksperiode.erSykeperiodeRettFør(other) }

    internal fun harPeriodeEtter(vedtaksperiode: Vedtaksperiode) =
        vedtaksperioder.any { other -> other.starterEtter(vedtaksperiode) }

    internal fun tidligerePerioderFerdigBehandlet(vedtaksperiode: Vedtaksperiode) =
        Vedtaksperiode.tidligerePerioderFerdigBehandlet(vedtaksperioder, vedtaksperiode)

    internal fun gjenopptaBehandling(hendelse: ArbeidstakerHendelse) {
        vedtaksperioder.any { it.håndter(GjenopptaBehandling(hendelse)) }
        person.nåværendeVedtaksperioder().firstOrNull()?.gjentaHistorikk(hendelse)
    }

    internal class GjenopptaBehandling(private val hendelse: ArbeidstakerHendelse) :
        ArbeidstakerHendelse(hendelse.meldingsreferanseId(), hendelse.aktivitetslogg) {
        override fun organisasjonsnummer() = hendelse.organisasjonsnummer()
        override fun aktørId() = hendelse.aktørId()
        override fun fødselsnummer() = hendelse.fødselsnummer()
    }

    internal class RollbackArbeidsgiver(
        private val organisasjonsnummer: String,
        private val hendelse: PersonHendelse
    ) :
        ArbeidstakerHendelse(hendelse.meldingsreferanseId(), hendelse.aktivitetslogg) {
        override fun organisasjonsnummer() = organisasjonsnummer
        override fun aktørId() = hendelse.aktørId()
        override fun fødselsnummer() = hendelse.fødselsnummer()
    }

    internal class TilbakestillBehandling(
        private val organisasjonsnummer: String,
        private val hendelse: PersonHendelse
    ) : ArbeidstakerHendelse(hendelse.meldingsreferanseId(), hendelse.aktivitetslogg) {
        override fun organisasjonsnummer() = organisasjonsnummer
        override fun aktørId() = hendelse.aktørId()
        override fun fødselsnummer() = hendelse.fødselsnummer()
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Arbeidsgiver", mapOf("organisasjonsnummer" to organisasjonsnummer))
    }

    internal fun lås(periode: Periode) {
        sykdomshistorikk.sykdomstidslinje().lås(periode)
    }

    internal fun låsOpp(periode: Periode) {
        sykdomshistorikk.sykdomstidslinje().låsOpp(periode)
    }

    internal fun overlapper(periode: Periode) = sykdomstidslinje().periode()?.overlapperMed(periode) ?: false

    internal fun nåværendeVedtaksperiode(): Vedtaksperiode? {
        return vedtaksperioder.firstOrNull { it.måFerdigstilles() }
    }

    internal fun harHistorikk() = !sykdomshistorikk.isEmpty()

    internal fun oppdatertUtbetalingstidslinje(periode: Periode, ytelser: Ytelser, historie: Historie): Utbetalingstidslinje {
        if (Toggles.nyInntekt) return historie.beregnUtbetalingstidslinjeVol2(organisasjonsnummer, periode, inntektshistorikkVol2, NormalArbeidstaker)
        val utbetalingstidslinje = historie.beregnUtbetalingstidslinje(organisasjonsnummer, periode, inntektshistorikk, NormalArbeidstaker)
        try {
            val sammenhengendePeriode = historie.sammenhengendePeriode(periode)
            val vol2Linje = historie.beregnUtbetalingstidslinjeVol2(organisasjonsnummer, periode, inntektshistorikkVol2, NormalArbeidstaker)
            sammenlignGammelOgNyUtbetalingstidslinje(utbetalingstidslinje, vol2Linje, sammenhengendePeriode)
        } catch (e: Throwable) {
            sikkerLogg.info("Feilet ved bygging av utbetalingstidslinje på ny måte for ${ytelser.vedtaksperiodeId}", e)
        }

        return utbetalingstidslinje
    }

    private fun sammenlignGammelOgNyUtbetalingstidslinje(
        utbetalingstidslinje: Utbetalingstidslinje,
        vol2Linje: Utbetalingstidslinje,
        sammenhengendePeriode: Periode
    ) {
        val vol1Linje = utbetalingstidslinje.kutt(sammenhengendePeriode.endInclusive)

        if (vol1Linje.size != vol2Linje.size)
            sikkerLogg.info("Forskjellig lengde på utbetalingstidslinjer. Vol1 = ${vol1Linje.size}, Vol2 = ${vol2Linje.size}")

        if (vol1Linje.toString() != vol2Linje.toString())
            sikkerLogg.info("Forskjellig toString() på utbetalingstidslinjer.\nVol1 = $vol1Linje\nVol2 = $vol2Linje")

        vol1Linje.zip(vol2Linje).mapNotNull { (vol1Dag, vol2Dag: Utbetalingstidslinje.Utbetalingsdag) ->
            val (vol1Dekning, vol1Dagsinntekt) = vol1Dag.økonomi.reflection { _, _, dekning, _, dagsinntekt, _, _, _ -> dekning to dagsinntekt }
            val (vol2Dekning, vol2Dagsinntekt) = vol2Dag.økonomi.reflection { _, _, dekning, _, dagsinntekt, _, _, _ -> dekning to dagsinntekt }

            if (vol1Dekning != vol2Dekning || vol1Dagsinntekt != vol2Dagsinntekt)
                "Vol1: ${vol1Dag.dato} [$vol1Dekning, $vol1Dagsinntekt] != Vol2: ${vol2Dag.dato} [$vol2Dekning, $vol2Dagsinntekt]"
            else
                null
        }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "\n", separator = "\n")
            ?.also(sikkerLogg::info)
    }

    fun støtterReplayFor(vedtaksperiode: Vedtaksperiode): Boolean {
        return finnSykeperiodeRettEtter(vedtaksperiode) == null
            && !sykdomstidslinje().harNyArbeidsgiverperiodeEtter(vedtaksperiode.periode().endInclusive)
    }

    internal fun append(bøtte: Historie.Historikkbøtte) {
        if (harHistorikk()) bøtte.add(organisasjonsnummer, sykdomstidslinje())
        utbetalteUtbetalinger().forEach {
            it.append(organisasjonsnummer, bøtte)
        }
    }

    internal class JsonRestorer private constructor() {
        internal companion object {
            internal fun restore(
                person: Person,
                organisasjonsnummer: String,
                id: UUID,
                inntektshistorikk: Inntektshistorikk,
                inntektshistorikkVol2: InntektshistorikkVol2,
                sykdomshistorikk: Sykdomshistorikk,
                vedtaksperioder: MutableList<Vedtaksperiode>,
                forkastede: SortedMap<Vedtaksperiode, ForkastetÅrsak>,
                utbetalinger: List<Utbetaling>,
                beregnetUtbetalingstidslinjer: List<Triple<String, Utbetalingstidslinje, LocalDateTime>>
            ) = Arbeidsgiver(
                person,
                organisasjonsnummer,
                id,
                inntektshistorikk,
                inntektshistorikkVol2,
                sykdomshistorikk,
                vedtaksperioder,
                forkastede,
                utbetalinger.toMutableList(),
                beregnetUtbetalingstidslinjer.toMutableList()
            )
        }
    }
}

internal enum class ForkastetÅrsak {
    IKKE_STØTTET,
    UKJENT,
    ERSTATTES,
    ANNULLERING
}

internal typealias VedtaksperioderFilter = (Vedtaksperiode) -> Boolean
