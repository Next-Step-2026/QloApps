"""
Deterministic Policy Evaluation Engine
"""
from typing import Dict, Any, Tuple, Union
from app.schemas import (
    PolicyType,
    PolicyDecision,
    MinimumStayFacts,
    AdvanceBookingFacts,
    OverbookingLimitFacts,
)


def evaluate_minimum_stay(
    facts: Union[Dict[str, Any], MinimumStayFacts]
) -> Tuple[PolicyDecision, str, str]:
    """
    Avalia a política MINIMUM_STAY
    """
    data = facts if isinstance(facts, MinimumStayFacts) else MinimumStayFacts(**facts)

    requested_nights = data.requested_nights
    required_min_nights = data.required_minimum_nights
    room_type = data.room_type

    if requested_nights >= required_min_nights:
        decision = PolicyDecision.ALLOW
        reason_code = "MINIMUM_STAY_MET"
        explanation = (
            f"Estadia de {requested_nights} noite(s) atende ao mínimo obrigatório "
            f"de {required_min_nights} noites para a categoria {room_type}."
        )
    else:
        decision = PolicyDecision.DENY
        reason_code = "NIGHTS_BELOW_MINIMUM"
        explanation = (
            f"Estadia de {requested_nights} noite(s) solicitada é inferior ao mínimo "
            f"obrigatório de {required_min_nights} noites para a categoria {room_type}."
        )

    return decision, reason_code, explanation


def evaluate_advance_booking(
    facts: Union[Dict[str, Any], AdvanceBookingFacts]
) -> Tuple[PolicyDecision, str, str]:
    """
    Avalia a política ADVANCE_BOOKING
    """
    data = facts if isinstance(facts, AdvanceBookingFacts) else AdvanceBookingFacts(**facts)

    days_in_advance = data.days_in_advance
    min_advance_days = data.min_advance_days

    if days_in_advance >= min_advance_days:
        decision = PolicyDecision.ALLOW
        reason_code = "ADVANCE_WINDOW_MET"
        explanation = (
            f"Antecedência de {days_in_advance} dia(s) atende ao requisito mínimo "
            f"de {min_advance_days} dia(s)."
        )
    else:
        decision = PolicyDecision.DENY
        reason_code = "ADVANCE_WINDOW_VIOLATED"
        explanation = (
            f"Antecedência de {days_in_advance} dia(s) é insuficiente frente "
            f"ao requisito de {min_advance_days} dia(s)."
        )

    return decision, reason_code, explanation


def evaluate_overbooking_limit(
    facts: Union[Dict[str, Any], OverbookingLimitFacts]
) -> Tuple[PolicyDecision, str, str]:
    """
    Avalia a política OVERBOOKING_LIMIT
    """
    data = facts if isinstance(facts, OverbookingLimitFacts) else OverbookingLimitFacts(**facts)

    total_capacity = data.total_capacity
    current_occupied = data.current_occupied
    requested_units = data.requested_units
    max_overbooking_rate = data.max_overbooking_rate

    # Arredondamento para baixo da capacidade máxima autorizada
    max_allowed_units = int(total_capacity * (1.0 + max_overbooking_rate))
    resulting_occupied = current_occupied + requested_units

    resulting_pct = round((resulting_occupied / total_capacity) * 100) if total_capacity > 0 else 0
    max_pct = round((1.0 + max_overbooking_rate) * 100)

    if resulting_occupied <= max_allowed_units:
        decision = PolicyDecision.ALLOW
        reason_code = "WITHIN_OVERBOOKING_BUFFER"
        explanation = (
            f"Ocupação resultante ({resulting_occupied}/{total_capacity} = {resulting_pct}%) "
            f"dentro do limite máximo permitido de {max_pct}% ({max_allowed_units} vagas)."
        )
    else:
        decision = PolicyDecision.DENY
        reason_code = "OVERBOOKING_CAPACITY_EXCEEDED"
        explanation = (
            f"Ocupação resultante ({resulting_occupied}/{total_capacity} = {resulting_pct}%) "
            f"excede o teto máximo permitido de {max_pct}% ({max_allowed_units} vagas)."
        )

    return decision, reason_code, explanation


def evaluate_policy(policy: PolicyType, facts: Dict[str, Any]) -> Tuple[PolicyDecision, str, str]:
    """
    Roteador determinístico de avaliação de políticas
    """
    if policy == PolicyType.MINIMUM_STAY:
        return evaluate_minimum_stay(facts)
    elif policy == PolicyType.ADVANCE_BOOKING:
        return evaluate_advance_booking(facts)
    elif policy == PolicyType.OVERBOOKING_LIMIT:
        return evaluate_overbooking_limit(facts)
    else:
        # Código defensivo: garante erro explícito caso novos membros sejam adicionados ao PolicyType
        raise ValueError(f"Política desconhecida: {policy}")
