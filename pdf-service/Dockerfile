FROM python:3.12-slim
WORKDIR /app

RUN apt-get update && apt-get install -y \
    weasyprint \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install -r requirements.txt --break-system-packages

COPY . .

EXPOSE 3001
CMD ["python", "server.py"]