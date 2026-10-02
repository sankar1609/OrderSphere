package com.ordersphere.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.AntPathMatcher;

/**
 * OpenAPI conventions shared by every service that adds springdoc. The spec's server is the
 * service's path through the API gateway ({@code /{spring.application.name}}), so "Try it out" in
 * the gateway's Swagger UI reaches it; every operation needs a Bearer JWT except the paths listed
 * in {@code ordersphere.openapi.public-paths}; role restrictions are read off {@code @PreAuthorize}
 * so the docs can't drift from what's enforced.
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiAutoConfiguration {

  static final String BEARER = "bearerAuth";
  private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";
  private static final Pattern ROLE = Pattern.compile("'([A-Z_]+)'");

  @Bean
  @ConditionalOnMissingBean
  public OpenAPI ordersphereOpenApi(@Value("${spring.application.name:service}") String name) {
    return new OpenAPI()
        .info(
            new Info()
                .title(name)
                .version("v1")
                .description(
                    "OrderSphere "
                        + name
                        + ". Authorize with an access token from POST"
                        + " /auth-service/auth/login (or a SERVICE token from POST"
                        + " /auth-service/auth/token)."))
        .servers(List.of(new Server().url("/" + name).description("Through the API gateway")))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }

  /** "Requires role: ..." from the handler's (or its controller's) {@code @PreAuthorize}. */
  @Bean
  public OperationCustomizer ordersphereRoleDescription() {
    return (operation, handlerMethod) -> {
      PreAuthorize rule =
          AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), PreAuthorize.class);
      if (rule == null) {
        rule =
            AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), PreAuthorize.class);
      }
      if (rule != null) {
        List<String> roles = new ArrayList<>();
        Matcher matcher = ROLE.matcher(rule.value());
        while (matcher.find()) {
          roles.add(matcher.group(1));
        }
        if (!roles.isEmpty()) {
          String requirement = "Requires role: " + String.join(", ", roles) + ".";
          operation.setDescription(
              operation.getDescription() == null
                  ? requirement
                  : operation.getDescription() + "\n\n" + requirement);
        }
      }
      return operation;
    };
  }

  /**
   * Public endpoints drop the Bearer requirement; every other operation documents the 401/403 it
   * can answer with, in the shape every service's exception handler uses.
   */
  @Bean
  public OpenApiCustomizer ordersphereSecurityByPath(
      @Value("${ordersphere.openapi.public-paths:}") List<String> publicPaths) {
    AntPathMatcher matcher = new AntPathMatcher();
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }
      // Registered here rather than on the OpenAPI bean: springdoc drops component schemas that
      // nothing references yet before customizers run, which left the 401/403 refs dangling.
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      openApi.getComponents().addSchemas("ErrorResponse", errorResponseSchema());
      openApi
          .getPaths()
          .forEach(
              (path, item) -> {
                boolean isPublic =
                    publicPaths.stream()
                        .filter(pattern -> !pattern.isBlank())
                        .anyMatch(pattern -> matcher.match(pattern.trim(), path));
                for (Operation operation : item.readOperations()) {
                  if (isPublic) {
                    operation.setSecurity(List.of()); // overrides the global Bearer requirement
                  } else {
                    addErrorResponse(operation, "401", "Missing, invalid or expired token");
                    addErrorResponse(operation, "403", "Authenticated but not allowed");
                  }
                }
              });
    };
  }

  private static void addErrorResponse(Operation operation, String status, String description) {
    if (operation.getResponses() != null && !operation.getResponses().containsKey(status)) {
      operation
          .getResponses()
          .addApiResponse(
              status,
              new ApiResponse()
                  .description(description)
                  .content(
                      new Content()
                          .addMediaType(
                              "application/json",
                              new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)))));
    }
  }

  private static Schema<?> errorResponseSchema() {
    return new ObjectSchema()
        .description("Error body returned by every service")
        .addProperty("timestamp", new StringSchema().format("date-time"))
        .addProperty("status", new IntegerSchema().example(409))
        .addProperty("error", new StringSchema().example("Conflict"))
        .addProperty(
            "message",
            new StringSchema().example("Not enough stock: SKU-1 (2 requested, 0 available)"));
  }
}
