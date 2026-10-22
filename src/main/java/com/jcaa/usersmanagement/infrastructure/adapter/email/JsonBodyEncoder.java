package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonBodyEncoder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  String encode(final Object body) {
    try {
      return OBJECT_MAPPER.writeValueAsString(body);
    } catch (final JsonProcessingException exception) {
      throw new IllegalArgumentException("No se pudo serializar el cuerpo del correo", exception);
    }
  }
}
