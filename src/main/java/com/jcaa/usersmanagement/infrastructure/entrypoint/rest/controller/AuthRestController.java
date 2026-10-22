package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import com.jcaa.usersmanagement.application.port.in.ActivateAccountUseCase;
import com.jcaa.usersmanagement.application.port.in.LoginUseCase;
import com.jcaa.usersmanagement.application.port.in.RegisterUserUseCase;
import com.jcaa.usersmanagement.application.service.dto.command.LoginCommand;import com.jcaa.usersmanagement.application.service.dto.command.RegisterUserCommand;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request.ActivateAccountRestRequest;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request.LoginRestRequest;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request.RegisterRestRequest;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.LoginRestResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.RegisterRestResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserRestResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.mapper.UserRestMapper;
import com.jcaa.usersmanagement.infrastructure.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

  private static final String BEARER_TOKEN_TYPE = "Bearer";

  private final LoginUseCase loginUseCase;
  private final RegisterUserUseCase registerUserUseCase;
  private final ActivateAccountUseCase activateAccountUseCase;
  private final JwtTokenService jwtTokenService;

  @Value("${activation.code.ttl-minutes:15}")
  private long activationTtlMinutes;

  @PostMapping("/login")
  @Operation(summary = "Autenticar usuario y obtener un token JWT")
  public LoginRestResponse login(@Valid @RequestBody final LoginRestRequest request) {
    final UserModel user = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
    return new LoginRestResponse(
        jwtTokenService.generate(user), BEARER_TOKEN_TYPE, jwtTokenService.expirationSeconds());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrar una cuenta propia",
      description =
          "Crea una cuenta MEMBER en estado PENDING y envía por correo un código de activación "
              + "de un solo uso. La respuesta nunca incluye el código.")
  public RegisterRestResponse register(@Valid @RequestBody final RegisterRestRequest request) {
    final UserModel user =
        registerUserUseCase.execute(
            new RegisterUserCommand(request.name(), request.email(), request.password()));
    return new RegisterRestResponse(
        user.getId().value(), user.getEmail().value(), user.getStatus().name(), activationTtlMinutes);
  }

  @PostMapping("/activate")
  @Operation(
      summary = "Activar una cuenta con el código recibido por correo",
      description = "El código caduca y solo puede usarse una vez.")
  public UserRestResponse activate(
      @Valid @RequestBody final ActivateAccountRestRequest request) {
    final UserModel user = activateAccountUseCase.execute(request.code());
    return UserRestMapper.toResponse(user);
  }
}
