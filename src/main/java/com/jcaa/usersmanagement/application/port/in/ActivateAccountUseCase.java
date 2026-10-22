package com.jcaa.usersmanagement.application.port.in;

import com.jcaa.usersmanagement.domain.model.UserModel;

public interface ActivateAccountUseCase {

  /**
   * Activa una cuenta pendiente usando el codigo recibido por correo.
   *
   * @return el usuario ya activo
   */
  UserModel execute(String code);
}
