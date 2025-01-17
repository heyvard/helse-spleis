package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDateTime
import java.util.UUID

internal class V115LagreVilkårsgrunnlagFraInfotrygd : JsonMigration(version = 115) {

    override val description: String = "Lagre vilkårsgrunnlag fra Infotrygd"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        val skjæringstidspunkterForVilkårsgrunnlag = jsonNode["vilkårsgrunnlagHistorikk"]
            .firstOrNull()?.get("vilkårsgrunnlag")
            ?.filter { vilkårsgrunnlag -> vilkårsgrunnlag["type"].asText() == "Infotrygd" }
            ?.map { it["skjæringstidspunkt"].asText() }

        jsonNode["arbeidsgivere"].forEach { arbeidsgiver ->
            val infotrygdInntektsopplysninger = arbeidsgiver["inntektshistorikk"].firstOrNull()?.get("inntektsopplysninger")
                ?.filter { inntektsopplysning -> !inntektsopplysning.has("skatteopplysninger") && inntektsopplysning["kilde"].asText() == "INFOTRYGD" }

            if(skjæringstidspunkterForVilkårsgrunnlag != null && infotrygdInntektsopplysninger != null) {
                lagreVilkårsgrunnlagFraInfotrygd(infotrygdInntektsopplysninger, skjæringstidspunkterForVilkårsgrunnlag, jsonNode)
            }
        }
    }

    private fun lagreVilkårsgrunnlagFraInfotrygd(
        inntektsopplysninger: List<JsonNode>,
        skjæringstidspunkterForVilkårsgrunnlag: List<String>,
        person: ObjectNode
    ) {
        var nyttInnslag: ObjectNode? = null

        inntektsopplysninger.forEach { inntektsopplysning ->
            val dato = inntektsopplysning["dato"].asText()
            if (!skjæringstidspunkterForVilkårsgrunnlag.contains(dato)) {
                if (nyttInnslag == null) {
                    nyttInnslag = person["vilkårsgrunnlagHistorikk"].first().deepCopy()
                    nyttInnslag!!.put("id", UUID.randomUUID().toString())
                    nyttInnslag!!.put("opprettet", LocalDateTime.now().toString())
                    person.withArray("vilkårsgrunnlagHistorikk").insert(0, nyttInnslag)
                }
                val vilkårsgrunnlag = nyttInnslag!!.withArray("vilkårsgrunnlag").addObject()
                vilkårsgrunnlag.put("skjæringstidspunkt", dato)
                vilkårsgrunnlag.put("type", "Infotrygd")
                V114LagreSykepengegrunnlag.genererSykepengegrunnlag(vilkårsgrunnlag, person)
            }
        }
    }
}


