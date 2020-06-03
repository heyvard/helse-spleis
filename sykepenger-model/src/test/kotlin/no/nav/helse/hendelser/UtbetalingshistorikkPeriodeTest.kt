package no.nav.helse.hendelser

import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.UtbetalingsdagVisitor
import no.nav.helse.testhelpers.januar
import no.nav.helse.testhelpers.mars
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UtbetalingshistorikkPeriodeTest {
    private companion object {
        private val EN_PERIODE = Periode(1.mars, 1.mars)
        private const val ORGNUMMER = "987654321"
    }

    private lateinit var aktivitetslogg: Aktivitetslogg

    @BeforeEach
    fun setup() {
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `ugyldig periode`() {
        assertDoesNotThrow {
            Utbetalingshistorikk.Periode.Ugyldig(2.januar, 1.januar).valider(aktivitetslogg, EN_PERIODE)
        }
        assertTrue(aktivitetslogg.hasErrors())
    }

    @Test
    fun `RefusjonTilArbeidsgiver mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(1.januar, 1.januar, 1234, 100, ORGNUMMER)
        periode.valider(aktivitetslogg, EN_PERIODE)
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(1.januar, inspektør.sisteDag)
    }

    @Test
    fun `ReduksjonArbeidsgiverRefusjon mappes til utbetalingstidslinje`() {
        val periode =
            Utbetalingshistorikk.Periode.ReduksjonArbeidsgiverRefusjon(1.januar, 1.januar, 1234, 100, ORGNUMMER)
        periode.valider(aktivitetslogg, EN_PERIODE)
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(1.januar, inspektør.sisteDag)
    }

    @Test
    fun `Utbetaling mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.Utbetaling(1.januar, 1.januar, 1234, 100, "81549300")
        periode.valider(aktivitetslogg, EN_PERIODE)
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(1.januar, inspektør.sisteDag)
    }

    @Test
    fun `ReduksjonMedlem mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.ReduksjonMedlem(1.januar, 1.januar, 1234, 100, "81549300")
        periode.valider(aktivitetslogg, EN_PERIODE)
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(1.januar, inspektør.sisteDag)
    }

    @Test
    fun `Ferie mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.Ferie(1.januar, 1.januar)
        periode.valider(aktivitetslogg, EN_PERIODE)
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertEquals(1.januar, inspektør.førsteDag)
        assertEquals(1.januar, inspektør.sisteDag)
    }

    @Test
    fun `Etterbetaling mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.Etterbetaling(1.januar, 1.januar)
        periode.valider(aktivitetslogg, Periode(1.januar, 1.januar))
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertNull(inspektør.førsteDag)
        assertNull(inspektør.sisteDag)
    }

    @Test
    fun `KontertRegnskap mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.KontertRegnskap(1.januar, 1.januar)
        periode.valider(aktivitetslogg, Periode(1.januar, 1.januar))
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertNull(inspektør.førsteDag)
        assertNull(inspektør.sisteDag)
    }

    @Test
    fun `Tilbakeført mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.Tilbakeført(1.januar, 1.januar)
        periode.valider(aktivitetslogg, Periode(1.januar, 1.januar))
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertNull(inspektør.førsteDag)
        assertNull(inspektør.sisteDag)
    }

    @Test
    fun `Konvertert mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.Konvertert(1.januar, 1.januar)
        periode.valider(aktivitetslogg, Periode(1.januar, 1.januar))
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertNull(inspektør.førsteDag)
        assertNull(inspektør.sisteDag)
    }

    @Test
    fun `Opphold mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.Opphold(1.januar, 1.januar)
        periode.valider(aktivitetslogg, Periode(1.januar, 1.januar))
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertNull(inspektør.førsteDag)
        assertNull(inspektør.sisteDag)
    }

    @Test
    fun `Sanksjon mappes til utbetalingstidslinje`() {
        val periode = Utbetalingshistorikk.Periode.Sanksjon(1.januar, 1.januar)
        periode.valider(aktivitetslogg, Periode(1.januar, 1.januar))
        val tidslinje = periode.tidslinje()

        assertFalse(aktivitetslogg.hasWarnings()) { aktivitetslogg.toString() }

        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertNull(inspektør.førsteDag)
        assertNull(inspektør.sisteDag)
    }

    @Test
    fun `Ukjent lager warning`() {
        Utbetalingshistorikk.Periode.Ukjent(1.januar, 1.januar).valider(aktivitetslogg, Periode(1.januar, 1.januar))
        assertFalse(aktivitetslogg.hasErrors())
        assertTrue(aktivitetslogg.hasWarnings())
    }

    @Test
    fun `Ukjent mappes til tom utbetalingstidslinje`() {
        val tidslinje = Utbetalingshistorikk.Periode.Ukjent(1.januar, 1.januar)
            .tidslinje()
        val inspektør = Inspektør().apply { tidslinje.accept(this) }
        assertNull(inspektør.førsteDag)
        assertNull(inspektør.sisteDag)
    }

    @Test
    fun `Ugyldig lager error`() {
        Utbetalingshistorikk.Periode.Ugyldig(1.januar, null).valider(aktivitetslogg, Periode(1.januar, 1.januar))
        assertTrue(aktivitetslogg.hasErrors())
    }

    private class Inspektør : UtbetalingsdagVisitor {
        var førsteDag: LocalDate? = null
        var sisteDag: LocalDate? = null

        private fun visitDag(dag: Utbetalingstidslinje.Utbetalingsdag) {
            førsteDag = førsteDag ?: dag.dato
            sisteDag = dag.dato
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag,
            dato: LocalDate,
            økonomi: Økonomi,
            aktuellDagsinntekt: Double
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.NavDag,
            dato: LocalDate,
            økonomi: Økonomi,
            grad: Prosentdel,
            aktuellDagsinntekt: Double,
            dekningsgrunnlag: Double,
            arbeidsgiverbeløp: Int,
            personbeløp: Int
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag,
            dato: LocalDate,
            økonomi: Økonomi,
            grad: Prosentdel
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag,
            dato: LocalDate,
            økonomi: Økonomi,
            aktuellDagsinntekt: Double
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.Fridag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag,
            dato: LocalDate,
            økonomi: Økonomi,
            grad: Prosentdel,
            aktuellDagsinntekt: Double,
            dekningsgrunnlag: Double,
            arbeidsgiverbeløp: Int,
            personbeløp: Int
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.ForeldetDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }

        override fun visit(
            dag: Utbetalingstidslinje.Utbetalingsdag.UkjentDag,
            dato: LocalDate,
            økonomi: Økonomi
        ) {
            visitDag(dag)
        }
    }
}
