package com.jcaa.usersmanagement.infrastructure.adapter.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brevo")
public record BrevoProperties(String apiKey, String apiUrl, String fromAddress, String fromName) {

  private static final String DEFAULT_API_URL = "https://api.brevo.com/v3/smtp/email";
  private static final String DEFAULT_FROM_NAME = "Gestion de Usuarios";

  public String resolvedApiUrl() {
    return isBlank(apiUrl) ? DEFAULT_API_URL : apiUrl;
  }

  public String resolvedFromName() {
    return isBlank(fromName) ? DEFAULT_FROM_NAME : fromName;
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
