package com.isums.issueservice.configurations;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Issue Service API")
                        .version("v1")
                        .description("ISUMS service API documentation"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(
                        BEARER_SCHEME,
                        new SecurityScheme().name(BEARER_SCHEME).type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")
                ));
    }

    @Bean
    public OperationCustomizer acceptLanguageHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() == null) {
                operation.setParameters(new ArrayList<>());
            }

            boolean alreadyPresent = operation.getParameters().stream()
                    .anyMatch(parameter -> "Accept-Language".equalsIgnoreCase(parameter.getName()));

            if (!alreadyPresent && supportsLocalizedResponse(handlerMethod)) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name("Accept-Language")
                        .required(false)
                        .description("Preferred response language. Supported: vi, en, ja. Default fallback: vi.")
                        .schema(new StringSchema()
                                ._default("vi")
                                .addEnumItem("vi")
                                .addEnumItem("en")
                                .addEnumItem("ja"))
                        .example("en"));
            }

            return operation;
        };
    }

    private boolean supportsLocalizedResponse(HandlerMethod handlerMethod) {
        String packageName = handlerMethod.getBeanType().getPackageName();
        return packageName.startsWith("com.isums.issueservice.controllers");
    }
}

