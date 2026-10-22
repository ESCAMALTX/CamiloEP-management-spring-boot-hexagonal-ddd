package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.domain.model.UserModel;

public interface ActivateUserPort {

  /**
   * Busca un usuario pendiente por el hash de su codigo vigente y, si existe, lo activa.
   *
   * @param codeHash hash SHA-256 del codigo introducido
   * @return el usuario activado
   * @throws com.jcaa.usersmanagement.domain.exception.InvalidActivationCodeException si no hay
   *     ningun codigo valido asociado
   */
  UserModel activateByCodeHash(String codeHash);
}
