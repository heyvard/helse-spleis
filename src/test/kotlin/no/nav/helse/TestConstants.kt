package no.nav.helse

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.søknad.domain.Sykepengesøknad
import no.nav.syfo.kafka.sykepengesoknad.dto.ArbeidsgiverDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.ArbeidsgiverForskuttererDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.ArbeidssituasjonDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.FravarDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.FravarstypeDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.PeriodeDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.SoknadsperiodeDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.SoknadsstatusDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.SoknadstypeDTO
import no.nav.syfo.kafka.sykepengesoknad.dto.SykepengesoknadDTO
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.*

object TestConstants {
    private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    val sykeperiodFOM = LocalDate.of(2019, Month.SEPTEMBER, 16)
    val sykeperiodeTOM = LocalDate.of(2019, Month.OCTOBER, 5)
    val egenmeldingFom = LocalDate.of(2019, Month.SEPTEMBER, 12)
    val egenmeldingTom = LocalDate.of(2019, Month.SEPTEMBER, 15)
    val ferieFom = LocalDate.of(2019, Month.OCTOBER, 1)
    val ferieTom = LocalDate.of(2019, Month.OCTOBER, 4)

    fun søknad(
            status: SoknadsstatusDTO = SoknadsstatusDTO.SENDT,
            fom: LocalDate = LocalDate.of(2019, Month.SEPTEMBER, 10),
            tom: LocalDate = LocalDate.of(2019, Month.OCTOBER, 5),
            søknadsperioder: List<SoknadsperiodeDTO> = listOf(SoknadsperiodeDTO(
                    fom = sykeperiodFOM,
                    tom = LocalDate.of(2019, Month.SEPTEMBER, 30)
            ), SoknadsperiodeDTO(
                    fom = LocalDate.of(2019, Month.OCTOBER, 5),
                    tom = sykeperiodeTOM
            ))
    ) = Sykepengesøknad(objectMapper.valueToTree(SykepengesoknadDTO(
            id = UUID.randomUUID().toString(),
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = status,
            aktorId = UUID.randomUUID().toString(),
            sykmeldingId = UUID.randomUUID().toString(),
            arbeidsgiver = ArbeidsgiverDTO(
                    navn = "enArbeidsgiver",
                    orgnummer = "123456789"
            ),
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiverForskutterer = ArbeidsgiverForskuttererDTO.JA,
            fom = fom,
            tom = tom,
            startSyketilfelle = LocalDate.of(2019, Month.SEPTEMBER, 10),
            arbeidGjenopptatt = LocalDate.of(2019, Month.OCTOBER, 6),
            opprettet = LocalDateTime.now(),
            sendtNav = LocalDateTime.now(),
            sendtArbeidsgiver = LocalDateTime.of(2019, Month.SEPTEMBER, 30, 0, 0, 0),
            egenmeldinger = listOf(PeriodeDTO(
                    fom = egenmeldingFom,
                    tom = egenmeldingTom
            )),
            soknadsperioder = søknadsperioder,
            fravar = listOf(FravarDTO(
                    fom = ferieFom,
                    tom = ferieTom,
                    type = FravarstypeDTO.FERIE
            ))
    )))

    val søknad = søknad()
}