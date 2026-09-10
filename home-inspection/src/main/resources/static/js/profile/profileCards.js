const STORAGE_KEY = "profile-open-cards";

function readOpenCards(){
    try {
        const stored = JSON.parse(localStorage.getItem(STORAGE_KEY));
        return new Set(Array.isArray(stored) ? stored : []);
    } catch (error) {
        return new Set();
    }
}

function writeOpenCards(openCards){
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([...openCards]));
    } catch (error) {
        // Storage can be off; the cards still work, they just reopen closed next visit.
    }
}

function setOpen(card, isOpen){
    const toggle = card.querySelector(".card-toggle");
    const body = card.querySelector(".card-body");

    card.classList.toggle("open", isOpen);
    toggle.setAttribute("aria-expanded", String(isOpen));
    body.hidden = !isOpen;
}

const openCards = readOpenCards();

document.querySelectorAll(".card.collapsible").forEach(card => {
    const toggle = card.querySelector(".card-toggle");
    const body = card.querySelector(".card-body");
    if (!toggle || !body) return;

    setOpen(card, openCards.has(card.dataset.card));

    toggle.addEventListener("click", () => {
        const isOpen = !card.classList.contains("open");
        setOpen(card, isOpen);

        if (isOpen) openCards.add(card.dataset.card);
        else openCards.delete(card.dataset.card);
        writeOpenCards(openCards);
    });
});
