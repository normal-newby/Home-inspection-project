from flask import Flask, request, send_file
from weasyprint import HTML
import base64, io, fitz

app = Flask(__name__)

def pdf_to_svg(pdf_bytes: bytes) -> str:
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    html = ""
    first = True
    for page in doc:
        svg = page.get_svg_image()
        page_id = ' id="appendix-page"' if first else ''
        html += f'''
        <div class="appendix-page"{page_id} style="page: appendix-page">{svg}
        </div>
        '''
        first = False
    return html

@app.route('/generate-pdf', methods=['POST'])
def generate_pdf():
    try:
        data = request.json
        html = data.get('html')

        appendix_base64 = data.get('appendixBase64')
        if appendix_base64:
            appendix_pdf_bytes = io.BytesIO(base64.b64decode(appendix_base64)).read()
            appendix_html = pdf_to_svg(appendix_pdf_bytes)
            html = html.replace("</body>", f"{appendix_html}</body>")

        with open("debug.html", "w", encoding="utf-8") as f:
            f.write(html)
        
        output_pdf_bytes = HTML(string=html).write_pdf()

        return send_file(
            io.BytesIO(output_pdf_bytes),
            mimetype='application/pdf'
        )

    except Exception as e:
        return {'error': str(e)}, 500
    

if __name__ == '__main__':
    app.run(host="0.0.0.0", port=3001)