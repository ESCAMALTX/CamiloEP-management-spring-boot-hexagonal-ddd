package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.ActivateAccountUseCase;
import com.jcaa.usersmanagement.application.port.out.ActivateUserPort;
import com.jcaa.usersmanagement.domain.model.UserModel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class ActivationService implements ActivateAccountUseCase {

  private static final int CODE_BYTES = 9;
  private static final String HASH_ALGORITHM = "SHA-256";

  private final ActivateUserPort activateUserPort;
  private final SecureRandom secureRandom = new SecureRandom();

  public ActivationService(final ActivateUserPort activateUserPort) {
    this.activateUserPort = activateUserPort;
  }

  @Override
  public UserModel execute(final String code) {
    return activateUserPort.activateByCodeHash(hash(normalize(code)));
  }

  /** Genera un codigo legible de 12 caracteres Base64 URL-safe (sin simbolos ambiguos). */
  public String generateCode() {
    final byte[] bytes = new byte[CODE_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String normalize(final String code) {
    return code == null ? "" : code.trim();
  }

  public static String hash(final String code) {
    try {
      final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      final byte[] hashed = digest.digest(code.getBytes(StandardCharsets.UTF_8));
      final StringBuilder builder = new StringBuilder(hashed.length * 2);
      for (final byte value : hashed) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (final NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 no disponible", exception);
    }
  }
}
