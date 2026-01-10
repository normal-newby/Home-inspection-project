const bookingsContainer = document.querySelector(".bookings-container");
function loadBookings(){
    fetch("http://localhost:8080/api/get/bookings")
    .then(response => response.json())
    .then(bookings => {
        bookingsContainer.innerHTML = "";

        bookings.forEach(booking => {
            const bookingElement = document.createElement("div");
            bookingElement.classList.add("booking");
            bookingElement.id = booking.id;

            bookingElement.innerHTML = `
                <div class="booking-left">
                    <div class="booking-name">
                        ${booking.clientFirstName} ${booking.clientLastName}
                    </div>
                    <div class="booking-address">
                        ${booking.inspectionAddress}
                    </div>
                    <div class="booking-postal">
                        ${booking.postalCode}
                    </div>
                </div>

                <div class="booking-right">
                    <a href="report_writing.html?id=${booking.id}&place=roofing&type=description" class="link">
                        Write Report →
                    </a>
                    <a href="booking-details.html?id=${booking.id}" class="link">
                        View Details →
                    </a>
                </div>
            `;

            bookingsContainer.appendChild(bookingElement);
        });
    })
    .catch(error => console.log(error));
}

window.onload = () => loadBookings();