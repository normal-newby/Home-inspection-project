package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByBooking_IdOrderByPosition(UUID bookingId);
}
