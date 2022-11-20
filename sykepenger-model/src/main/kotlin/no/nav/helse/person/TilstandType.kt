package no.nav.helse.person

enum class TilstandType {
    AVVENTER_HISTORIKK,
    AVVENTER_GODKJENNING,
    AVVENTER_SIMULERING,
    TIL_UTBETALING,
    TIL_INFOTRYGD,
    AVSLUTTET,
    AVSLUTTET_UTEN_UTBETALING,
    REVURDERING_FEILET,
    UTBETALING_FEILET,
    START,
    AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
    AVVENTER_BLOKKERENDE_PERIODE,
    AVVENTER_VILKÅRSPRØVING,
    AVVENTER_REVURDERING,
    AVVENTER_GJENNOMFØRT_REVURDERING,
    AVVENTER_HISTORIKK_REVURDERING,
    AVVENTER_VILKÅRSPRØVING_REVURDERING,
    AVVENTER_SIMULERING_REVURDERING,
    AVVENTER_GODKJENNING_REVURDERING
}
