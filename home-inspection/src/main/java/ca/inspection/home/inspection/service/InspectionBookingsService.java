package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.BookingDetails;
import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.entity.BookingStatus;
import ca.inspection.home.inspection.entity.Client;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.repository.ClientRepository;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.InvoiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InspectionBookingsService {
    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    @Autowired
    private GoogleCalendarService googleCalendarService;

    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    @Autowired
    private InspectionImagesService inspectionImagesService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    public InspectionBookings createBooking(InspectionBookings booking){
        log.info("Creating booking for {}", HelperFunctions.fullName(booking));
        // Rejects impossible dates (Feb 30, half-filled dates) before anything is written.
        BookingSchedule.of(booking);
        booking.setInspectionNumber(inspectorProfileService.getAndUpdateNumber());
        // Set up invoices
        if (booking.getInvoices() != null){
            booking.getInvoices().forEach(invoice -> invoice.setBookings(booking));
        }
        attachClients(booking);
        InspectionBookings saved = inspectionBookingsRepository.save(booking);

        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(saved);

        InspectorProfile profile = inspectorProfileService.getProfile();
        report.setSummary(profile.getSummaryLetterBody());
        report.setEmailBody(profile.getEmailTemplate());

        report = inspectionReportsRepository.save(report);
        saved.setInspectionReport(report);

        pushToCalendar(saved);
        return saved;
    }

    private void pushToCalendar(InspectionBookings booking){
        try {
            String eventId = googleCalendarService.syncBooking(booking);
            if (!java.util.Objects.equals(eventId, booking.getGoogleEventId())) {
                booking.setGoogleEventId(eventId);
                inspectionBookingsRepository.save(booking);
            }
        } catch (Exception e){
            log.warn("Could not sync booking {} to Google Calendar: {}", booking.getId(), e.getMessage());
        }
    }

    // Sorting
    public List<BookingDetails> findAll(){
        LocalDate today = LocalDate.now();
        return inspectionBookingsRepository.findBookingDetailsByOrderByCreatedAtDesc().stream()
                .sorted(byInspectionDate(today))
                .toList();
    }

    static Comparator<BookingDetails> byInspectionDate(LocalDate today){
        return Comparator
                // 0 = upcoming, 1 = past, 2 = no date set
                .comparingInt((BookingDetails booking) -> {
                    LocalDate date = inspectionDate(booking);
                    if (date == null) return 2;
                    return date.isBefore(today) ? 1 : 0;
                })
                .thenComparing(booking -> {
                    LocalDate date = inspectionDate(booking);
                    if (date == null) return LocalDate.MAX;
                    // Past bookings count backwards, so the most recent one leads that group.
                    return date.isBefore(today) ? LocalDate.MAX.minusDays(date.toEpochDay()) : date;
                })
                .thenComparing(InspectionBookingsService::primaryLastName);
    }

    private static String primaryLastName(BookingDetails booking){
        List<Client> clients = booking.getClients();
        if (clients == null || clients.isEmpty()) return "";
        String last = clients.getFirst().getLastName();
        return last == null ? "" : last;
    }

    static LocalDate inspectionDate(BookingDetails booking){
        String rawMonth = booking.getMonth();
        Integer day = booking.getDay();
        Integer year = booking.getYear();
        if (rawMonth == null || rawMonth.isBlank() || day == null || year == null) return null;

        try {
            return LocalDate.of(year, parseMonth(rawMonth), day);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Month parseMonth(String raw){
        String value = raw.trim();
        if (value.chars().allMatch(Character::isDigit)) return Month.of(Integer.parseInt(value));

        for (Month candidate : Month.values()){
            if (candidate.getDisplayName(TextStyle.FULL, Locale.CANADA).equalsIgnoreCase(value)
                    || candidate.getDisplayName(TextStyle.SHORT, Locale.CANADA).equalsIgnoreCase(value)){
                return candidate;
            }
        }
        throw new IllegalArgumentException("\"" + raw + "\" is not a valid month.");
    }

    public ResponseEntity<?> updateStatus(UUID id, String status){
        BookingStatus parsed;
        try {
            parsed = BookingStatus.parse(status);
        } catch (IllegalArgumentException e){
            log.warn("Rejected booking status {} for {}: {}", status, id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        try {
            InspectionBookings booking = inspectionBookingsRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Booking not found: " + id));
            booking.setStatus(parsed);
            inspectionBookingsRepository.save(booking);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to set status on booking {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public InspectionBookings findById(UUID id){
        return inspectionBookingsRepository.findById(id).orElseThrow();
    }

    public BookingDetails getBookingDetails(UUID id){
        BookingDetails details = inspectionBookingsRepository.getBookingDetails(id);
        if (details == null){
            throw new java.util.NoSuchElementException("Booking not found: " + id);
        }
        return details;
    }

    public InspectionReport getReportFromBooking(UUID bookingId){
        InspectionBookings booking = inspectionBookingsRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("booking not found"));
        return booking.getInspectionReport();
    }

    public ResponseEntity<?> updateBooking(UUID id, InspectionBookings booking){
        BookingSchedule.of(booking);
        try {
            InspectionBookings existing = inspectionBookingsRepository.findById(id).orElse(null);
            if (existing == null){
                return ResponseEntity.notFound().build();
            }

            booking.setId(id);
            booking.setInspectionNumber(existing.getInspectionNumber());
            if (booking.getGoogleEventId() == null){
                booking.setGoogleEventId(existing.getGoogleEventId());
            }
            // The form has no status control, so its save would otherwise reset it.
            booking.setStatus(existing.getStatus());

            if (booking.getInvoices() == null){
                booking.setInvoices(detachedCopies(invoiceRepository.findByBookings_Id(id)));
            }
            booking.getInvoices().forEach(invoice -> invoice.setBookings(booking));

            if (booking.getClients() == null){
                booking.setClients(detachedClientCopies(clientRepository.findByBooking_IdOrderByPosition(id)));
            }
            attachClients(booking);

            inspectionBookingsRepository.save(booking);

            pushToCalendar(booking);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to update booking {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Copies, so re-attaching them to the incoming booking doesn't mutate the managed rows.
    private static List<Invoice> detachedCopies(List<Invoice> invoices){
        if (invoices == null) return new ArrayList<>();
        return invoices.stream().map(source -> {
            Invoice copy = new Invoice();
            copy.setId(source.getId());
            copy.setType(source.getType());
            copy.setFee(source.getFee());
            return copy;
        }).collect(Collectors.toList());
    }

    private static List<Client> detachedClientCopies(List<Client> clients){
        if (clients == null) return new ArrayList<>();
        return clients.stream().map(source -> {
            Client copy = new Client();
            copy.setId(source.getId());
            copy.setFirstName(source.getFirstName());
            copy.setLastName(source.getLastName());
            copy.setEmail(source.getEmail());
            copy.setPhone(source.getPhone());
            return copy;
        }).collect(Collectors.toList());
    }

    // The form's order is the stored order, so the first client stays first.
    private static void attachClients(InspectionBookings booking){
        if (booking.getClients() == null) return;
        List<Client> clients = booking.getClients();
        for (int i = 0; i < clients.size(); i++){
            clients.get(i).setBooking(booking);
            clients.get(i).setPosition(i);
        }
    }

    public ResponseEntity<?> deleteBooking(UUID id){
        try {
            InspectionBookings bookings = inspectionBookingsRepository.findById(id)
                            .orElseThrow();
            try {
                googleCalendarService.deleteEvent(bookings);
            } catch (Exception e){
                log.warn("Could not remove Google Calendar event for booking {}: {}", id, e.getMessage());
            }

            // Read while the rows are still there; the cascade takes them with the booking.
            Integer inspectionNumber = bookings.getInspectionNumber();
            List<String> fileNames = inspectionImagesRepository.findImageUrlsByBookingId(id);

            inspectionBookingsRepository.delete(bookings);
            inspectionImagesService.deleteBookingFiles(id, inspectionNumber, fileNames);
            log.info("Deleted booking {}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to delete booking {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public InvoiceAmount buildInvoiceAmount(List<Invoice> invoices, boolean removeTax){
        BigDecimal subtotal = invoices.stream()
                .map(Invoice::getFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal hst = removeTax ? BigDecimal.ZERO : subtotal.multiply(new BigDecimal("0.08"));
        BigDecimal gst = removeTax ? BigDecimal.ZERO : subtotal.multiply(new BigDecimal("0.05"));
        BigDecimal total = subtotal.add(hst).add(gst);
        return new InvoiceAmount(
                subtotal.setScale(2, RoundingMode.HALF_UP),
                hst.setScale(2, RoundingMode.HALF_UP),
                gst.setScale(2, RoundingMode.HALF_UP),
                total.setScale(2, RoundingMode.HALF_UP)
        );
    }
}
