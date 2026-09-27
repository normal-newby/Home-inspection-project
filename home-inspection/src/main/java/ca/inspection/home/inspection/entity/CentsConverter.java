package ca.inspection.home.inspection.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;
import java.math.RoundingMode;

// SQLite has no decimal type, so money is stored as whole cents to keep it exact.
@Converter
public class CentsConverter implements AttributeConverter<BigDecimal, Long> {

    @Override
    public Long convertToDatabaseColumn(BigDecimal amount) {
        if (amount == null) return null;
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    @Override
    public BigDecimal convertToEntityAttribute(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }
}
