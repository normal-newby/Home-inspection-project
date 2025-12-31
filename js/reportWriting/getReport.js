const params = new URLSearchParams(window.location.search);
const bookingId = params.get("id");

fetch(`http://localhost:8080/api/reports/${bookingId}`)
.then(result => result.json())
.then(report => {
    console.log(report);
})
.catch(error => console.log(error));