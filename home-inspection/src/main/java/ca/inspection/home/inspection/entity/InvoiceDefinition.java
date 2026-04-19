package ca.inspection.home.inspection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_definition")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceDefinition {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "TEXT")
    private UUID id;

    private String type;
    private BigDecimal fee;
}
