package ca.inspection.home.inspection.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class CentsConverterTest {

    private final CentsConverter converter = new CentsConverter();

    @Test
    void toDatabase_storesWholeCents() {
        assertThat(converter.convertToDatabaseColumn(new BigDecimal("299.99"))).isEqualTo(29999L);
        assertThat(converter.convertToDatabaseColumn(new BigDecimal("425"))).isEqualTo(42500L);
    }

    @Test
    void toDatabase_roundsFractionsOfACentHalfUp() {
        assertThat(converter.convertToDatabaseColumn(new BigDecimal("10.005"))).isEqualTo(1001L);
        assertThat(converter.convertToDatabaseColumn(new BigDecimal("10.004"))).isEqualTo(1000L);
    }

    @Test
    void toEntity_alwaysHasTwoDecimals() {
        assertThat(converter.convertToEntityAttribute(42500L)).isEqualTo(new BigDecimal("425.00"));
        assertThat(converter.convertToEntityAttribute(29999L)).isEqualTo(new BigDecimal("299.99"));
    }

    @Test
    void nulls_passThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
