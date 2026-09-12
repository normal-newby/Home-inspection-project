package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.BookingStatus;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import ca.inspection.home.inspection.repository.InvoiceRepository;
import ca.inspection.home.inspection.service.InspectionBookingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/concurrency-test.db",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.data-source-properties.journal_mode=DELETE"
})
public class ConcurrentBookingCreationIT {

    @Autowired private InspectionBookingsService bookingsService;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InspectorProfileRepository inspectorProfileRepository;

    @BeforeEach
    void resetState() {
        reportsRepository.deleteAll();
        invoiceRepository.deleteAll();
        bookingsRepository.deleteAll();
        inspectorProfileRepository.deleteAll();

        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(0);
        inspectorProfileRepository.save(profile);
    }

    @Test
    void concurrentBookings_allSucceedAndGetDistinctInspectionNumbers() throws Exception {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startTogether = new CyclicBarrier(threads);

        List<Callable<Integer>> jobs = java.util.stream.IntStream.range(0, threads)
                .<Callable<Integer>>mapToObj(i -> () -> {
                    startTogether.await();
                    InspectionBookings booking = new InspectionBookings();
                    booking.setInspectionAddress("Racer " + i);
                    return bookingsService.createBooking(booking).getInspectionNumber();
                })
                .toList();

        List<Future<Integer>> futures = pool.invokeAll(jobs);
        pool.shutdown();

        List<Integer> assigned = new java.util.ArrayList<>();
        List<String> failures = new java.util.ArrayList<>();
        for (Future<Integer> f : futures) {
            try {
                assigned.add(f.get());
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                failures.add(root.getClass().getSimpleName() + ": " + root.getMessage());
            }
        }

        assertThat(failures)
                .as("every concurrent booking create succeeded")
                .isEmpty();
        assertThat(bookingsRepository.findAll())
                .as("every concurrent booking was persisted")
                .hasSize(threads);

        List<Integer> real = assigned.stream().filter(java.util.Objects::nonNull).toList();
        List<Integer> duplicates = real.stream()
                .collect(Collectors.groupingBy(n -> n, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(java.util.Map.Entry::getKey)
                .toList();

        assertThat(duplicates)
                .as("no two bookings share an inspection number (assigned=%s)", assigned)
                .isEmpty();
    }

    @Test
    void concurrentStatusUpdates_allSucceed() throws Exception {
        int threads = 6;
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            InspectionBookings booking = new InspectionBookings();
            booking.setInspectionAddress("Status " + i);
            ids.add(bookingsService.createBooking(booking).getId());
        }

        // findById then save — the read-then-write shape that used to fail on lock upgrade.
        // The service turns a lost write into a 400 rather than throwing, so that is checked too.
        List<String> failures = runTogether(threads, i -> {
            var response = bookingsService.updateStatus(ids.get(i), "COMPLETED");
            if (!response.getStatusCode().is2xxSuccessful()){
                throw new IllegalStateException("status update returned " + response.getStatusCode());
            }
        });

        assertThat(failures).as("every concurrent status update succeeded").isEmpty();
        assertThat(statusesOf(ids)).containsOnly(BookingStatus.COMPLETED);
    }

    @Test
    void concurrentEdits_allSucceed() throws Exception {
        int threads = 6;
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            InspectionBookings booking = new InspectionBookings();
            booking.setInspectionAddress("Edit " + i);
            ids.add(bookingsService.createBooking(booking).getId());
        }

        List<String> failures = runTogether(threads, i -> {
            InspectionBookings edit = new InspectionBookings();
            edit.setInspectionAddress("Edit " + i);
            edit.setCity("Toronto");
            var response = bookingsService.updateBooking(ids.get(i), edit);
            if (!response.getStatusCode().is2xxSuccessful()){
                throw new IllegalStateException("edit returned " + response.getStatusCode());
            }
        });

        assertThat(failures).as("every concurrent booking edit succeeded").isEmpty();
        assertThat(citiesOf(ids)).containsOnly("Toronto");
    }

    private List<BookingStatus> statusesOf(List<UUID> ids){
        return bookingsRepository.findAllById(ids).stream()
                .map(InspectionBookings::getStatus).toList();
    }

    private List<String> citiesOf(List<UUID> ids){
        return bookingsRepository.findAllById(ids).stream()
                .map(InspectionBookings::getCity).toList();
    }

    /** Fires the job on every thread at once and returns the root cause of any that blew up. */
    private static List<String> runTogether(int threads, java.util.function.IntConsumer job)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startTogether = new CyclicBarrier(threads);

        List<Callable<Void>> jobs = java.util.stream.IntStream.range(0, threads)
                .<Callable<Void>>mapToObj(i -> () -> {
                    startTogether.await();
                    job.accept(i);
                    return null;
                })
                .toList();

        List<Future<Void>> futures = pool.invokeAll(jobs);
        pool.shutdown();

        List<String> failures = new java.util.ArrayList<>();
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                failures.add(root.getClass().getSimpleName() + ": " + root.getMessage());
            }
        }
        return failures;
    }
}
