package com.jcaa.usersmanagement.application.port.in;

import com.jcaa.usersmanagement.application.service.dto.command.RegisterUserCommand;
import com.jcaa.usersmanagement.domain.model.UserModel;

public interface RegisterUserUseCase {

  /**
   * Registra una cuenta propia (sin sesion) y genera un codigo de activacion enviado por correo.
   *
   * @return el usuario creado, siempre en estado PENDING
   */
  UserModel execute(RegisterUserCommand command);
}
