package com.finantrack.finantrack.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PresupuestoForm {

    @NotNull(message = "Selecciona una categoría.")
    private Long categoriaId;

    @NotNull(message = "El límite es obligatorio.")
    @DecimalMin(value = "0.01", message = "El límite debe ser mayor que cero.")
    @Digits(integer = 12, fraction = 2, message = "El límite admite hasta 12 enteros y 2 decimales.")
    private BigDecimal montoLimite;

    @NotNull(message = "La fecha inicial es obligatoria.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha final es obligatoria.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaFin;
}
