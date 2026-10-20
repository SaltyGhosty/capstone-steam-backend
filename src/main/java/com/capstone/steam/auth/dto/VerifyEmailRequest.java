package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El enlace del correo más la contraseña con la que se hizo ese registro. */
public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 100) String password) {
}
