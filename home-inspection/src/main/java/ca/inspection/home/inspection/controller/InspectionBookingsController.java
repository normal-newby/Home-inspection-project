package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.service.InspectionBookingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class InspectionBookingsController {
    @Autowired
    private InspectionBookingsService inspectionBookingsService;

    @PostMapping("/bookings")
    public InspectionBookings createBooking(@RequestBody InspectionBookings booking){
        return inspectionBookingsService.createBooking(booking);
    }

    @GetMapping("/get/bookings")
    public List<InspectionBookings> getAllBookings(){
        return inspectionBookingsService.findAll();
    }

    @GetMapping("/bookings/{id}")
    public InspectionBookings getBooking(@PathVariable UUID id){
        return inspectionBookingsService.findById(id);
    }

    @PutMapping("/bookings/{id}")
    public ResponseEntity<?> updateBooking(@PathVariable UUID id, @RequestBody InspectionBookings bookings){
        return inspectionBookingsService.updateBooking(id, bookings);
    }
}
