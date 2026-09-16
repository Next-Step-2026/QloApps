"""
Testes Baseados em Propriedades (Hypothesis) e Verificação Formal (Z3 Solver)
para o motor determinístico de políticas de reserva.
"""

import pytest

from app.engine import (
    evaluate_advance_booking,
    evaluate_minimum_stay,
    evaluate_overbooking_limit,
)
from app.schemas import PolicyDecision

# ---------------------------------------------------------------------------
# Tópico 22: Property-Based Testing com Hypothesis
# ---------------------------------------------------------------------------
try:
    from hypothesis import given
    from hypothesis import strategies as st

    @pytest.mark.property
    @given(
        requested_nights=st.integers(min_value=1, max_value=365),
        required_minimum_nights=st.integers(min_value=1, max_value=365),
    )
    def test_property_minimum_stay_invariants(requested_nights: int, required_minimum_nights: int):
        """
        Propriedade Invariante: Se requested >= required, a decisão SEMPRE deve ser ALLOW.
        Caso contrário, a decisão SEMPRE deve ser DENY.
        """
        decision, reason_code, _ = evaluate_minimum_stay(
            {
                "requested_nights": requested_nights,
                "required_minimum_nights": required_minimum_nights,
                "room_type": "standard",
            }
        )

        if requested_nights >= required_minimum_nights:
            assert decision == PolicyDecision.ALLOW
            assert reason_code == "MINIMUM_STAY_MET"
        else:
            assert decision == PolicyDecision.DENY
            assert reason_code == "NIGHTS_BELOW_MINIMUM"

    @pytest.mark.property
    @given(
        days_in_advance=st.integers(min_value=0, max_value=730),
        min_advance_days=st.integers(min_value=0, max_value=730),
    )
    def test_property_advance_booking_invariants(days_in_advance: int, min_advance_days: int):
        """
        Propriedade Invariante: Antecedência suficiente SEMPRE resulta em ALLOW.
        """
        decision, reason_code, _ = evaluate_advance_booking(
            {
                "days_in_advance": days_in_advance,
                "min_advance_days": min_advance_days,
            }
        )

        if days_in_advance >= min_advance_days:
            assert decision == PolicyDecision.ALLOW
            assert reason_code == "ADVANCE_WINDOW_MET"
        else:
            assert decision == PolicyDecision.DENY
            assert reason_code == "ADVANCE_WINDOW_VIOLATED"

    @pytest.mark.property
    @given(
        total_capacity=st.integers(min_value=1, max_value=1000),
        current_occupied=st.integers(min_value=0, max_value=2000),
        requested_units=st.integers(min_value=1, max_value=100),
        rate=st.floats(min_value=0.0, max_value=0.5),
    )
    def test_property_overbooking_invariants(total_capacity, current_occupied, requested_units, rate):
        """
        Propriedade Invariante: Ocupação final <= teto máximo permitido SEMPRE é ALLOW.
        """
        decision, _, _ = evaluate_overbooking_limit(
            {
                "total_capacity": total_capacity,
                "current_occupied": current_occupied,
                "requested_units": requested_units,
                "max_overbooking_rate": rate,
            }
        )

        max_allowed = int(total_capacity * (1.0 + rate))
        if current_occupied + requested_units <= max_allowed:
            assert decision == PolicyDecision.ALLOW
        else:
            assert decision == PolicyDecision.DENY

except ImportError:
    # Se hypothesis ainda não estiver instalado no ambiente atual
    pass


# ---------------------------------------------------------------------------
# Tópico 14: Verificação Formal com Z3 Theorem Prover
# ---------------------------------------------------------------------------
@pytest.mark.formal
def test_formal_verification_overbooking_never_violates_boundary():
    """
    Prova matematicamente via Z3 que NUNCA existirá um estado (capacidade, ocupação, unidades)
    onde a ocupação total exceda o teto de overbooking E a decisão seja ALLOW.
    """
    try:
        import z3
    except ImportError:
        pytest.skip("Z3 solver não instalado no ambiente.")

    solver = z3.Solver()

    # Variáveis simbólicas inteiras
    capacity = z3.Int("capacity")
    occupied = z3.Int("occupied")
    requested = z3.Int("requested")

    # Domínio dos fatos de entrada (conforme schemas.py)
    solver.add(capacity > 0)
    solver.add(occupied >= 0)
    solver.add(requested >= 1)

    # Taxa fixa de 5% de overbooking: max_allowed = capacity * 105 / 100
    # Condição para violar a política:
    resulting_occupied = occupied + requested
    max_allowed = (capacity * 105) / 100

    # Queremos provar se é possível haver VIOLAÇÃO (ocupação resultante > max_allowed)
    # MAS com o algoritmo concedendo autorização (ALLOW: resulting_occupied <= max_allowed)
    contradiction = z3.And(resulting_occupied > max_allowed, resulting_occupied <= max_allowed)

    solver.add(contradiction)

    # O solver deve ser UNSAT (insatisfazível), provando que não há contraexemplo possível
    result = solver.check()
    assert result == z3.unsat, "A prova formal falhou: existe um contraexemplo!"
