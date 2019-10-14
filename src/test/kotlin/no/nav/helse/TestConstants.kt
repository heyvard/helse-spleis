package no.nav.helse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.hendelse.NySykepengesøknad
import no.nav.helse.hendelse.SendtSykepengesøknad
import no.nav.helse.hendelse.SykepengeHistorikk
import no.nav.inntektsmeldingkontrakt.Arbeidsgivertype
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import no.nav.inntektsmeldingkontrakt.Refusjon
import no.nav.inntektsmeldingkontrakt.Status
import no.nav.syfo.kafka.sykepengesoknad.dto.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.*

internal object TestConstants {
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    val sykeperiodFOM = 16.september
    val sykeperiodeTOM = 5.oktober
    val egenmeldingFom = 12.september
    val egenmeldingTom = 15.september
    val ferieFom = 1.oktober
    val ferieTom = 4.oktober

    fun søknadDTO(
        id: String = UUID.randomUUID().toString(),
        status: SoknadsstatusDTO,
        aktørId: String = UUID.randomUUID().toString(),
        fom: LocalDate = 10.september,
        tom: LocalDate = 5.oktober,
        arbeidGjenopptatt: LocalDate? = null,
        korrigerer: String? = null,
        egenmeldinger: List<PeriodeDTO> = listOf(PeriodeDTO(
            fom = egenmeldingFom,
            tom = egenmeldingTom
        )),
        søknadsperioder: List<SoknadsperiodeDTO> = listOf(SoknadsperiodeDTO(
            fom = sykeperiodFOM,
            tom = 30.september
        ), SoknadsperiodeDTO(
            fom = 5.oktober,
            tom = sykeperiodeTOM
        )),
        fravær: List<FravarDTO> = listOf(FravarDTO(
            fom = ferieFom,
            tom = ferieTom,
            type = FravarstypeDTO.FERIE)),
        arbeidsgiver: ArbeidsgiverDTO? = ArbeidsgiverDTO(
            navn = "enArbeidsgiver",
            orgnummer = "123456789"
        )
    ) = SykepengesoknadDTO(
            id = id,
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = status,
            aktorId = aktørId,
            sykmeldingId = UUID.randomUUID().toString(),
            arbeidsgiver = arbeidsgiver,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiverForskutterer = ArbeidsgiverForskuttererDTO.JA,
            fom = fom,
            tom = tom,
            startSyketilfelle = 10.september,
            arbeidGjenopptatt = arbeidGjenopptatt,
            korrigerer = korrigerer,
            opprettet = LocalDateTime.now(),
            sendtNav = LocalDateTime.now(),
            sendtArbeidsgiver = 30.september.atStartOfDay(),
            egenmeldinger = egenmeldinger,
            soknadsperioder = søknadsperioder,
            fravar = fravær
        )

    fun sendtSøknad(
        id: String = UUID.randomUUID().toString(),
        aktørId: String = UUID.randomUUID().toString(),
        fom: LocalDate = 10.september,
        tom: LocalDate = 5.oktober,
        arbeidGjenopptatt: LocalDate? = null,
        korrigerer: String? = null,
        egenmeldinger: List<PeriodeDTO> = listOf(PeriodeDTO(
            fom = egenmeldingFom,
            tom = egenmeldingTom
        )),
        søknadsperioder: List<SoknadsperiodeDTO> = listOf(SoknadsperiodeDTO(
            fom = sykeperiodFOM,
            tom = 30.september
        ), SoknadsperiodeDTO(
            fom = 5.oktober,
            tom = sykeperiodeTOM
        )),
        fravær: List<FravarDTO> = listOf(FravarDTO(
            fom = ferieFom,
            tom = ferieTom,
            type = FravarstypeDTO.FERIE)),
        arbeidsgiver: ArbeidsgiverDTO? = ArbeidsgiverDTO(
            navn = "enArbeidsgiver",
            orgnummer = "123456789"
        )
    ) = SendtSykepengesøknad(objectMapper.valueToTree<JsonNode>(søknadDTO(
            id = id,
            aktørId = aktørId,
            fom = fom,
            tom = tom,
            arbeidGjenopptatt = arbeidGjenopptatt,
            korrigerer = korrigerer,
            egenmeldinger = egenmeldinger,
            søknadsperioder = søknadsperioder,
            fravær = fravær,
            status = SoknadsstatusDTO.SENDT,
            arbeidsgiver = arbeidsgiver
        )))

    fun nySøknad(
        id: String = UUID.randomUUID().toString(),
        aktørId: String = UUID.randomUUID().toString(),
        fom: LocalDate = 10.september,
        tom: LocalDate = 5.oktober,
        arbeidGjenopptatt: LocalDate? = null,
        korrigerer: String? = null,
        egenmeldinger: List<PeriodeDTO> = listOf(PeriodeDTO(
            fom = egenmeldingFom,
            tom = egenmeldingTom
        )),
        søknadsperioder: List<SoknadsperiodeDTO> = listOf(SoknadsperiodeDTO(
            fom = sykeperiodFOM,
            tom = 30.september
        ), SoknadsperiodeDTO(
            fom = 5.oktober,
            tom = sykeperiodeTOM
        )),
        fravær: List<FravarDTO> = listOf(FravarDTO(
            fom = ferieFom,
            tom = ferieTom,
            type = FravarstypeDTO.FERIE)),
        arbeidsgiver: ArbeidsgiverDTO? = ArbeidsgiverDTO(
            navn = "enArbeidsgiver",
            orgnummer = "123456789"
        )
    ) = NySykepengesøknad(objectMapper.valueToTree<JsonNode>(søknadDTO(
        id = id,
        aktørId = aktørId,
        fom = fom,
        tom = tom,
        arbeidGjenopptatt = arbeidGjenopptatt,
        korrigerer = korrigerer,
        egenmeldinger = egenmeldinger,
        søknadsperioder = søknadsperioder,
        fravær = fravær,
        status = SoknadsstatusDTO.NY,
        arbeidsgiver = arbeidsgiver
    )))

    fun inntektsmelding(virksomhetsnummer: String? = null) = no.nav.helse.hendelse.Inntektsmelding(objectMapper.valueToTree(Inntektsmelding(
        inntektsmeldingId = "",
        arbeidstakerFnr = "",
        arbeidstakerAktorId = "",
        virksomhetsnummer = virksomhetsnummer,
        arbeidsgiverFnr = null,
        arbeidsgiverAktorId = null,
        arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
        arbeidsforholdId = null,
        beregnetInntekt = null,
        refusjon = Refusjon(
            beloepPrMnd = null,
            opphoersdato = null
        ),
        endringIRefusjoner = emptyList(),
        opphoerAvNaturalytelser = emptyList(),
        gjenopptakelseNaturalytelser = emptyList(),
        arbeidsgiverperioder = emptyList(),
        status = Status.GYLDIG,
        arkivreferanse = ""
    )))

    fun sykepengeHistorikk(): SykepengeHistorikk {
        val historikk = { "aktørId" to "12345678910" }
        return SykepengeHistorikk(objectMapper.valueToTree(historikk))
    }
}

val Int.juli
    get() = LocalDate.of(2019, Month.JULY, this)

val Int.august
    get() = LocalDate.of(2019, Month.AUGUST, this)

val Int.september
    get() = LocalDate.of(2019, Month.SEPTEMBER, this)

val Int.oktober
    get() = LocalDate.of(2019, Month.OCTOBER, this)