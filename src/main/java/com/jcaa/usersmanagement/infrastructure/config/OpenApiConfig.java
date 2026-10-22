package com.jcaa.usersmanagement.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  static final String BEARER_SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI usersManagementOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Users Management API")
                .version("2.1.3")
                .description(
                    "Gestión de usuarios. Obtenga el token en POST /api/auth/login y púlselo en "
                        + "Authorize para probar los endpoints protegidos."))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME_NAME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
