const form = document.getElementById("booking-form");

form.addEventListener("submit", (e) => {
    e.preventDefault();
    
    const formData = new FormData(form);
    const data = Object.fromEntries(formData.entries());
    console.log(data);

    fetch("http://localhost:8080/api/bookings", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    });
});