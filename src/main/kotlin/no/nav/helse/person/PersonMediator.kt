package no.nav.helse.person

import no.nav.helse.behov.BehovProducer
import no.nav.helse.hendelse.Inntektsmelding
import no.nav.helse.hendelse.NySykepengesøknad
import no.nav.helse.hendelse.SendtSykepengesøknad
import no.nav.helse.hendelse.Sykepengehistorikk
import no.nav.helse.oppgave.OppgaveProducer
import no.nav.helse.person.domain.Person
import no.nav.helse.person.domain.PersonObserver
import no.nav.helse.person.domain.Sakskompleks.TilstandType.SKAL_TIL_INFOTRYGD
import no.nav.helse.person.domain.SakskompleksObserver
import no.nav.helse.person.domain.UtenforOmfangException
import no.nav.helse.sakskompleks.SakskompleksProbe

internal class PersonMediator(private val personRepository: PersonRepository,
                              private val sakskompleksProbe: SakskompleksProbe = SakskompleksProbe(),
                              private val behovProducer: BehovProducer,
                              private val oppgaveProducer: OppgaveProducer = OppgaveProducer()) : PersonObserver{

    override fun personEndret(person: Person) {
        personRepository.lagrePerson(person)
    }

    override fun sakskompleksHarBehov(event: SakskompleksObserver.NeedEvent) {
        behovProducer.sendNyttBehov(event.type.toString(), mapOf(
            "aktørId" to event.aktørId,
            "organisasjonsnummer" to event.organisasjonsnummer,
            "sakskompleksId" to event.sakskompleksId))
    }

    fun håndterNySøknad(sykepengesøknad: NySykepengesøknad) =
        try {
            finnPerson(sykepengesøknad.aktørId)
                .also { person ->
                    person.håndterNySøknad(sykepengesøknad)
                }
        } catch (err: UtenforOmfangException) {
            sakskompleksProbe.utenforOmfang(err, sykepengesøknad)
        }

    fun håndterSendtSøknad(sykepengesøknad: SendtSykepengesøknad) =
        try {
            finnPerson(sykepengesøknad.aktørId)
                .also { person ->
                    person.håndterSendtSøknad(sykepengesøknad)
                }
        } catch (err: UtenforOmfangException) {
            sakskompleksProbe.utenforOmfang(err, sykepengesøknad)
        }

    fun håndterInntektsmelding(inntektsmelding: Inntektsmelding) =
        finnPerson(inntektsmelding.aktørId()).also { person ->
            person.håndterInntektsmelding(inntektsmelding)
        }

    fun håndterSykepengehistorikk(sykepengehistorikk: Sykepengehistorikk) {
        finnPerson(sykepengehistorikk.aktørId).also { person ->
            person.håndterSykepengehistorikk(sykepengehistorikk)
        }
    }

    override fun sakskompleksEndret(event: SakskompleksObserver.StateChangeEvent) {
        if (event.currentState == SKAL_TIL_INFOTRYGD) {
            oppgaveProducer.opprettOppgave("AAAAAAA! HELP!")
        }
    }

    private fun finnPerson(aktørId: String) =
        (personRepository.hentPerson(aktørId) ?: Person(aktørId = aktørId)).also {
            it.addObserver(this)
            it.addObserver(sakskompleksProbe)
        }

}
