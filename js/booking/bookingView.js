const params = new URLSearchParams(window.location.search);
const bookingId = params.get("id");

const infoDiv = document.querySelector(".info");

console.log(bookingId);

fetch(`http://localhost:8080/api/get/bookings/${bookingId}`)
.then(result => result.json())
.then(booking => {
    document.getElementById("clientName").innerText =
                    booking.clientFirstName + " " + booking.clientLastName;

    document.getElementById("email").innerText = booking.email || "—";
    document.getElementById("phone").innerText = booking.phone || "—";

    document.getElementById("address").innerText =
        booking.inspectionAddress + (booking.suite ? ", Suite " + booking.suite : "");

    document.getElementById("city").innerText = booking.city;
    document.getElementById("province").innerText = booking.province;
    document.getElementById("postalCode").innerText = booking.postalCode;

    document.getElementById("referredBy").innerText = booking.referredBy;
    document.getElementById("bookedBy").innerText = booking.bookedBy;
})
.catch(error => console.log(error));