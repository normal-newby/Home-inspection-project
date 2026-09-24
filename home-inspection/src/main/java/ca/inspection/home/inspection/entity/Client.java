package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "client", indexes = {
        @Index(name = "idx_client_booking", columnList = "booking_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Client {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    // Order on the booking form; the first client is the one the booking is filed under.
    private Integer position;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    @JsonBackReference("clients")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private InspectionBookings booking;

    @JsonIgnore
    public String getFullName(){
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }
}
