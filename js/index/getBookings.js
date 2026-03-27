const bookingsContainer = document.querySelector(".bookings-container");
function loadBookings(){
    fetch("http://localhost:8080/api/get/bookings")
    .then(response => response.json())
    .then(bookings => {
        bookingsContainer.innerHTML = "";

        console.log(bookings);

        bookings.forEach(booking => {
            const bookingElement = document.createElement("div");
            bookingElement.classList.add("booking");
            bookingElement.id = booking.id;

            bookingElement.innerHTML = `
                <div class="booking-card">
                    <div class="booking-left">
                        <div class="booking-name">${booking.clientFirstName} ${booking.clientLastName}</div>
                        <div class="booking-meta">
                            <span class="booking-address">${booking.inspectionAddress}</span>
                            <span class="booking-dot">·</span>
                            <span class="booking-postal">${booking.postalCode}</span>
                        </div>
                        <div class="booking-date">${booking.day} ${booking.month}, ${booking.year}</div>
                    </div>
                    <div class="booking-right">
                        <a href="report_writing.html?id=${booking.id}&place=roofing&type=description" class="booking-link primary">
                            Write Report →
                        </a>
                        <a href="booking-details.html?id=${booking.id}" class="booking-link secondary">
                            View Details →
                        </a>
                    </div>
                </div>
            `;

            bookingsContainer.appendChild(bookingElement);
        });
    })
    .catch(error => console.log(error));
}

window.onload = () => loadBookings();