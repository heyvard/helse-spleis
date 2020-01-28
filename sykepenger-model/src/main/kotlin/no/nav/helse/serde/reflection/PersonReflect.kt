package no.nav.helse.serde.reflection

import no.nav.helse.person.Person

internal class PersonReflect(person: Person) {
    private val aktørId: String = person["aktørId"]
    private val fødselsnummer: String = person["fødselsnummer"]

    internal fun toMap() = mutableMapOf<String, Any?>(
        "aktørId" to aktørId,
        "fødselsnummer" to fødselsnummer
    )
}
