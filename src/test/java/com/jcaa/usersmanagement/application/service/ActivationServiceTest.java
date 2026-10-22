package com.jcaa.usersmanagement.application.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ActivationService (generacion y hash de codigos)")
class ActivationServiceTest {

  @Test
  @DisplayName("generateCode() produce 12 caracteres URL-safe y distintos en cada llamada")
  void shouldGenerateUniqueUrlSafeCodes() {
    // Arrange
    final ActivationService service = new ActivationService(null);

    // Act
    final String first = service.generateCode();
    final String second = service.generateCode();

    // Assert
    assertAll(
        "codigo generado",
        () -> assertEquals(12, first.length()),
        () -> assertTrue(first.matches("[A-Za-z0-9_-]+")),
        () -> assertNotEquals(first, second));
  }

  @Test
  @DisplayName("hash() es SHA-256 en hexadecimal, estable e insensible a espacios externos")
  void shouldHashDeterministically() {
    // Act
    final String hash = ActivationService.hash("ABCDEF123456");
    final String withSpaces = ActivationService.hash(ActivationService.normalize("  ABCDEF123456  "));

    // Assert
    assertAll(
        "hash",
        () -> assertEquals(64, hash.length()),
        () -> assertTrue(hash.matches("[0-9a-f]{64}")),
        () -> assertEquals(hash, withSpaces),
        () -> assertNotEquals(hash, ActivationService.hash("otro-codigo")));
  }

  @Test
  @DisplayName("normalize() tolera null y recorta espacios")
  void shouldNormalizeNull() {
    assertEquals("", ActivationService.normalize(null));
    assertEquals("abc", ActivationService.normalize(" abc "));
  }
}
