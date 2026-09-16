# Testes E2E com Playwright: Feature Actionable Entity Search

Esta suíte de testes ponta a ponta (E2E) com **Playwright + pytest** valida exclusivamente a feature **Actionable Entity Search** (`modules/qloactionablesearch` + `search-service-cpp`).

Ela simula a interação real de um usuário no navegador, testando a busca em linguagem natural, extração semântica de entidades e integração com o motor C++.

---

## 1. Estrutura dos Arquivos

```text
tests/e2e/
├── conftest.py                # Setup de sessão autenticada e auto-start do motor C++
├── test_actionable_search.py  # Testes da feature: buscas por 'suite', hóspedes, comodidades e validações
├── requirements.txt           # Dependências da suíte (playwright, pytest-playwright)
└── .gitignore                 # Ignora artefatos de sessão (.auth/), traces e screenshots
```

---

## 2. Cenários Testados na Feature

- **`test_actionable_search_page_renders`**: Garante que o painel administrativo da feature carrega o formulário, campo `#search_query` e botão de busca.
- **`test_actionable_search_suite_mar_two_adults`**: Busca `"suite para 2 adultos com vista mar"` e valida tokens (`suite`, `vista`, `mar`), capacidade (`2 Adult(s)`), comodidade (`vista_mar`) e o quarto casado (**Suíte Master Vista Mar** / `room-suite-01`).
- **`test_actionable_search_standard_casal`**: Busca `"quarto standard casal com ar condicionado"` e valida a entidade casada (**Quarto Standard Casal** / `room-std-02`).
- **`test_actionable_search_empty_query_validation`**: Valida que queries em branco disparam o aviso de contingência na interface.

---

## 3. Como Executar os Testes

```bash
# Execução padrão no terminal (Headless)
python3 -m pytest tests/e2e -v

# Execução visual abrindo o navegador (Headed)
python3 -m pytest tests/e2e -v --headed

# Execução visual com câmera lenta (debug passo a passo)
python3 -m pytest tests/e2e -v --headed --slowmo 500
```

---

## 4. Variáveis de Ambiente Suportadas

| Variável | Padrão | Descrição |
|---|---|---|
| `QLOAPPS_BASE_URL` | `http://127.0.0.1:8080` | URL base do QloApps |
| `QLOAPPS_ADMIN_EMAIL` | `joaolisboa@google.com` | Email do administrador para autenticação |
| `QLOAPPS_ADMIN_PASSWORD` | `Password` | Senha do administrador |
