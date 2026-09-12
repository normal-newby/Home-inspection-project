FROM python:3.12-slim
WORKDIR /app

RUN apt-get update && apt-get install -y \
    weasyprint \
    && rm -rf /var/lib/apt/lists/* \
    && pip install pypdf requests pymupdf --break-system-packages

COPY requirements.txt .
RUN pip install -r requirements.txt --break-system-packages

COPY . .

EXPOSE 3001

CMD ["gunicorn", "--bind", "0.0.0.0:3001", "--workers", "2", "--threads", "2", "--timeout", "600", "--graceful-timeout", "30", "--access-logfile", "-", "server:app"]
