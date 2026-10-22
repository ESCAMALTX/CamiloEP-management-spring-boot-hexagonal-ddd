package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ActivateAccountRestRequest(
    @NotBlank(message = "code must be not blank") String code) {}
