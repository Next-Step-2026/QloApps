# Microserviço de Inspeção Visual e Métricas Objetivas de Imagem (QLO-FEAT-002)

Serviço local rápido em Python (FastAPI + Pillow) para avaliação determinística da qualidade de fotografias de inspeção de quartos de hotel (governança/housekeeping).

## Métricas Avaliadas
- **Resolução Mínima:** Largura $\ge 800$ e Altura $\ge 600$ (ou retrato $\ge 600 \times 800$).
- **Luminância Média:** Conversão para escala de cinza (`L`) e média $[0..255]$ (`UNDEREXPOSED` < 40, `OVEREXPOSED` > 220, `OPTIMAL`).
- **Nitidez:** Variância do filtro de gradiente/Laplaciano (`BLURRY` < 50.0, `SHARP` $\ge$ 50.0).

## Limites e Regras de Upload (Defesa em Profundidade)
- **Limite Máximo por Foto:** 5 MB (`MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024`).
- **Sincronização PHP/Python:** Esse limite de 5 MB é validado em duas camadas:
  1. No PHP (`AdminVisualInspectionController::MAX_FILE_SIZE_BYTES = 5242880`) para *fail-fast* antes da chamada cURL.
  2. Na API FastAPI (`MAX_FILE_SIZE_BYTES`) para validação de borda em requisições diretas.
  > Se o limite de tamanho for alterado, ambas as constantes (no PHP e no Python) devem ser atualizadas em conjunto.

---

## Como Executar Localmente

### 1. Instalação das dependências
```bash
pip install -r requirements.txt
```

### 2. Geração das Fixtures de Teste
```bash
python tests/generate_fixtures.py
```

### 3. Execução dos Testes Automatizados (Unitários, API e BDD)
```bash
PYTHONPATH=. pytest tests/ -v
```

### 4. Inicialização do Servidor HTTP (Porta 8102)
```bash
uvicorn app.main:app --host 127.0.0.1 --port 8102
```

---

## Exemplos de Uso via cURL

### Health check
```bash
curl -s http://127.0.0.1:8102/healthz
```

### Envio de Foto para Inspeção
```bash
curl -s -X POST http://127.0.0.1:8102/v1/visual-inspections \
  -H "X-Correlation-ID: req-inspect-101" \
  -F "file=@tests/fixtures/room_sharp.jpg" \
  -F "room_id=room-101" \
  -F "inspection_id=INSP-2026-001" | jq .
```
