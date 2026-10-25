package com.finantrack.finantrack.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class AporteMetaForm {

    @NotNull(message = "El monto del aporte es obligatorio.")
    @DecimalMin(value = "0.01", message = "El aporte debe ser mayor que cero.")
    @Digits(integer = 12, fraction = 2, message = "El aporte admite hasta 12 enteros y 2 decimales.")
    private BigDecimal monto;

    @Size(max = 255, message = "La descripción no puede superar los 255 caracteres.")
    private String descripcion;

    @NotNull(message = "La fecha del aporte es obligatoria.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha = LocalDate.now();
}
