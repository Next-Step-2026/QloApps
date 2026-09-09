# Dockerfile para o Reservation Policy Engine
FROM python:3.13-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8105

WORKDIR /app

# Criação de usuário não-privilegiado para segurança (Trivy / Checkov compliance)
RUN groupadd -r appgroup && useradd -r -g appgroup -u 10001 appuser

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# Ajuste de permissões
RUN chown -R appuser:appgroup /app
USER 10001:appgroup

EXPOSE 8105

HEALTHCHECK --interval=20s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8105/healthz')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8105"]
