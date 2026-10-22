package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Envio de correo con la API HTTP de Brevo.
 *
 * <p>Se usa cuando el entorno no permite conexiones SMTP salientes (por ejemplo, el plan gratuito
 * de Render). Habla HTTPS sobre el puerto 443 y se activa con el perfil {@code brevo}.
 */
@Slf4j
@Component
@Profile("brevo")
@EnableConfigurationProperties(BrevoProperties.class)
public class BrevoEmailSenderAdapter implements EmailSenderPort {

  private static final String HEADER_API_KEY = "api-key";
  private static final String HEADER_CONTENT_TYPE = "Content-Type";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String LOG_SENT = "[BrevoEmailSenderAdapter] correo enviado exitosamente.";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final int HTTP_OK_MIN = 200;
  private static final int HTTP_OK_MAX = 299;

  private final BrevoProperties properties;
  private final HttpClient httpClient;
  private final JsonBodyEncoder jsonEncoder;

  @Autowired
  public BrevoEmailSenderAdapter(final BrevoProperties properties) {
    this(properties, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  BrevoEmailSenderAdapter(final BrevoProperties properties, final HttpClient httpClient) {
    this.properties = properties;
    this.httpClient = httpClient;
    this.jsonEncoder = new JsonBodyEncoder();
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    ensureApiKeyIsConfigured();
    final HttpRequest request = buildRequest(destination);
    try {
      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      ensureResponseIsSuccessful(destination, response);
      log.info(LOG_SENT);
    } catch (final IOException ioException) {
      throw EmailSenderException.becauseSmtpFailed(
          destination.getDestinationEmail(), ioException.getMessage());
    } catch (final InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw EmailSenderException.becauseSmtpFailed(
          destination.getDestinationEmail(), interruptedException.getMessage());
    }
  }

  private void ensureApiKeyIsConfigured() {
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw EmailSenderException.becauseSmtpFailed("(sin destinatario)", "BREVO_API_KEY no definida");
    }
  }

  private HttpRequest buildRequest(final EmailDestinationModel destination) {
    final String body =
        jsonEncoder.encode(
            Map.of(
                "sender",
                Map.of("email", properties.fromAddress(), "name", properties.resolvedFromName()),
                "to",
                java.util.List.of(
                    Map.of("email", destination.getDestinationEmail(),
                        "name", destination.getDestinationName())),
                "subject", destination.getSubject(),
                "htmlContent", destination.getBody()));
    return HttpRequest.newBuilder(URI.create(properties.resolvedApiUrl()))
        .timeout(REQUEST_TIMEOUT)
        .header(HEADER_API_KEY, properties.apiKey())
        .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private void ensureResponseIsSuccessful(
      final EmailDestinationModel destination, final HttpResponse<String> response) {
    final int status = response.statusCode();
    if (status < HTTP_OK_MIN || status > HTTP_OK_MAX) {
      throw EmailSenderException.becauseSmtpFailed(
          destination.getDestinationEmail(), "Brevo HTTP " + status + ": " + response.body());
    }
  }
}
