package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.RegisterUserUseCase;
import com.jcaa.usersmanagement.application.port.out.GetUserByEmailPort;
import com.jcaa.usersmanagement.application.port.out.SaveActivationCodePort;
import com.jcaa.usersmanagement.application.port.out.SaveUserPort;
import com.jcaa.usersmanagement.application.service.dto.command.RegisterUserCommand;
import com.jcaa.usersmanagement.application.service.mapper.UserApplicationMapper;
import com.jcaa.usersmanagement.domain.exception.UserAlreadyExistsException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Registro publico: siempre crea cuentas MEMBER en estado PENDING y envia un codigo de activacion
 * de un solo uso al correo indicado. Ningun dato de esta operacion permite activar la cuenta.
 */
@Service
@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserUseCase {

  private final SaveUserPort saveUserPort;
  private final GetUserByEmailPort getUserByEmailPort;
  private final SaveActivationCodePort saveActivationCodePort;
  private final EmailNotificationService emailNotificationService;
  private final ActivationService activationService;
  private final Validator validator;

  @Value("${activation.code.ttl-minutes:15}")
  private long ttlMinutes;

  @Override
  public UserModel execute(final RegisterUserCommand command) {
    validate(command);

    final UserEmail email = new UserEmail(command.email());
    getUserByEmailPort
        .getByEmail(email)
        .ifPresent(
            ignored -> {
              throw UserAlreadyExistsException.becauseEmailAlreadyExists(email.value());
            });

    final UserModel userToSave =
        UserApplicationMapper.fromRegisterCommandToModel(UUID.randomUUID().toString(), command);
    final UserModel savedUser = saveUserPort.save(userToSave);

    final String code = activationService.generateCode();
    saveActivationCodePort.saveCode(savedUser.getId().value(), ActivationService.hash(code), ttlMinutes);
    emailNotificationService.notifyActivationCode(savedUser, code, ttlMinutes);

    return savedUser;
  }

  private void validate(final RegisterUserCommand command) {
    final Set<ConstraintViolation<RegisterUserCommand>> violations = validator.validate(command);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
