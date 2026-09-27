import { notify } from "../ui/dialog.js";

const Chart = window.Chart;

const fromInput = document.getElementById("fromMonth");
const toInput = document.getElementById("toMonth");
const presetButtons = [...document.querySelectorAll("[data-preset]")];
const statusButtons = [...document.querySelectorAll("[data-status]")];
const paymentButtons = [...document.querySelectorAll("[data-payment]")];
const content = document.querySelector(".analytics-content");
const note = document.querySelector(".analytics-note");

const css = getComputedStyle(document.documentElement);
const COLORS = {
    accent: css.getPropertyValue("--accent").trim(),
    darkAccent: css.getPropertyValue("--dark-accent").trim(),
    ink: css.getPropertyValue("--ink").trim(),
    muted: css.getPropertyValue("--muted").trim(),
    border: css.getPropertyValue("--border").trim()
};

const money = new Intl.NumberFormat("en-CA", { style: "currency", currency: "CAD" });
const wholeMoney = new Intl.NumberFormat("en-CA", { style: "currency", currency: "CAD", maximumFractionDigits: 0 });
const count = new Intl.NumberFormat("en-CA");
const percent = new Intl.NumberFormat("en-CA", { style: "percent", maximumFractionDigits: 1 });

const state = { from: "", to: "", status: "ALL", payment: "ALL" };
const charts = {};
let latestRequest = 0;

// ── Months ──

function monthKey(date){
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function presetRange(preset){
    const now = new Date();
    const current = monthKey(now);
    return preset === "YTD" ? { from: `${now.getFullYear()}-01`, to: current } : { from: current, to: current };
}

function toDate(key){
    const [year, month] = key.split("-").map(Number);
    return new Date(year, month - 1, 1);
}

function monthLabel(key, withYear){
    const options = withYear ? { month: "short", year: "numeric" } : { month: "short" };
    return toDate(key).toLocaleString("en-CA", options);
}

function fullMonthLabel(key){
    return toDate(key).toLocaleString("en-CA", { month: "long", year: "numeric" });
}

function rangeLabel(from, to){
    if (from === to) return fullMonthLabel(from);
    const sameYear = from.slice(0, 4) === to.slice(0, 4);
    return `${monthLabel(from, !sameYear)} – ${monthLabel(to, true)}`;
}

// ── Filters ──

function syncControls(){
    fromInput.value = state.from;
    toInput.value = state.to;
    presetButtons.forEach(button => {
        const range = presetRange(button.dataset.preset);
        button.classList.toggle("active", range.from === state.from && range.to === state.to);
    });
    statusButtons.forEach(button => button.classList.toggle("active", button.dataset.status === state.status));
    paymentButtons.forEach(button => button.classList.toggle("active", button.dataset.payment === state.payment));
}

function setRange(from, to){
    state.from = from;
    state.to = to;
    syncControls();
    load();
}

presetButtons.forEach(button => button.addEventListener("click", () => {
    const range = presetRange(button.dataset.preset);
    setRange(range.from, range.to);
}));

// Moving one end past the other drags the other end with it, so the range is never backwards.
fromInput.addEventListener("change", () => {
    if (!fromInput.value) return syncControls();
    setRange(fromInput.value, fromInput.value > state.to ? fromInput.value : state.to);
});

toInput.addEventListener("change", () => {
    if (!toInput.value) return syncControls();
    setRange(toInput.value < state.from ? toInput.value : state.from, toInput.value);
});

statusButtons.forEach(button => button.addEventListener("click", () => {
    state.status = button.dataset.status;
    syncControls();
    load();
}));

paymentButtons.forEach(button => button.addEventListener("click", () => {
    state.payment = button.dataset.payment;
    syncControls();
    load();
}));

// ── Loading ──

async function load(){
    const request = ++latestRequest;
    content.classList.add("loading");
    const params = new URLSearchParams(state);
    try {
        const response = await fetch(`/api/analytics?${params}`);
        const body = await response.json().catch(() => null);
        if (!response.ok) throw new Error(body?.message ?? "Could not load analytics.");
        // A slower, older request must not overwrite a newer range.
        if (request === latestRequest) render(body);
    } catch (error) {
        if (request === latestRequest) notify(error.message || "Could not load analytics.", { error: true });
    } finally {
        if (request === latestRequest) content.classList.remove("loading");
    }
}

// ── Rendering ──

function render(report){
    const { totals } = report;
    setStat("revenue", money.format(totals.revenue));
    setStat("inspections", count.format(totals.inspections));
    setStat("averageFee", totals.averageFee == null ? "–" : money.format(totals.averageFee));
    setStat("tax", money.format(totals.tax));
    setStat("range", rangeLabel(report.from, report.to));

    const undated = report.undatedBookings;
    note.hidden = undated === 0;
    note.textContent = undated === 1
        ? "1 booking has no inspection date, so it isn't counted here."
        : `${undated} bookings have no inspection date, so they aren't counted here.`;

    renderCharts(report);
    renderServices(report);
    renderMonths(report);
}

function setStat(name, value){
    document.querySelector(`[data-stat="${name}"]`).textContent = value;
}

function renderCharts(report){
    const spansYears = report.from.slice(0, 4) !== report.to.slice(0, 4);
    const keys = report.months.map(month => month.month);
    const labels = keys.map(key => monthLabel(key, spansYears));

    drawBars("revenueChart", labels, keys, report.months.map(month => Number(month.revenue)), {
        format: value => money.format(value),
        tick: value => wholeMoney.format(value)
    });
    drawBars("inspectionsChart", labels, keys, report.months.map(month => month.inspections), {
        format: value => `${count.format(value)} ${value === 1 ? "inspection" : "inspections"}`,
        tick: value => count.format(value),
        wholeNumbers: true
    });
}

function drawBars(canvasId, labels, keys, values, { format, tick, wholeNumbers = false }){
    const existing = charts[canvasId];
    if (existing) {
        existing.data.labels = labels;
        existing.data.datasets[0].data = values;
        existing.$keys = keys;
        existing.update();
        return;
    }

    const chart = new Chart(document.getElementById(canvasId), {
        type: "bar",
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor: COLORS.accent,
                hoverBackgroundColor: COLORS.darkAccent,
                maxBarThickness: 24,
                // Rounded at the value end, square on the baseline.
                borderRadius: 4,
                borderSkipped: "start"
            }]
        },
        options: {
            maintainAspectRatio: false,
            animation: { duration: 250 },
            // The whole month column is the hover target, not just the painted bar.
            interaction: { mode: "index", intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: COLORS.ink,
                    padding: 10,
                    cornerRadius: 6,
                    displayColors: false,
                    titleFont: { weight: "600", size: 14 },
                    titleMarginBottom: 2,
                    bodyColor: COLORS.border,
                    callbacks: {
                        title: items => format(items[0].parsed.y),
                        label: item => fullMonthLabel(chart.$keys[item.dataIndex])
                    }
                }
            },
            scales: {
                x: {
                    grid: { display: false },
                    border: { color: COLORS.border },
                    ticks: { color: COLORS.muted, maxRotation: 0, autoSkipPadding: 8 }
                },
                y: {
                    beginAtZero: true,
                    border: { display: false },
                    grid: { color: COLORS.border, lineWidth: 1 },
                    ticks: {
                        color: COLORS.muted,
                        maxTicksLimit: 5,
                        precision: wholeNumbers ? 0 : undefined,
                        callback: value => tick(value)
                    }
                }
            }
        }
    });
    chart.$keys = keys;
    charts[canvasId] = chart;
}

function cell(text, className){
    const td = document.createElement("td");
    td.textContent = text;
    if (className) td.className = className;
    return td;
}

function renderServices(report){
    const body = document.querySelector(".services-body");
    const empty = document.querySelector(".services-empty");
    const total = report.services.reduce((sum, service) => sum + Number(service.revenue), 0);

    body.replaceChildren(...report.services.map(service => {
        const share = total > 0 ? Number(service.revenue) / total : 0;
        const row = document.createElement("tr");
        row.append(
            cell(service.type, "cell-strong"),
            cell(count.format(service.count), "cell-num"),
            cell(money.format(service.revenue), "cell-num")
        );

        const shareCell = document.createElement("td");
        const shareWrap = document.createElement("div");
        shareWrap.className = "share";
        const bar = document.createElement("div");
        bar.className = "share-bar";
        bar.style.width = `${Math.round(share * 75)}%`;
        const value = document.createElement("span");
        value.className = "share-value";
        value.textContent = percent.format(share);
        shareWrap.append(bar, value);
        shareCell.appendChild(shareWrap);
        row.appendChild(shareCell);
        return row;
    }));

    body.closest(".bookings-table-wrap").hidden = report.services.length === 0;
    empty.hidden = report.services.length > 0;
}

function renderMonths(report){
    const body = document.querySelector(".months-body");
    body.replaceChildren(...report.months.map(month => {
        const row = document.createElement("tr");
        const quiet = month.inspections === 0 ? " cell-muted" : "";
        row.append(
            cell(fullMonthLabel(month.month), month.inspections === 0 ? "cell-muted" : ""),
            cell(count.format(month.inspections), "cell-num" + quiet),
            cell(money.format(month.revenue), "cell-num" + quiet),
            cell(month.averageFee == null ? "–" : money.format(month.averageFee), "cell-num" + quiet),
            cell(money.format(month.tax), "cell-num" + quiet)
        );
        return row;
    }));

    const { totals } = report;
    const footer = document.createElement("tr");
    footer.append(
        cell("Total"),
        cell(count.format(totals.inspections), "cell-num"),
        cell(money.format(totals.revenue), "cell-num"),
        cell(totals.averageFee == null ? "–" : money.format(totals.averageFee), "cell-num"),
        cell(money.format(totals.tax), "cell-num")
    );
    document.querySelector(".months-total").replaceChildren(footer);
}

// ── Start ──

Chart.defaults.font.family = "'DM Sans', sans-serif";
Chart.defaults.font.size = 12;
Chart.defaults.color = COLORS.muted;

const initial = presetRange("YTD");
state.from = initial.from;
state.to = initial.to;
syncControls();
// Canvas text is drawn once, so wait for DM Sans before the first chart.
document.fonts.ready.then(load);
