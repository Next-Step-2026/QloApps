import pytest

CATALOG_FIXTURE = [
    {
        "id": "suite-master-01",
        "type": "ROOM_TYPE",
        "title": "Suíte Master Vista Mar",
        "capacity_adults": 2,
        "amenities": ["vista_mar", "banheira", "ar_condicionado"],
        "aliases": ["suite master", "vista mar", "suite"],
    },
    {
        "id": "standard-casal-02",
        "type": "ROOM_TYPE",
        "title": "Quarto Standard Casal",
        "capacity_adults": 2,
        "amenities": ["ar_condicionado", "wifi"],
        "aliases": ["standard", "casal"],
    },
    {
        "id": "single-solteiro-03",
        "type": "ROOM_TYPE",
        "title": "Quarto Single Individual",
        "capacity_adults": 1,
        "amenities": ["ventilador"],
        "aliases": ["single", "solteiro"],
    },
    {
        "id": "chale-familia-04",
        "type": "ROOM_TYPE",
        "title": "Chalé Família com Piscina e Garagem",
        "capacity_adults": 4,
        "amenities": ["piscina", "estacionamento", "pet_friendly"],
        "aliases": ["chale", "familia"],
    },
]


def test_healthcheck(client):
    """Verifica se o endpoint de healthz responde status 200 e UP."""
    res = client.get("/healthz")
    assert res.status_code == 200
    assert "application/json" in res.headers.get("content-type", "")
    data = res.json()
    assert data["status"] == "UP"


def test_search_parse_success(client):
    """Verifica resposta de sucesso do parse com X-Correlation-ID."""
    payload = {
        "query": "suite para 2 adultos com vista mar",
        "catalog": CATALOG_FIXTURE,
    }
    headers = {"X-Correlation-ID": "corr-pytest-001"}

    res = client.post("/v1/search/parse", json=payload, headers=headers)
    assert res.status_code == 200
    assert "application/json" in res.headers.get("content-type", "")

    data = res.json()
    assert data["correlation_id"] == "corr-pytest-001"
    assert data["extracted_filters"]["adults"] == 2
    assert "vista_mar" in data["extracted_filters"]["amenities"]
    assert data["matching_entity_ids"] == ["suite-master-01"]
    assert data["total_matches"] == 1
    assert "suite" in data["tokens_matched"]


@pytest.mark.parametrize(
    "query,expected_adults",
    [
        ("suite para 2 adultos", 2),
        ("quarto com banheira para 3 pessoas", 3),
        ("quarto para 4 com ar split", 4),
        ("quarto para duas pessoas com wifi", 2),
        ("quarto casal pe na areia climatizado", 2),
        ("apartamento triplo", 3),
        ("chale quadruplo", 4),
        ("quarto solteiro", 1),
    ],
)
def test_search_occupancy_extraction(client, query, expected_adults):
    """Testa extração de ocupação em diversas variações de linguagem natural."""
    payload = {
        "query": query,
        "catalog": CATALOG_FIXTURE,
    }
    res = client.post("/v1/search/parse", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["extracted_filters"]["adults"] == expected_adults


def test_search_amenities_synonyms(client):
    """Valida mapeamento de comodidades e sinônimos (banheira, ar split, pet friendly)."""
    payload = {
        "query": "chale com jacuzzi, ar split, piscina e pet friendly",
        "catalog": CATALOG_FIXTURE,
    }
    res = client.post("/v1/search/parse", json=payload)
    assert res.status_code == 200
    data = res.json()
    amenities = data["extracted_filters"]["amenities"]
    assert "banheira" in amenities  # jacuzzi -> banheira
    assert "ar_condicionado" in amenities  # ar split -> ar_condicionado
    assert "piscina" in amenities
    assert "pet_friendly" in amenities


def test_search_conjunctive_and(client):
    """Valida busca conjuntiva (AND): termo presente em múltiplos quartos."""
    payload = {
        "query": "ar condicionado",
        "catalog": CATALOG_FIXTURE,
    }
    res = client.post("/v1/search/parse", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert len(data["matching_entity_ids"]) == 2
    assert "suite-master-01" in data["matching_entity_ids"]
    assert "standard-casal-02" in data["matching_entity_ids"]


def test_search_capacity_filtering(client):
    """Valida filtro de capacidade mínima (minAdults = 2 exclui quartos de 1 pessoa)."""
    payload = {
        "query": "quarto para 2 pessoas",
        "catalog": CATALOG_FIXTURE,
    }
    res = client.post("/v1/search/parse", json=payload)
    assert res.status_code == 200
    data = res.json()
    # standard-casal-02 tem capacidade 2; single-solteiro-03 tem capacidade 1 e deve ser excluído
    assert "standard-casal-02" in data["matching_entity_ids"]
    assert "single-solteiro-03" not in data["matching_entity_ids"]


def test_search_typo_tolerance(client):
    """Tolerância a erros de digitação: 'vita' é ignorado, 'suite' e 'mar' casam."""
    payload = {
        "query": "suite vita mar",
        "catalog": CATALOG_FIXTURE,
    }
    res = client.post("/v1/search/parse", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["matching_entity_ids"] == ["suite-master-01"]
    assert "suite" in data["tokens_matched"]
    assert "mar" in data["tokens_matched"]
    assert "vita" not in data["tokens_matched"]


def test_search_unknown_terms_returns_zero(client):
    """Consulta apenas com termos inexistentes retorna total_matches == 0."""
    payload = {
        "query": "xptovita qwerty termoinexistente",
        "catalog": CATALOG_FIXTURE,
    }
    res = client.post("/v1/search/parse", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["total_matches"] == 0
    assert data["matching_entity_ids"] == []


# Testes de Validação e Tratamento de Erros (RFC 7807)

def test_search_validation_empty_body(client):
    """Corpo vazio na requisição deve retornar status 400."""
    res = client.post("/v1/search/parse", content=b"")
    assert res.status_code == 400
    assert "application/problem+json" in res.headers.get("content-type", "")
    error = res.json()
    assert error["status"] == 400
    assert "vazio" in error["detail"].lower()


def test_search_validation_missing_query(client):
    """Campo 'query' ausente deve retornar status 400."""
    res = client.post("/v1/search/parse", json={"catalog": []})
    assert res.status_code == 400
    error = res.json()
    assert error["status"] == 400
    assert "query" in error["detail"]


def test_search_validation_empty_query(client):
    """Campo 'query' composto apenas por espaços deve retornar status 400."""
    res = client.post("/v1/search/parse", json={"query": "   \t  ", "catalog": []})
    assert res.status_code == 400
    error = res.json()
    assert error["status"] == 400
    assert "vazio" in error["detail"].lower()


def test_search_validation_query_too_long(client):
    """Query que excede 256 caracteres deve retornar status 400."""
    long_query = "suite " * 60  # > 256 caracteres
    res = client.post("/v1/search/parse", json={"query": long_query, "catalog": []})
    assert res.status_code == 400
    error = res.json()
    assert error["status"] == 400
    assert "256" in error["detail"]


def test_search_validation_missing_catalog(client):
    """Campo 'catalog' ausente ou inválido deve retornar status 400."""
    res = client.post("/v1/search/parse", json={"query": "suite"})
    assert res.status_code == 400
    error = res.json()
    assert error["status"] == 400
    assert "catalog" in error["detail"]


def test_search_validation_catalog_limit_exceeded(client):
    """Catálogo com mais de 100 itens deve retornar status 400 (RN-006)."""
    large_catalog = [{"id": f"room-{i}", "title": f"Quarto {i}"} for i in range(101)]
    res = client.post("/v1/search/parse", json={"query": "quarto", "catalog": large_catalog})
    assert res.status_code == 400
    error = res.json()
    assert error["status"] == 400
    assert "100" in error["detail"]


def test_search_validation_malformed_json(client):
    """Payload JSON sintaticamente malformado deve retornar status 400."""
    res = client.post(
        "/v1/search/parse",
        content=b'{"query": "suite", malformed}',
        headers={"Content-Type": "application/json"},
    )
    assert res.status_code == 400
    error = res.json()
    assert error["status"] == 400
    assert error["type"] == "https://hotel.local/errors/invalid-json"
