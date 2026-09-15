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

### 3. Execução dos Testes Automatizados com Cobertura (pytest-cov)
```bash
PYTHONPATH=. pytest
```
Para visualizar o relatório HTML gerado pelo `pytest-cov`:
```bash
# O relatório interativo é gerado em htmlcov/index.html
xdg-open htmlcov/index.html # ou abra no seu navegador
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

---

## Testes de Mutação (Mutation Testing com `mutmut`)

O projeto está configurado via `setup.cfg` para rodar testes de mutação direcionados ao módulo `app/analyzer.py`:

### 1. Executar os testes de mutação
```bash
./.venv/bin/mutmut run
```

### 2. Ver o resumo dos mutantes sobreviventes
```bash
./.venv/bin/mutmut results
```

### 3. Visualizar o diff das mutações sobreviventes no terminal
```bash
# Exibir o diff de todos os mutantes sobreviventes
./.venv/bin/mutmut show survived

# Exibir o diff de um mutante específico
./.venv/bin/mutmut show app.analyzer.x_validate_dimensions__mutmut_2
```

### 4. Navegar interativamente via terminal (TUI)
Para navegar visualmente pelos mutantes em um terminal interativo:
```bash
./.venv/bin/mutmut browse
```
*(Use as setas para navegar, `Enter` para inspecionar e `q` para sair)*

---

## Testes de Fuzzing (Fuzz Testing no `analyzer.py` e Pipeline)

O projeto conta com duas camadas complementares de Fuzzing:

### 1. Fuzzing Baseado em Propriedades e Invariantes (`pytest -m fuzz`)
Testa exaustivamente o `analyzer.py` sob espaços de cores exóticos (`RGB`, `RGBA`, `L`, `1`, `P`, `CMYK`, `YCbCr`, `HSV`), geometrias extremas/limítrofes (1x1, 2x2, cortes de 1px), ruídos arbitrários de bytes e números de ponto flutuante extremos:
```bash
./.venv/bin/pytest -m fuzz -v
```

### 2. Harness de Mutação de Bytes (`tests/fuzz_harness.py`)
Gera mutações aleatórias em streams binários JPEG/PNG (bit flips, deleção/inserção de bytes, truncamento) para garantir que decodificadores e avaliadores não causem memory leaks, crashes não tratados ou exceções inesperadas:
```bash
# Executa 2.000 iterações de fuzzing com semente reproduzível
./.venv/bin/python tests/fuzz_harness.py --iterations 2000 --seed 42
```

