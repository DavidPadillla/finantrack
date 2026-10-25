package com.finantrack.finantrack.dto;

import com.finantrack.finantrack.model.TipoMovimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoriaForm {

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 80, message = "El nombre no puede superar los 80 caracteres.")
    private String nombre;

    @NotNull(message = "Selecciona el tipo de categoría.")
    private TipoMovimiento tipo;

    @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "Usa un color hexadecimal válido, por ejemplo #38B889.")
    private String color;
}
