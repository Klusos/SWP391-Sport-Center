package group5.example.sportCenter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String JWT_SCHEME = "bearerAuth";

    @Bean
    OpenAPI sportCenterOpenApi() {
        return new OpenAPI()
                .info(new Info().title("SportCenter API").version("1.0"))
                .components(new Components().addSecuritySchemes(JWT_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer jwtSecurityForProtectedEndpoints() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
                if (!isPublic(path, method.name())) {
                    operation.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
                }
            }));
        };
    }

    private boolean isPublic(String path, String method) {
        if ("GET".equals(method) && path.startsWith("/api/public/")) return true;
        if ("POST".equals(method) && "/api/auth/login".equals(path)) return true;
        return "POST".equals(method) && "/api/consultation-requests".equals(path);
    }
}
