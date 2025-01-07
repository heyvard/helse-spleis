package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass
import no.nav.helse.dto.HendelseskildeDto

internal typealias Melding = KClass<out Any>

data class Hendelseskilde(
    val type: String,
    val meldingsreferanseId: UUID,
    val tidsstempel: LocalDateTime
) {
    internal constructor(
        hendelse: Melding,
        meldingsreferanseId: UUID,
        tidsstempel: LocalDateTime
    ) : this(kildenavn(hendelse), meldingsreferanseId, tidsstempel)

    companion object {
        internal val INGEN = Hendelseskilde("SykdomshistorikkHendelse", UUID.randomUUID(), LocalDateTime.now())

        private fun kildenavn(hendelse: Melding): String =
            hendelse.simpleName ?: "Ukjent"

        internal fun gjenopprett(dto: HendelseskildeDto): Hendelseskilde {
            return Hendelseskilde(
                type = dto.type,
                meldingsreferanseId = dto.meldingsreferanseId,
                tidsstempel = dto.tidsstempel
            )
        }
    }

    override fun toString() = type
    internal fun erAvType(meldingstype: Melding) = this.type == kildenavn(meldingstype)

    // todo: midlertidig fordi "Inntektsmelding" ikke er en SykdomshistorikkHendelse. Alle dager med kilde "Inntektsmelding" må migreres til "BitFraInntektsmelding"
    internal fun erAvType(meldingstype: String) = this.type == meldingstype
    internal fun dto() = HendelseskildeDto(type, meldingsreferanseId, tidsstempel)
}
