package no.nav.helse.hendelser

import no.nav.helse.Organisasjonsnummer
import no.nav.helse.hendelser.OverstyrArbeidsforhold.ArbeidsforholdOverstyrt.Companion.overstyringFor
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Arbeidsforholdhistorikk
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.PersonHendelse
import no.nav.helse.somOrganisasjonsnummer
import java.time.LocalDate
import java.util.*

class OverstyrArbeidsforhold(
    meldingsreferanseId: UUID,
    private val fødselsnummer: String,
    private val aktørId: String,
    private val skjæringstidspunkt: LocalDate,
    private val overstyrteArbeidsforhold: List<ArbeidsforholdOverstyrt>
) : PersonHendelse(meldingsreferanseId, Aktivitetslogg()) {
    override fun fødselsnummer() = fødselsnummer
    override fun aktørId() = aktørId

    internal fun erRelevant(skjæringstidspunkt: LocalDate) = this.skjæringstidspunkt == skjæringstidspunkt

    internal fun lagre(arbeidsgiver: Arbeidsgiver) {
        val overstyring = overstyrteArbeidsforhold.overstyringFor(arbeidsgiver.organisasjonsnummer().somOrganisasjonsnummer())
        if (overstyring != null) {
            arbeidsgiver.lagreOverstyrArbeidsforhold(skjæringstidspunkt, overstyring)
        }
    }

    class ArbeidsforholdOverstyrt(private val orgnummer: Organisasjonsnummer, private val erAktivt: Boolean) {

        companion object {
            internal fun Iterable<ArbeidsforholdOverstyrt>.overstyringFor(orgnummer: Organisasjonsnummer) = firstOrNull { it.orgnummer == orgnummer }
        }

        internal fun lagre(skjæringstidspunkt: LocalDate, arbeidsforholdhistorikk: Arbeidsforholdhistorikk) {
            if (erAktivt) {
                arbeidsforholdhistorikk.aktiverArbeidsforhold(skjæringstidspunkt)
            } else {
                arbeidsforholdhistorikk.deaktiverArbeidsforhold(skjæringstidspunkt)
            }
        }
    }
}