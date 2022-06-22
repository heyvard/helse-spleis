package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.hendelser.Periode
import org.slf4j.LoggerFactory

internal class V166UtbetalteDagerMedForHøyAvviksprosent: JsonMigration(166) {

    override val description = "Identifiserer og logger dager som er utbetalt med en avviksprosent på over 25 %"

    private fun finnAvvikOrNull(jsonNode: ObjectNode) = try {
        finnAvvik(jsonNode)
    } catch (throwable: Throwable) {
        sikkerlogg.error("Feil ved identifisering av utbetalinger med avvikt over 25%: {}",
            keyValue("fødselsnummer", jsonNode.path("fødselsnummer").asText()),
            throwable
        )
        null
    }

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        finnAvvikOrNull(jsonNode)?.forEach { avvik ->
            sikkerlogg.warn("Utbetalt med avvik over 25%: {}, {}, {}, {}, {}, {}, {}",
                keyValue("fødselsnummer", avvik.sykmeldt),
                keyValue("aktørId", avvik.aktørId),
                keyValue("organisasjonsnummer", avvik.arbeidsgiver),
                keyValue("avviksprosent", avvik.avviksprosent),
                keyValue("antallDager", avvik.antallDager),
                keyValue("fom", avvik.periode.start),
                keyValue("tom", avvik.periode.endInclusive)
            )
        }
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        private fun JsonNode.asLocalDate() = LocalDate.parse(asText())
        private fun JsonNode.asLocalDateTime() = LocalDateTime.parse(asText())
        private fun JsonNode.dager() = path("dato").takeIf { it.isTextual }?.let { listOf(it.asLocalDate()) } ?: Periode(path("fom").asLocalDate(), path("tom").asLocalDate()).toList()
        private fun JsonNode.tom() = path("dato").takeIf { it.isTextual }?.asLocalDate() ?: path("tom").asLocalDate()

        internal data class Avvik(
            val sykmeldt: String,
            val aktørId: String,
            val arbeidsgiver: String,
            val skjæringstidspunkt: LocalDate,
            private val avvik: Double,
            private val utbetalteDager: List<LocalDate>
        ) {
            val avviksprosent = avvik * 100
            val periode = utbetalteDager.toSortedSet().let { Periode(it.first(), it.last()) }
            val antallDager = utbetalteDager.size
        }

        internal fun finnAvvik(jsonNode: ObjectNode): List<Avvik> {
            val fødselsnummer = jsonNode.path("fødselsnummer").asText()
            val aktørId = jsonNode.path("aktørId").asText()

            val nyesteVilkårsgrunnlag = jsonNode["vilkårsgrunnlagHistorikk"].firstOrNull() ?: return emptyList()

            val skjæringstidspunktTilForHøytAvvik = nyesteVilkårsgrunnlag.path("vilkårsgrunnlag")
                .filter { it.path("type").asText() == "Vilkårsprøving" }
                .filter { it.hasNonNull("avviksprosent") }
                .filter { it.path("avviksprosent").asDouble() > 0.25 }
                .associate { it.path("skjæringstidspunkt").asLocalDate() to it.path("avviksprosent").asDouble() }
                .takeUnless { it.isEmpty() }
                ?: return emptyList()

            val avvik = mutableListOf<Avvik>()
            jsonNode.path("arbeidsgivere").forEach { arbeidsgiver ->
                val organisasjonsnummer = arbeidsgiver.path("organisasjonsnummer").asText()

                val utbetalteUtbetalinger = arbeidsgiver.path("utbetalinger")
                    .groupBy { UUID.fromString(it.path("korrelasjonsId").asText()) }
                    .mapValues { entry -> entry.value.maxByOrNull { it.path("tidsstempel").asLocalDateTime() }!! }
                    .values
                    .filter { it.path("status").asText() == "UTBETALT" }
                    .takeUnless { it.isEmpty() }
                    ?: return@forEach

                val skjæringstidspunktTilUtbetalteDager = utbetalteUtbetalinger
                    .flatMap { utbetaling ->
                        val utbetalingFom = utbetaling.path("fom").asLocalDate()
                        utbetaling.path("utbetalingstidslinje").path("dager").filterNot { it.tom() < utbetalingFom }
                    }
                    .filter { it.path("type").asText() == "NavDag" }
                    .filter { it.path("arbeidsgiverbeløp").asDouble() > 0 || it.path("personbeløp").asDouble() > 0 }
                    .filter { it.hasNonNull("skjæringstidspunkt") }
                    .groupBy { it.path("skjæringstidspunkt").asLocalDate() }
                    .mapValues { entry -> entry.value.flatMap { it.dager() } }
                    .takeUnless { it.isEmpty() }
                    ?: return@forEach

                skjæringstidspunktTilUtbetalteDager.filterKeys { it in skjæringstidspunktTilForHøytAvvik.keys }.forEach { (skjæringstidspunkt, utbetalteDager) ->
                    avvik.add(Avvik(
                        sykmeldt = fødselsnummer,
                        aktørId = aktørId,
                        arbeidsgiver = organisasjonsnummer,
                        skjæringstidspunkt = skjæringstidspunkt,
                        avvik = skjæringstidspunktTilForHøytAvvik.getValue(skjæringstidspunkt),
                        utbetalteDager = utbetalteDager
                    ))
                }
            }

            return avvik
        }
    }
}