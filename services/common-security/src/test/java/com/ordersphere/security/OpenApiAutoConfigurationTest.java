package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

class OpenApiAutoConfigurationTest {

  private final OpenApiAutoConfiguration config = new OpenApiAutoConfiguration();

  @PreAuthorize("hasRole('ADMIN')")
  static class AdminController {
    public void classLevel() {}

    @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
    public void methodLevel() {}
  }

  static class OpenController {
    public void open() {}
  }

  private static String describe(Class<?> type, String method) throws Exception {
    Operation operation =
        new OpenApiAutoConfiguration()
            .ordersphereRoleDescription()
            .customize(
                new Operation(),
                new HandlerMethod(type.getDeclaredConstructor().newInstance(), method));
    return operation.getDescription();
  }

  @Test
  void documentsTheRolesFromPreAuthorizeOnTheMethodOrItsController() throws Exception {
    assertThat(describe(AdminController.class, "methodLevel"))
        .isEqualTo("Requires role: ADMIN, VENDOR.");
    assertThat(describe(AdminController.class, "classLevel")).isEqualTo("Requires role: ADMIN.");
    assertThat(describe(OpenController.class, "open")).isNull();
  }

  @Test
  void serverIsTheServicesPathThroughTheGatewayAndBearerIsRequiredByDefault() {
    OpenAPI openApi = config.ordersphereOpenApi("ordersphere-orders");

    assertThat(openApi.getServers().get(0).getUrl()).isEqualTo("/ordersphere-orders");
    assertThat(openApi.getSecurity().get(0)).containsKey(OpenApiAutoConfiguration.BEARER);
  }

  @Test
  void publicPathsDropTheBearerRequirementAndOthersDocument401And403() {
    Operation login =
        new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()));
    Operation me =
        new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()));
    OpenAPI openApi =
        new OpenAPI()
            .paths(
                new Paths()
                    .addPathItem("/auth/login", new PathItem().post(login))
                    .addPathItem("/auth/me", new PathItem().get(me)));

    config
        .ordersphereSecurityByPath(List.of("/auth/login", "/auth/.well-known/**"))
        .customise(openApi);

    assertThat(login.getSecurity()).isEmpty();
    assertThat(login.getResponses()).doesNotContainKey("401");
    assertThat(me.getSecurity()).isNull();
    assertThat(me.getResponses()).containsKeys("401", "403");
    // The schema the 401/403 responses point at is in the document they're in.
    assertThat(openApi.getComponents().getSchemas()).containsKey("ErrorResponse");
  }
}
