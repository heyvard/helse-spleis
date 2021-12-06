package no.nav.helse.serde.api.builders

import no.nav.helse.hendelser.Simulering
import no.nav.helse.serde.api.SimuleringsdataDTO
import no.nav.helse.serde.api.UtbetalingerDTO
import no.nav.helse.utbetalingslinjer.*
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Klassekode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Satstype
import no.nav.helse.utbetalingslinjer.Utbetalingslinje
import java.time.LocalDate
import java.time.LocalDateTime

internal class OppdragBuilder : BuilderState() {
    private lateinit var fagsystemId: String
    private lateinit var tidsstempel: LocalDateTime
    private var simuleringsResultat: SimuleringsdataDTO? = null
    private val utbetalingslinjer = mutableListOf<UtbetalingerDTO.UtbetalingslinjeDTO>()

    internal fun build() = OppdragDTO(
        fagsystemId = fagsystemId,
        tidsstempel = tidsstempel,
        simuleringsResultat = simuleringsResultat,
        utbetalingslinjer = utbetalingslinjer
    )

    override fun preVisitOppdrag(
        oppdrag: Oppdrag,
        fagsystemId: String,
        totalBeløp: Int,
        nettoBeløp: Int,
        tidsstempel: LocalDateTime,
        endringskode: Endringskode,
        avstemmingsnøkkel: Long?,
        status: Oppdragstatus?,
        overføringstidspunkt: LocalDateTime?,
        simuleringsResultat: Simulering.SimuleringResultat?
    ) {
        this.fagsystemId = fagsystemId
        this.tidsstempel = tidsstempel
        this.simuleringsResultat = simuleringsResultat?.let {
            SimuleringsdataDTO(
                totalbeløp = simuleringsResultat.totalbeløp,
                perioder = simuleringsResultat.perioder.map { periode ->
                    SimuleringsdataDTO.PeriodeDTO(
                        fom = periode.periode.start,
                        tom = periode.periode.endInclusive,
                        utbetalinger = periode.utbetalinger.map { utbetaling ->
                            SimuleringsdataDTO.UtbetalingDTO(
                                utbetalesTilId = utbetaling.utbetalesTil.id,
                                utbetalesTilNavn = utbetaling.utbetalesTil.navn,
                                forfall = utbetaling.forfallsdato,
                                detaljer = utbetaling.detaljer.map { detaljer ->
                                    SimuleringsdataDTO.DetaljerDTO(
                                        faktiskFom = detaljer.periode.start,
                                        faktiskTom = detaljer.periode.endInclusive,
                                        konto = detaljer.konto,
                                        beløp = detaljer.beløp,
                                        tilbakeføring = detaljer.tilbakeføring,
                                        sats = detaljer.sats.sats,
                                        typeSats = detaljer.sats.type,
                                        antallSats = detaljer.sats.antall,
                                        uføregrad = detaljer.uføregrad,
                                        klassekode = detaljer.klassekode.kode,
                                        klassekodeBeskrivelse = detaljer.klassekode.beskrivelse,
                                        utbetalingstype = detaljer.utbetalingstype,
                                        refunderesOrgNr = detaljer.refunderesOrgnummer
                                    )
                                },
                                feilkonto = utbetaling.feilkonto
                            )
                        }
                    )
                }
            )
        }
    }

    override fun visitUtbetalingslinje(
        linje: Utbetalingslinje,
        fom: LocalDate,
        tom: LocalDate,
        satstype: Satstype,
        beløp: Int?,
        aktuellDagsinntekt: Int?,
        grad: Double?,
        delytelseId: Int,
        refDelytelseId: Int?,
        refFagsystemId: String?,
        endringskode: Endringskode,
        datoStatusFom: LocalDate?,
        klassekode: Klassekode
    ) {
        val erOpphør = datoStatusFom != null
        if (!erOpphør) {
            utbetalingslinjer.add(UtbetalingerDTO.UtbetalingslinjeDTO(
                fom = fom,
                tom = tom,
                dagsats = beløp!!,
                grad = grad!!
            ))
        }
    }

    override fun postVisitOppdrag(
        oppdrag: Oppdrag,
        fagsystemId: String,
        totalBeløp: Int,
        nettoBeløp: Int,
        tidsstempel: LocalDateTime,
        endringskode: Endringskode,
        avstemmingsnøkkel: Long?,
        status: Oppdragstatus?,
        overføringstidspunkt: LocalDateTime?,
        simuleringsResultat: Simulering.SimuleringResultat?
    ) {
        popState()
    }
}
