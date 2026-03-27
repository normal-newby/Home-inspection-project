const express = require('express');
const puppeteer = require('puppeteer');

const app = express();
app.use(express.json({ limit: '50mb' }));

app.post('/generate-pdf', async (req, res) => {
    let browser;
    try {
        browser = await puppeteer.launch({
            headless: 'new',
            args: ['--no-sandbox', '--disable-setuid-sandbox']
        });

        const page = await browser.newPage();
        await page.setContent(req.body.html, { waitUntil: 'networkidle0' });

        const pdf = await page.pdf({
            format: 'A4',
            printBackground: true,
            margin: {
                top: '28mm',
                bottom: '20mm',
                left: '15mm',
                right: '15mm'
            }
        });

        res.set('Content-Type', 'application/pdf');
        res.send(pdf);

    } catch (err) {
        console.error('PDF generation failed:', err);
        res.status(500).json({ error: err.message });
    } finally {
        if (browser) await browser.close();
    }
});

app.listen(3001, () => console.log('PDF service running on port 3001'));