# QloApps Visual Inspection Module (`qlovisualinspection`)

Módulo de governança e auditoria de housekeeping para validação de evidências fotográficas de quartos de hotel (QLO-FEAT-002).

## Visão Geral da Arquitetura

O fluxo de inspeção visual opera através de uma integração entre o back-office do QloApps (PHP) e o microserviço de avaliação de qualidade de imagem (Python FastAPI):

```text
[ Admin / Housekeeper ]
          │ (Upload multipart)
          ▼
[ AdminVisualInspectionController.php ] ── (cURL / RFC 7807) ──► [ inspection-service-python ]
          │                                                               │
  (Grava BD & Disco)                                            (Avalia resolução,
          ▼                                                      luminância, nitidez)
[ qlo_visual_inspection ]
```

## Limites de Upload e Validações (Defesa em Profundidade)

### Limite Máximo por Foto: **5 MB** (`5242880 bytes`)

Para garantir segurança, resiliência e desempenho, o limite de **5 MB** é validado e aplicado em duas camadas:

1. **Camada PHP (QloApps Back-Office)**
   - Local: [`AdminVisualInspectionController::MAX_FILE_SIZE_BYTES`](controllers/admin/AdminVisualInspectionController.php) (`5242880` bytes).
   - **Objetivo:** *Fail-fast*. Bloqueia uploads excessivos antes do envio cURL para o serviço local, poupando I/O e tempo de conexão.
   - Retorno: Mensagem de erro traduzida na tela solicitando retake.

2. **Camada Python (Microserviço FastAPI)**
   - Local: [`main.py: MAX_FILE_SIZE_BYTES`](../../inspection-service-python/app/main.py) (`5 * 1024 * 1024` bytes).
   - **Objetivo:** Proteção de borda da API caso o serviço seja invocado diretamente ou por outros clientes (e.g., aplicativo mobile).
   - Retorno: `HTTP 400 Bad Request` padronizado via RFC 7807 (`Problem Details`).

> **Nota de Manutenção:** Caso o tamanho máximo permitido para fotos de inspeção precise ser alterado, certifique-se de ajustar ambas as constantes (`AdminVisualInspectionController::MAX_FILE_SIZE_BYTES` no PHP e `MAX_FILE_SIZE_BYTES` no `app/main.py` do microserviço Python) para mantê-las sincronizadas.
