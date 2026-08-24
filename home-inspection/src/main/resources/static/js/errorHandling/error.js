const params = new URLSearchParams(window.location.search);
const status = params.get("status");
const message = params.get("message");
const path = params.get("path");

const titles = {
    "400": "Bad request",
    "401": "Not signed in",
    "403": "You don't have access to that",
    "404": "Page not found",
    "413": "That file is too large",
    "500": "Something went wrong on our end",
    "503": "Service temporarily unavailable"
};

if (status) {
    document.getElementById("status").textContent = status;
    document.getElementById("title").textContent = titles[status] || "Something went wrong";
}
if (message) {
    document.getElementById("message").textContent = message;
}
if (path) {
    const el = document.getElementById("path");
    el.textContent = path;
    el.style.display = "inline-block";
}