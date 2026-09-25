package com.seatlock.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds an "Authorize" button to Swagger UI (http://localhost:8080/swagger-ui.html) for JWTs. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI seatLockOpenApi() {
        return new OpenAPI()
                .info(new Info().title("SeatLock API").version("0.1.0")
                        .description("Real-time event ticket booking with concurrency-safe seat holds"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
