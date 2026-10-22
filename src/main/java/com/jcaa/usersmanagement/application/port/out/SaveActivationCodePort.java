package com.jcaa.usersmanagement.application.port.out;

public interface SaveActivationCodePort {
  /**
   * Guarda el hash del codigo de activacion para el usuario indicado.
   *
   * @param userId identificador del usuario
   * @param codeHash hash SHA-256 del codigo en claro
   * @param ttlMinutes vigencia en minutos
   */
  void saveCode(String userId, String codeHash, long ttlMinutes);
}
