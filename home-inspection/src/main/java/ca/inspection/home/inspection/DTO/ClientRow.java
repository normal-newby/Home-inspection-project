package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.Client;

import java.util.UUID;

public record ClientRow(UUID bookingId, String firstName, String lastName, String email, String phone) {
    public String fullName(){
        return Client.fullName(firstName, lastName);
    }
}
