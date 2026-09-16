"""
@file engine.py
@brief Motor Determinístico de Avaliação de Regras de Reserva.
@details Implementa políticas de reserva sem efeitos colaterais ou dependências externas.
"""

from typing import Any

from app.schemas import (
    AdvanceBookingFacts,
    MinimumStayFacts,
    OverbookingLimitFacts,
    PolicyDecision,
    PolicyType,
)


def evaluate_minimum_stay(
    facts: dict[str, Any] | MinimumStayFacts,
) -> tuple[PolicyDecision, str, str]:
    """
    @brief Avalia o cumprimento da regra de estadia mínima (MINIMUM_STAY).
    @param facts Dicionário com dados brutos ou instância de MinimumStayFacts.
    @return tuple[PolicyDecision, str, str] Decisão (ALLOW/DENY), reason_code e justificativa.
    @throws ValidationError Se os dados informados violarem os limites do schema.
    @note Thread-safe: Função pura, sem dependência ou alteração de estado global.
    """
    data = facts if isinstance(facts, MinimumStayFacts) else MinimumStayFacts(**facts)

    requested_nights = data.requested_nights
    required_min_nights = data.required_minimum_nights
    room_type = data.room_type

    if requested_nights >= required_min_nights:
        decision = PolicyDecision.ALLOW
        reason_code = "MINIMUM_STAY_MET"
        explanation = (
            f"Stay of {requested_nights} night(s) meets the mandatory minimum "
            f"of {required_min_nights} night(s) for room type {room_type}."
        )
    else:
        decision = PolicyDecision.DENY
        reason_code = "NIGHTS_BELOW_MINIMUM"
        explanation = (
            f"Requested stay of {requested_nights} night(s) is below the mandatory "
            f"minimum of {required_min_nights} night(s) for room type {room_type}."
        )

    return decision, reason_code, explanation


def evaluate_advance_booking(
    facts: dict[str, Any] | AdvanceBookingFacts,
) -> tuple[PolicyDecision, str, str]:
    """
    @brief Avalia o cumprimento da antecedência mínima de compra (ADVANCE_BOOKING).
    @param facts Dicionário com dados brutos ou instância de AdvanceBookingFacts.
    @return tuple[PolicyDecision, str, str] Decisão (ALLOW/DENY), reason_code e justificativa.
    @throws ValidationError se os dias informados forem negativos.
    """
    data = facts if isinstance(facts, AdvanceBookingFacts) else AdvanceBookingFacts(**facts)

    days_in_advance = data.days_in_advance
    min_advance_days = data.min_advance_days

    if days_in_advance >= min_advance_days:
        decision = PolicyDecision.ALLOW
        reason_code = "ADVANCE_WINDOW_MET"
        explanation = (
            f"Advance booking of {days_in_advance} day(s) meets the minimum requirement of {min_advance_days} day(s)."
        )
    else:
        decision = PolicyDecision.DENY
        reason_code = "ADVANCE_WINDOW_VIOLATED"
        explanation = f"Advance booking of {days_in_advance} day(s) is insufficient against the requirement of {min_advance_days} day(s)."

    return decision, reason_code, explanation


def evaluate_overbooking_limit(
    facts: dict[str, Any] | OverbookingLimitFacts,
) -> tuple[PolicyDecision, str, str]:
    """
    @brief Avalia a conformidade com o limite máximo de overbooking (OVERBOOKING_LIMIT).
    @param facts Dicionário com dados brutos ou instância de OverbookingLimitFacts.
    @return tuple[PolicyDecision, str, str] Decisão (ALLOW/DENY), reason_code e justificativa.
    @throws ValidationError Se total_capacity <= 0 ou taxas forem negativas.
    @warning A capacidade autorizada é truncada para baixo (int), evitando frações de quartos.
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
            f"Resulting occupancy ({resulting_occupied}/{total_capacity} = {resulting_pct}%) "
            f"is within the maximum allowed limit of {max_pct}% ({max_allowed_units} units)."
        )
    else:
        decision = PolicyDecision.DENY
        reason_code = "OVERBOOKING_CAPACITY_EXCEEDED"
        explanation = (
            f"Resulting occupancy ({resulting_occupied}/{total_capacity} = {resulting_pct}%) "
            f"exceeds the maximum allowed limit of {max_pct}% ({max_allowed_units} units)."
        )

    return decision, reason_code, explanation


def evaluate_policy(policy: PolicyType, facts: dict[str, Any]) -> tuple[PolicyDecision, str, str]:
    """
    @brief Roteador determinístico que despacha a avaliação para a estratégia correspondente.
    @param policy Tipo enumerado da política a ser executada.
    @param facts Dicionário de fatos contextuais a serem validados.
    @return tuple[PolicyDecision, str, str] Decisão, reason_code e justificativa.
    @throws ValueError Se uma política desconhecida for fornecida.
    """
    if policy == PolicyType.MINIMUM_STAY:
        return evaluate_minimum_stay(facts)
    elif policy == PolicyType.ADVANCE_BOOKING:
        return evaluate_advance_booking(facts)
    elif policy == PolicyType.OVERBOOKING_LIMIT:
        return evaluate_overbooking_limit(facts)
    else:
        # Código defensivo: garante erro explícito caso novos membros sejam adicionados ao PolicyType
        raise ValueError(f"Unknown policy: {policy}")
