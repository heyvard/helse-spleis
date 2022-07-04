package no.nav.helse.hendelser

import java.time.LocalDate
import java.util.UUID
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.ArbeidstakerHendelse
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Inntektshistorikk
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.økonomi.Inntekt


class OverstyrInntekt(
    meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    organisasjonsnummer: String,
    internal val inntekt: Inntekt,
    internal val skjæringstidspunkt: LocalDate
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer) {

    internal fun erRelevant(skjæringstidspunkt: LocalDate) = this.skjæringstidspunkt == skjæringstidspunkt

    internal fun addInntekt(inntektshistorikk: Inntektshistorikk) {
        inntektshistorikk.append { addSaksbehandler(skjæringstidspunkt, meldingsreferanseId(), inntekt) }
    }

    internal fun tilRevurderingAvvistEvent(): PersonObserver.RevurderingAvvistEvent =
        PersonObserver.RevurderingAvvistEvent(
            fødselsnummer = fødselsnummer,
            errors = this.errorsAndWorse()
        )

    internal fun loggførHendelsesreferanse(person: Person) {
        person.loggførHendelsesreferanse(organisasjonsnummer, skjæringstidspunkt, this)
    }

    internal fun leggTil(hendelseIder: MutableSet<Dokumentsporing>) {
        hendelseIder.add(Dokumentsporing.overstyrInntekt(meldingsreferanseId()))
    }

    internal fun valider(arbeidsgivere: MutableList<Arbeidsgiver>) {
        if (arbeidsgivere.none { it.harSykdomFor(skjæringstidspunkt) }) {
            severe("Kan ikke overstyre inntekt hvis vi ikke har en arbeidsgiver med sykdom for skjæringstidspunktet")
        }
    }
}
