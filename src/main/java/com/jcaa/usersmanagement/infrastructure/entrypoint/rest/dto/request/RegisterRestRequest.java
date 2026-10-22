package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRestRequest(
    @NotBlank(message = "name must be not blank")
        @Size(min = 3, message = "name must have at least 3 characters")
        String name,
    @NotBlank(message = "email must be not blank")
        @Email(message = "email must be a valid email address")
        String email,
    @NotBlank(message = "password must be not blank")
        @Size(min = 8, message = "password must have at least 8 characters")
        String password) {}
