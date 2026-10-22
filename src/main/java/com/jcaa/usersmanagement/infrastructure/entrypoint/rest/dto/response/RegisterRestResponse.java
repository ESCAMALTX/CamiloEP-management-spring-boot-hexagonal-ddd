package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta al registrar una cuenta. El código de activación solo se envía por correo.")
public record RegisterRestResponse(
    @Schema(description = "Identificador del usuario creado") String id,
    @Schema(description = "Correo donde se envió el código") String email,
    @Schema(description = "Estado inicial, siempre PENDING") String status,
    @Schema(description = "Minutos de vigencia del código") long expiresInMinutes) {}
