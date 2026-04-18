package ca.inspection.home.inspection.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceAmount {
    private BigDecimal subtotal;
    private BigDecimal hst;
    private BigDecimal gst;
    private BigDecimal total;
}
