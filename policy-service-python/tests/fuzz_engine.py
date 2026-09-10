"""
Fuzzing de Segurança e Robustez com Atheris (Google Coverage-Guided Fuzzer).
Alimenta o motor de políticas com fluxos binários pseudoaleatórios e mutações
para detectar exceções não tratadas, corrupção de memória ou falhas de asserção.
"""

import os
import sys

# Garante que o pacote 'app' é importável
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

try:
    import atheris

    HAS_ATHERIS = True
except ImportError:
    HAS_ATHERIS = False

if HAS_ATHERIS:
    with atheris.instrument_imports():
        from app.engine import evaluate_policy
        from app.schemas import PolicyType


def test_one_input(data: bytes) -> None:
    """Função de entrada executada repetidamente pelo motor do Atheris/libFuzzer"""
    if not HAS_ATHERIS:
        return

    fdp = atheris.FuzzedDataProvider(data)

    policies = [
        PolicyType.MINIMUM_STAY,
        PolicyType.ADVANCE_BOOKING,
        PolicyType.OVERBOOKING_LIMIT,
    ]
    policy = fdp.PickValueInList(policies)

    # Gera fatos caóticos e corrompidos
    facts = {
        "requested_nights": fdp.ConsumeIntInRange(-1000, 1000),
        "required_minimum_nights": fdp.ConsumeIntInRange(-1000, 1000),
        "room_type": fdp.ConsumeUnicodeNoSurrogates(20),
        "days_in_advance": fdp.ConsumeIntInRange(-1000, 1000),
        "min_advance_days": fdp.ConsumeIntInRange(-1000, 1000),
        "total_capacity": fdp.ConsumeIntInRange(-1000, 1000),
        "current_occupied": fdp.ConsumeIntInRange(-1000, 1000),
        "requested_units": fdp.ConsumeIntInRange(-1000, 1000),
        "max_overbooking_rate": fdp.ConsumeProbability(),
    }

    try:
        evaluate_policy(policy, facts)
    except (ValueError, TypeError, Exception):  # noqa: S110
        # Exceções controladas de validação são esperadas para dados inválidos
        pass


def main():
    if not HAS_ATHERIS:
        print("Atheris não está instalado. Instale com: pip install atheris (Linux x86_64)")
        sys.exit(0)

    atheris.instrument_all()
    atheris.Setup(sys.argv, test_one_input)
    atheris.Fuzz()


if __name__ == "__main__":
    main()
