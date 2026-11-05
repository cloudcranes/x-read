FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    X_READ_HOST=0.0.0.0 \
    X_READ_PORT=8080 \
    X_READ_DB=/data/x-read.sqlite3
WORKDIR /app
COPY requirements.lock pyproject.toml ./
RUN pip install --no-cache-dir -r requirements.lock
COPY x_read ./x_read
COPY THIRD_PARTY_NOTICES.md ./
RUN chmod -R a+rX /app \
    && pip install --no-cache-dir --no-deps . \
    && useradd --uid 10001 --create-home reader \
    && mkdir -p /data && chown reader:reader /data
USER reader
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=3)"
CMD ["python", "-m", "x_read"]
