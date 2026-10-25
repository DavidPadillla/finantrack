package com.finantrack.finantrack.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MetaAhorroForm {

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
    private String nombre;

    @Size(max = 255, message = "La descripción no puede superar los 255 caracteres.")
    private String descripcion;

    @NotNull(message = "El monto objetivo es obligatorio.")
    @DecimalMin(value = "0.01", message = "El monto objetivo debe ser mayor que cero.")
    @Digits(integer = 12, fraction = 2, message = "El monto admite hasta 12 enteros y 2 decimales.")
    private BigDecimal montoObjetivo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaObjetivo;
}
