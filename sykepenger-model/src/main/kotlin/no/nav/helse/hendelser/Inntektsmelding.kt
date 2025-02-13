package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.`§ 8-10 ledd 3`
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Avsender.ARBEIDSGIVER
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.mapWithNext
import no.nav.helse.nesteDag
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.ForkastetVedtaksperiode
import no.nav.helse.person.ForkastetVedtaksperiode.Companion.overlapperMed
import no.nav.helse.person.Person
import no.nav.helse.person.Sykmeldingsperioder
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.beløp.Kilde
import no.nav.helse.person.inntekt.Arbeidstakerinntektskilde
import no.nav.helse.person.inntekt.FaktaavklartInntekt
import no.nav.helse.person.inntekt.Inntektsdata
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Inntektsmeldinginntekt
import no.nav.helse.person.inntekt.Inntektsopplysning
import no.nav.helse.person.refusjon.Refusjonsservitør
import no.nav.helse.økonomi.Inntekt

class Inntektsmelding(
    meldingsreferanseId: MeldingsreferanseId,
    private val refusjon: Refusjon,
    orgnummer: String,
    private val beregnetInntekt: Inntekt,
    arbeidsgiverperioder: List<Periode>,
    private val begrunnelseForReduksjonEllerIkkeUtbetalt: String?,
    private val opphørAvNaturalytelser: List<OpphørAvNaturalytelse>,
    private val harFlereInntektsmeldinger: Boolean,
    private val førsteFraværsdag: LocalDate?,
    mottatt: LocalDateTime
) : Hendelse {

    override val behandlingsporing = Behandlingsporing.Arbeidsgiver(
        organisasjonsnummer = orgnummer
    )
    override val metadata = HendelseMetadata(
        meldingsreferanseId = meldingsreferanseId,
        avsender = ARBEIDSGIVER,
        innsendt = mottatt,
        registrert = mottatt,
        automatiskBehandling = false
    )

    private val grupperteArbeidsgiverperioder = arbeidsgiverperioder.grupperSammenhengendePerioder()
    private val dager by lazy {
        DagerFraInntektsmelding(
            arbeidsgiverperioder = grupperteArbeidsgiverperioder,
            førsteFraværsdag = førsteFraværsdag,
            mottatt = metadata.registrert,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            harFlereInntektsmeldinger = harFlereInntektsmeldinger,
            opphørAvNaturalytelser = opphørAvNaturalytelser,
            hendelse = this
        )
    }

    internal val inntektsdato: LocalDate by lazy {
        if (førsteFraværsdag != null && (grupperteArbeidsgiverperioder.isEmpty() || førsteFraværsdag > grupperteArbeidsgiverperioder.last().endInclusive.nesteDag)) førsteFraværsdag
        else grupperteArbeidsgiverperioder.maxOf { it.start }
    }

    private val refusjonsdato: LocalDate by lazy {
        if (førsteFraværsdag == null) grupperteArbeidsgiverperioder.maxOf { it.start }
        else grupperteArbeidsgiverperioder.map { it.start }.plus(førsteFraværsdag).max()
    }

    internal val refusjonsservitør get() = Refusjonsservitør.fra(refusjon.refusjonstidslinje(refusjonsdato, metadata.meldingsreferanseId, metadata.innsendt))

    private var håndtertInntekt = false
    val dokumentsporing = Dokumentsporing.inntektsmeldingInntekt(meldingsreferanseId)

    private val inntektsdata = Inntektsdata(metadata.meldingsreferanseId, inntektsdato, beregnetInntekt, metadata.registrert)

    internal fun korrigertInntekt() = FaktaavklartInntekt(
        id = UUID.randomUUID(),
        inntektsdata = inntektsdata,
        inntektsopplysning = Inntektsopplysning.Arbeidstaker(Arbeidstakerinntektskilde.Arbeidsgiver)
    )

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, aktivitetslogg: IAktivitetslogg, alternativInntektsdato: LocalDate) {
        val inntektsdato = alternativInntektsdato.takeUnless { it == inntektsdato } ?: return
        if (!inntektshistorikk.leggTil(Inntektsmeldinginntekt(UUID.randomUUID(), inntektsdata.copy(dato = inntektsdato), Inntektsmeldinginntekt.Kilde.Arbeidsgiver))) return
        aktivitetslogg.info("Lagrer inntekt på alternativ inntektsdato $inntektsdato")
    }

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk, subsumsjonslogg: Subsumsjonslogg): LocalDate {
        subsumsjonslogg.logg(`§ 8-10 ledd 3`(beregnetInntekt.årlig, beregnetInntekt.daglig))
        inntektshistorikk.leggTil(Inntektsmeldinginntekt(UUID.randomUUID(), inntektsdata.copy(dato = inntektsdato), Inntektsmeldinginntekt.Kilde.Arbeidsgiver))
        return inntektsdato
    }

    internal fun inntektHåndtert() {
        håndtertInntekt = true
    }

    data class OpphørAvNaturalytelse(
        val beløp: Inntekt,
        val fom: LocalDate,
        val naturalytelse: String
    )

    data class Refusjon(
        val beløp: Inntekt?,
        val opphørsdato: LocalDate?,
        val endringerIRefusjon: List<EndringIRefusjon> = emptyList()
    ) {

        internal fun refusjonstidslinje(refusjonsdato: LocalDate, meldingsreferanseId: MeldingsreferanseId, tidsstempel: LocalDateTime): Beløpstidslinje {
            val kilde = Kilde(meldingsreferanseId, ARBEIDSGIVER, tidsstempel)

            val opphørIRefusjon = opphørsdato?.let {
                val sisteRefusjonsdag = maxOf(it, refusjonsdato.forrigeDag)
                EndringIRefusjon(Inntekt.INGEN, sisteRefusjonsdag.nesteDag)
            }

            val hovedopplysning = EndringIRefusjon(beløp ?: Inntekt.INGEN, refusjonsdato).takeUnless { it.endringsdato == opphørIRefusjon?.endringsdato }

            val gyldigeEndringer = endringerIRefusjon
                .filter { it.endringsdato > refusjonsdato }
                .filter { it.endringsdato < (opphørIRefusjon?.endringsdato ?: LocalDate.MAX) }
                .distinctBy { it.endringsdato }

            val alleRefusjonsopplysninger = listOfNotNull(hovedopplysning, *gyldigeEndringer.toTypedArray(), opphørIRefusjon).sortedBy { it.endringsdato }

            check(alleRefusjonsopplysninger.isNotEmpty()) { "Inntektsmeldingen inneholder ingen refusjonsopplysninger. Hvordan er dette mulig?" }

            return alleRefusjonsopplysninger.mapWithNext { nåværende, neste ->
                val fom = nåværende.endringsdato
                val tom = neste?.endringsdato?.forrigeDag ?: fom
                Beløpstidslinje.fra(fom til tom, nåværende.beløp, kilde)
            }.reduce(Beløpstidslinje::plus)
        }

        data class EndringIRefusjon(val beløp: Inntekt, val endringsdato: LocalDate)
    }

    internal fun dager(): DagerFraInntektsmelding {
        return dager
    }

    internal fun ferdigstill(
        aktivitetslogg: IAktivitetslogg,
        person: Person,
        vedtaksperioder: List<Vedtaksperiode>,
        forkastede: List<ForkastetVedtaksperiode>,
        sykmeldingsperioder: Sykmeldingsperioder
    ) {
        if (håndtertInntekt) return // Definisjonen av om en inntektsmelding er håndtert eller ikke er at vi har håndtert inntekten i den... 🤡
        val relevanteSykmeldingsperioder = sykmeldingsperioder.overlappendePerioder(dager) + sykmeldingsperioder.perioderInnenfor16Dager(dager)
        val overlapperMedForkastet = forkastede.overlapperMed(dager)
        val harPeriodeInnenfor16Dager = dager.harPeriodeInnenfor16Dager(vedtaksperioder)
        if (relevanteSykmeldingsperioder.isNotEmpty() && !overlapperMedForkastet) {
            person.emitInntektsmeldingFørSøknadEvent(metadata.meldingsreferanseId.id, relevanteSykmeldingsperioder, behandlingsporing.organisasjonsnummer)
            return aktivitetslogg.info("Inntektsmelding før søknad - er relevant for sykmeldingsperioder $relevanteSykmeldingsperioder")
        }
        aktivitetslogg.info("Inntektsmelding ikke håndtert")
        person.emitInntektsmeldingIkkeHåndtert(this, behandlingsporing.organisasjonsnummer, harPeriodeInnenfor16Dager)
    }

    internal fun skalOppdatereVilkårsgrunnlag(sykdomstidslinjeperiode: Periode?): Boolean {
        if (sykdomstidslinjeperiode == null) return false // har ikke noe sykdom for arbeidsgiveren
        return inntektsdato in sykdomstidslinjeperiode
    }
}
