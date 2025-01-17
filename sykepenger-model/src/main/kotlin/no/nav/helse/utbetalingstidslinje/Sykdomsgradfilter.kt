package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.Varselkode
import no.nav.helse.person.Varselkode.RV_VV_4
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.etterlevelse.SubsumsjonObserver.Companion.subsumsjonsformat
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Companion.avvis
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Companion.avvisteDager
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Companion.periode
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi.Companion.totalSykdomsgrad

internal object Sykdomsgradfilter: UtbetalingstidslinjerFilter {

    override fun filter(
        tidslinjer: List<Utbetalingstidslinje>,
        perioder: List<Triple<Periode, IAktivitetslogg, SubsumsjonObserver>>
    ): List<Utbetalingstidslinje> {
        val tidslinjerForSubsumsjon = tidslinjer.subsumsjonsformat()
        val dagerUnderGrensen = periode(tidslinjer).filter { dato -> totalSykdomsgrad(tidslinjer.map { it[dato].økonomi }).erUnderGrensen() }

        avvis(tidslinjer, dagerUnderGrensen.grupperSammenhengendePerioder(), listOf(Begrunnelse.MinimumSykdomsgrad))

        perioder.forEach { (periode, aktivitetslogg, subsumsjonObserver) ->
            Prosentdel.subsumsjon(subsumsjonObserver) { grense ->
                `§ 8-13 ledd 2`(periode, tidslinjerForSubsumsjon, grense, dagerUnderGrensen)
            }
            val avvisteDager = avvisteDager(tidslinjer, periode, Begrunnelse.MinimumSykdomsgrad)
            val harAvvisteDager = avvisteDager.isNotEmpty()
            subsumsjonObserver.`§ 8-13 ledd 1`(periode, avvisteDager.map { it.dato }, tidslinjerForSubsumsjon)
            if (harAvvisteDager) aktivitetslogg.varsel(RV_VV_4)
            else aktivitetslogg.info("Ingen avviste dager på grunn av 20 %% samlet sykdomsgrad-regel for denne perioden")
        }
        return tidslinjer
    }
}
