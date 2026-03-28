const express = require('express');
const puppeteer = require('puppeteer');

const app = express();
app.use(express.json({ limit: '50mb' }));

const fs = require('fs');
const path = require('path');

app.post('/generate-pdf', async (req, res) => {
    let browser;
    try {
        browser = await puppeteer.launch({
            headless: 'new',
            args: ['--no-sandbox', '--disable-setuid-sandbox']
        });

        const page = await browser.newPage();

        await page.setContent(req.body.html, { waitUntil: 'networkidle0' });

        await page.addScriptTag({
            path: path.join(__dirname, 'node_modules/pagedjs/dist/paged.polyfill.js')
        });
        await page.waitForSelector('.pagedjs_page', { timeout: 30000 });

        await page.evaluate(() => typeof window.PagedPolyfill !== 'undefined');

        const pdf = await page.pdf({
            format: 'A4',
            printBackground: true,
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