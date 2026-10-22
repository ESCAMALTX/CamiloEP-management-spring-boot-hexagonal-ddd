package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

@DisplayName("BrevoEmailSenderAdapter")
class BrevoEmailSenderAdapterTest {

  private static final String API_KEY = "xkeysib-test";
  private static final String FROM = "sender@example.com";
  private static final String DEST_EMAIL = "john@example.com";
  private static final String DEST_NAME = "John Doe";
  private static final String SUBJECT = "Cuenta creada";
  private static final String BODY = "<html>Hola</html>";

  private HttpClient httpClient;
  private HttpResponse<String> httpResponse;
  private BrevoEmailSenderAdapter adapter;
  private EmailDestinationModel destination;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    httpClient = mock(HttpClient.class);
    httpResponse = mock(HttpResponse.class);
    final BrevoProperties properties =
        new BrevoProperties(API_KEY, null, FROM, "Gestion de Usuarios");
    adapter = new BrevoEmailSenderAdapter(properties, httpClient);
    destination = new EmailDestinationModel(DEST_EMAIL, DEST_NAME, SUBJECT, BODY);
  }

  @Test
  @DisplayName("send() publica en la API de Brevo con la clave y el HTML del destinatario")
  void shouldPostToBrevoApi() throws Exception {
    // Arrange
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(httpResponse);

    // Act
    adapter.send(destination);

    // Assert
    final var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(requestCaptor.capture(), any());
    final HttpRequest request = requestCaptor.getValue();
    assertAll(
        "peticion a Brevo",
        () -> assertEquals("POST", request.method()),
        () ->
            assertEquals(
                "https://api.brevo.com/v3/smtp/email", request.uri().toString()),
        () -> assertEquals(API_KEY, request.headers().firstValue("api-key").orElse(null)));
  }

  @Test
  @DisplayName("send() lanza EmailSenderException cuando Brevo responde con error")
  void shouldThrowWhenBrevoRejectsTheEmail() throws Exception {
    // Arrange
    when(httpResponse.statusCode()).thenReturn(401);
    when(httpResponse.body()).thenReturn("{\"message\":\"Key not found\"}");
    when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(httpResponse);

    // Act & Assert
    final EmailSenderException exception =
        assertThrows(EmailSenderException.class, () -> adapter.send(destination));
    assertAll(
        "detalle del error",
        () -> assertTrue(exception.getMessage().contains(DEST_EMAIL)),
        () -> assertTrue(exception.getMessage().contains("401")));
  }

  @Test
  @DisplayName("send() rechaza la llamada si falta la clave de API")
  void shouldRejectWhenApiKeyIsMissing() {
    // Arrange
    final BrevoProperties properties = new BrevoProperties("", null, FROM, "App");
    final BrevoEmailSenderAdapter adapterWithoutKey =
        new BrevoEmailSenderAdapter(properties, httpClient);

    // Act & Assert
    assertThrows(EmailSenderException.class, () -> adapterWithoutKey.send(destination));
    verifyNoInteractions(httpClient);
  }
}
