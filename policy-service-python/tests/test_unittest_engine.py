"""
Testes Unitários Estruturais usando o módulo built-in unittest do Python.
Valida o comportamento determinístico das regras de negócio sem dependências externas.
"""

import unittest

from app.engine import (
    evaluate_advance_booking,
    evaluate_minimum_stay,
    evaluate_overbooking_limit,
    evaluate_policy,
)
from app.schemas import (
    AdvanceBookingFacts,
    MinimumStayFacts,
    OverbookingLimitFacts,
    PolicyDecision,
    PolicyType,
)


class TestReservationPolicyEngineUnittest(unittest.TestCase):
    """Suíte de testes clássica baseada em unittest.TestCase (padrão JUnit)"""

    def setUp(self):
        """Fixture de preparação executada antes de cada teste"""
        self.default_room = "deluxe"

    # --- Testes de Estadia Mínima (MINIMUM_STAY) ---

    def test_minimum_stay_exact_match_allowed(self):
        """Valida que estadia exatamente igual ao mínimo exigido é autorizada (ALLOW)"""
        facts = MinimumStayFacts(
            requested_nights=3,
            required_minimum_nights=3,
            room_type=self.default_room,
        )
        decision, reason_code, explanation = evaluate_minimum_stay(facts)
        self.assertEqual(decision, PolicyDecision.ALLOW)
        self.assertEqual(reason_code, "MINIMUM_STAY_MET")
        self.assertIn("atende ao mínimo obrigatório", explanation)

    def test_minimum_stay_below_minimum_denied(self):
        """Valida que estadia inferior ao mínimo exigido é negada (DENY)"""
        facts = MinimumStayFacts(
            requested_nights=1,
            required_minimum_nights=2,
            room_type=self.default_room,
        )
        decision, reason_code, explanation = evaluate_minimum_stay(facts)
        self.assertEqual(decision, PolicyDecision.DENY)
        self.assertEqual(reason_code, "NIGHTS_BELOW_MINIMUM")
        self.assertIn("inferior ao mínimo obrigatório", explanation)

    # --- Testes de Antecedência de Reserva (ADVANCE_BOOKING) ---

    def test_advance_booking_exact_match_allowed(self):
        """Valida que reserva com antecedência exatamente igual ao mínimo é autorizada (ALLOW)"""
        facts = AdvanceBookingFacts(days_in_advance=5, min_advance_days=5)
        decision, reason_code, _ = evaluate_advance_booking(facts)
        self.assertEqual(decision, PolicyDecision.ALLOW)
        self.assertEqual(reason_code, "ADVANCE_WINDOW_MET")

    def test_advance_booking_same_day_denied(self):
        """Valida que reserva para o mesmo dia (0 dias) quando exigido antecedência é negada (DENY)"""
        facts = AdvanceBookingFacts(days_in_advance=0, min_advance_days=2)
        decision, reason_code, _ = evaluate_advance_booking(facts)
        self.assertEqual(decision, PolicyDecision.DENY)
        self.assertEqual(reason_code, "ADVANCE_WINDOW_VIOLATED")

    # --- Testes de Limite de Overbooking (OVERBOOKING_LIMIT) ---

    def test_overbooking_within_ceiling_allowed(self):
        """Valida que ocupação resultante dentro do limite de overbooking é autorizada (ALLOW)"""
        facts = OverbookingLimitFacts(
            total_capacity=100,
            current_occupied=100,
            requested_units=5,
            max_overbooking_rate=0.05,
        )
        decision, reason_code, _ = evaluate_overbooking_limit(facts)
        self.assertEqual(decision, PolicyDecision.ALLOW)
        self.assertEqual(reason_code, "WITHIN_OVERBOOKING_BUFFER")

    def test_overbooking_exceeding_ceiling_denied(self):
        """Valida que ocupação resultante que excede o limite de overbooking é negada (DENY)"""
        facts = OverbookingLimitFacts(
            total_capacity=100,
            current_occupied=100,
            requested_units=6,
            max_overbooking_rate=0.05,
        )
        decision, reason_code, _ = evaluate_overbooking_limit(facts)
        self.assertEqual(decision, PolicyDecision.DENY)
        self.assertEqual(reason_code, "OVERBOOKING_CAPACITY_EXCEEDED")

    # --- Testes do Roteador Polimórfico (evaluate_policy) ---

    def test_evaluate_policy_router_dispatches_correctly(self):
        """Valida despacho correto do roteador principal para cada tipo de política"""
        decision, reason, _ = evaluate_policy(
            PolicyType.MINIMUM_STAY,
            {"requested_nights": 2, "required_minimum_nights": 2},
        )
        self.assertEqual(decision, PolicyDecision.ALLOW)
        self.assertEqual(reason, "MINIMUM_STAY_MET")


if __name__ == "__main__":
    unittest.main()
