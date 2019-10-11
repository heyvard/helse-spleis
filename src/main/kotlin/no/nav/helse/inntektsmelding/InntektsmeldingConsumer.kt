package no.nav.helse.inntektsmelding

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.hendelse.Inntektsmelding
import no.nav.helse.person.PersonMediator
import no.nav.helse.serde.JsonNodeSerde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed

internal class InntektsmeldingConsumer(
        streamsBuilder: StreamsBuilder,
        private val inntektsmeldingKafkaTopic: String,
        private val personMediator: PersonMediator,
        private val probe: InntektsmeldingProbe = InntektsmeldingProbe()
) {

    companion object {
        val inntektsmeldingObjectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    init {
        build(streamsBuilder)
    }

    fun build(builder: StreamsBuilder) =
        builder.stream<String, JsonNode>(
            listOf(inntektsmeldingKafkaTopic), Consumed.with(Serdes.String(), JsonNodeSerde(inntektsmeldingObjectMapper))
            .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST)
        )
            .mapValues { jsonNode ->
                Inntektsmelding(jsonNode)
            }
            .peek{_, inntektsmelding -> probe.mottattInntektsmelding(inntektsmelding)}
            .foreach{_, inntektsmelding -> håndterInntektsmelding(inntektsmelding)}

    private fun håndterInntektsmelding(inntektsmelding: Inntektsmelding) {
        personMediator.håndterInntektsmelding(inntektsmelding)
    }

}
