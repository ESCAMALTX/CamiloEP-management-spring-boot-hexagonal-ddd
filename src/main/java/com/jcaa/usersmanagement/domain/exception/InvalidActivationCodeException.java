package com.jcaa.usersmanagement.domain.exception;

public final class InvalidActivationCodeException extends DomainException {

  private static final String MESSAGE_INVALID = "El código de activación no es válido.";
  private static final String MESSAGE_EXPIRED = "El código de activación ha caducado. Solicita uno nuevo.";
  private static final String MESSAGE_USED = "El código de activación ya fue utilizado.";

  private InvalidActivationCodeException(final String message) {
    super(message);
  }

  public static InvalidActivationCodeException becauseCodeDoesNotExist() {
    return new InvalidActivationCodeException(MESSAGE_INVALID);
  }

  public static InvalidActivationCodeException becauseCodeExpired() {
    return new InvalidActivationCodeException(MESSAGE_EXPIRED);
  }

  public static InvalidActivationCodeException becauseCodeAlreadyUsed() {
    return new InvalidActivationCodeException(MESSAGE_USED);
  }
}
