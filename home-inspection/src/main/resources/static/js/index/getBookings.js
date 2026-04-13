const bookingsContainer = document.querySelector(".bookings-container");

function createBooking(booking) {
    // Main element (contains everything)
    const bookingElement = document.createElement("div");
    bookingElement.classList.add("booking");
    bookingElement.id = booking.id;

    // Card for flex box
    const bookingCard = document.createElement("div");
    bookingCard.className = "booking-card";

    // Left side for meta info
    const bookingLeft = document.createElement("div");
    bookingLeft.className = "booking-left";

    const bookingName = document.createElement("div");
    bookingName.className = "booking-name";
    bookingName.textContent = `${booking.clientFirstName} ${booking.clientLastName}`;
    bookingLeft.appendChild(bookingName);

    const bookingMeta = document.createElement("div");
    bookingMeta.className = "booking-meta";

    const bookingAddress = document.createElement("span");
    bookingAddress.className = "booking-address";
    bookingAddress.textContent = booking.inspectionAddress;
    bookingMeta.appendChild(bookingAddress);

    const bookingDot = document.createElement("span");
    bookingDot.className = "booking-dot";
    bookingDot.textContent = "·";
    bookingMeta.appendChild(bookingDot);

    const bookingPostal = document.createElement("span");
    bookingPostal.className = "booking-postal";
    bookingPostal.textContent = booking.postalCode;
    bookingMeta.appendChild(bookingPostal);

    bookingLeft.appendChild(bookingMeta);

    const bookingDate = document.createElement("div");
    bookingDate.className = "booking-date";
    bookingDate.textContent = `${booking.day} ${booking.month}, ${booking.year}`;
    bookingLeft.appendChild(bookingDate);

    bookingCard.appendChild(bookingLeft);

    // Right side for actions
    const bookingRight = document.createElement("div");
    bookingRight.className = "booking-right";

    const viewDetailsLink = document.createElement("a");
    viewDetailsLink.href = `booking.html?id=${booking.id}`;
    viewDetailsLink.className = "booking-link secondary view-details";
    viewDetailsLink.textContent = "View Details →";
    viewDetailsLink.style.gridArea = "view";
    bookingRight.appendChild(viewDetailsLink);

    const writeReportLink = document.createElement("a");
    writeReportLink.href = `report_writing.html?id=${booking.id}&place=roofing&type=description`;
    writeReportLink.className = "booking-link primary";
    writeReportLink.textContent = "Write Report →";
    writeReportLink.style.gridArea = "write";
    bookingRight.appendChild(writeReportLink);

    const removeButton = document.createElement("button");
    removeButton.className = "booking-link remove-btn";
    removeButton.textContent = "Remove Booking";
    removeButton.addEventListener("click", () => deleteBooking(booking.id));
    removeButton.style.gridArea = "remove";
    bookingRight.appendChild(removeButton);

    bookingCard.appendChild(bookingRight);

    bookingElement.appendChild(bookingCard);

    bookingsContainer.appendChild(bookingElement);
}

function loadBookings(){
    fetch("http://localhost:8080/api/get/bookings")
    .then(response => response.json())
    .then(bookings => {
        bookingsContainer.innerHTML = "";

        bookings.forEach(booking => {
            createBooking(booking);
        });
    })
    .catch(error => console.log(error));
}

function deleteBooking(id) {
    if (confirm("Are you sure you want to delete this booking?")) {
        fetch(`http://localhost:8080/api/bookings/${id}`, {
            method: 'DELETE'
        })
        .then(response => {
            if (response.ok) {
                loadBookings(); // Reload the bookings
            } else {
                alert("Failed to delete booking");
            }
        })
        .catch(error => console.log(error));
    }
}

window.onload = () => loadBookings();