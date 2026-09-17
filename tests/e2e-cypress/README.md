# Testes End-to-End (E2E) com Cypress — Inspeção Visual

Este diretório contém a suíte completa de testes automatizados E2E utilizando Cypress para o módulo **qlovisualinspection** (`QLO-FEAT-002`).

## Estrutura

```
tests/e2e-cypress/
├── cypress.config.js               # Configurações do Cypress (baseUrl, timeouts, viewports)
├── package.json                    # Dependências do Cypress
├── cypress/
│   ├── fixtures/                   # Imagens determinísticas (sharp, dark, blurry)
│   ├── support/
│   │   ├── e2e.js                  # Setup global e tratamento de exceções
│   │   └── commands.js             # Comandos customizados (cy.loginAdmin, cy.goToVisualInspection)
│   └── e2e/
│       └── visual_inspection.cy.js # Testes dos cenários de aprovação, retake e validações
```

## Como Executar

### 1. Pré-requisitos
- Node.js (v18+) e npm instalados.
- Servidor web do QloApps ativo (Apache/PHP na porta `8080`).

### 2. Configurar Variáveis de Ambiente (.env)
Copie o modelo de variáveis e ajuste com suas credenciais:
```bash
cp .env.example .env
```

Conteúdo do `.env`:
```ini
CYPRESS_BASE_URL=http://localhost:8080
CYPRESS_ADMIN_BASE_URL=admin646rrpdpy
CYPRESS_ADMIN_EMAIL=seu_email@dominio.com
CYPRESS_ADMIN_PASSWORD=sua_senha
CYPRESS_BROWSER=chrome  # ou electron
```

### 3. Execução Automatizada (Recomendada)
O script [`run_e2e.sh`](run_e2e.sh) carrega o `.env` automaticamente, checa a integridade dos serviços (iniciando o microserviço Python na porta 8102 se necessário) e roda os testes:

```bash
# Modo headless padrão (Chrome ou navegador do .env)
./run_e2e.sh

# Modo headless forçando Electron
./run_e2e.sh --electron

# Modo interativo (UI do Cypress)
./run_e2e.sh --open
```

### 4. Limpeza Pós-Teste
Para limpar as fotos temporárias e registros da tabela de auditoria:
```bash
npm run cypress:clean
```
