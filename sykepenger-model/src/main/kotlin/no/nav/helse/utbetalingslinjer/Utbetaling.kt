package no.nav.helse.utbetalingslinjer

import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.UtbetalingVisitor
import no.nav.helse.utbetalingslinjer.Fagområde.SPREF
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag.Companion.arbeidsgiverUtbetaling
import java.time.LocalDate
import java.time.LocalDateTime

// Understands related payment activities for an Arbeidsgiver
internal class Utbetaling
    private constructor(
        private val utbetalingstidslinje: Utbetalingstidslinje,
        private val arbeidsgiverUtbetalingslinjer: Utbetalingslinjer,
        private val personUtbetalingslinjer: Utbetalingslinjer,
        private val tidsstempel: LocalDateTime
    ) {

    internal constructor(
        fødselsnummer: String,
        organisasjonsnummer: String,
        utbetalingstidslinje: Utbetalingstidslinje,
        sisteDato: LocalDate,
        aktivitetslogg: Aktivitetslogg,
        tidligere: Utbetaling?
    ) : this(
        utbetalingstidslinje,
        buildArb(organisasjonsnummer, utbetalingstidslinje, sisteDato, aktivitetslogg, tidligere),
        buildPerson(fødselsnummer, utbetalingstidslinje, sisteDato, aktivitetslogg, tidligere),
        LocalDateTime.now()
    )

    internal fun arbeidsgiverUtbetalingslinjer() = arbeidsgiverUtbetalingslinjer

    internal fun personUtbetalingslinjer() = personUtbetalingslinjer

    companion object {

        private fun buildArb(
            organisasjonsnummer: String,
            tidslinje: Utbetalingstidslinje,
            sisteDato: LocalDate,
            aktivitetslogg: Aktivitetslogg,
            tidligere: Utbetaling?
        ) = Utbetalingslinjer(
            organisasjonsnummer,
            SPREF,
            SpennBuilder(tidslinje, sisteDato, arbeidsgiverUtbetaling).result()
        )
            .forskjell(tidligere?.arbeidsgiverUtbetalingslinjer ?: Utbetalingslinjer(organisasjonsnummer, SPREF))
            .also {
                if (it.isEmpty())
                    aktivitetslogg.info("Ingen utbetalingslinjer bygget")
                else
                    aktivitetslogg.info("Utbetalingslinjer bygget vellykket")
            }

        private fun buildPerson(
            fødselsnummer: String,
            tidslinje: Utbetalingstidslinje,
            sisteDato: LocalDate,
            aktivitetslogg: Aktivitetslogg,
            tidligere: Utbetaling?
        ): Utbetalingslinjer {
            return Utbetalingslinjer(fødselsnummer, Fagområde.SP)
        }
    }

    internal fun accept(visitor: UtbetalingVisitor) {
        visitor.preVisitUtbetaling(this, tidsstempel)
        utbetalingstidslinje.accept(visitor)
        visitor.preVisitArbeidsgiverUtbetalingslinjer(arbeidsgiverUtbetalingslinjer)
        arbeidsgiverUtbetalingslinjer.accept(visitor)
        visitor.postVisitArbeidsgiverUtbetalingslinjer(arbeidsgiverUtbetalingslinjer)
        visitor.preVisitPersonUtbetalingslinjer(personUtbetalingslinjer)
        personUtbetalingslinjer.accept(visitor)
        visitor.postVisitPersonUtbetalingslinjer(personUtbetalingslinjer)
        visitor.postVisitUtbetaling(this, tidsstempel)
    }

    internal fun utbetalingstidslinje() = utbetalingstidslinje
}


