from flask import Flask, request, send_file
from weasyprint import HTML
from pypdf import PdfReader, PdfWriter
import requests
import io
import os

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")

app = Flask(__name__)

@app.route('/generate-pdf', methods=['POST'])
def generate_pdf():
    try:
        data = request.json
        html = data.get('html')
        bookingId = data.get("bookingId")

        pdf_bytes = HTML(string=html).write_pdf()

        appendix_url = f"{SPRING_BASE_URL}/api/reports/{bookingId}/appendix-pdf"
        appendix_response = requests.get(appendix_url)
        appendix_response.raise_for_status()
        appendix_pdf_bytes = appendix_response.content

        writer = PdfWriter()

        for reader in [PdfReader(io.BytesIO(pdf_bytes)), PdfReader(io.BytesIO(appendix_pdf_bytes))]:
            for page in reader.pages:
                writer.add_page(page)
        
        output_pdf_bytes = io.BytesIO()
        writer.write(output_pdf_bytes)
        output_pdf_bytes.seek(0)
        
        return send_file(
            output_pdf_bytes,
            mimetype='application/pdf'
        )
    
    except Exception as e:
        return {'error': str(e)}, 500
    

if __name__ == '__main__':
    app.run(host="0.0.0.0", port=3001)