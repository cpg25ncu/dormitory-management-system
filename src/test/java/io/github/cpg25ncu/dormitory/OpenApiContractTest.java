package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/** Keeps docs/openapi.yaml and the implemented endpoints in step. */
class OpenApiContractTest extends ApiIntegrationTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    // Spring Security handles these in its filter chain, so no controller declares them.
    private static final Set<String> SECURITY_ENDPOINTS = Set.of("POST /api/login", "POST /api/logout");

    private static Set<String> documented;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void readContract() throws IOException {
        Map<String, Object> contract;
        try (InputStream input = Files.newInputStream(Path.of("docs", "openapi.yaml"))) {
            contract = new Yaml().load(input);
        }
        documented = new TreeSet<>();
        var paths = (Map<String, Map<String, Object>>) contract.get("paths");
        paths.forEach((path, operations) -> operations.keySet().stream().filter(HTTP_METHODS::contains)
                .forEach(method -> documented.add(method.toUpperCase() + " " + path)));
    }

    @Test
    void everyApiEndpointIsDocumentedAndEveryDocumentedApiEndpointExists() {
        Set<String> implemented = new TreeSet<>(SECURITY_ENDPOINTS);
        mappings.getHandlerMethods().keySet().forEach(mapping -> {
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/")) continue;
                assertThat(mapping.getMethodsCondition().getMethods()).as(path + " declares its HTTP methods").isNotEmpty();
                for (RequestMethod method : mapping.getMethodsCondition().getMethods()) implemented.add(method + " " + path);
            }
        });
        Set<String> documentedApi = new TreeSet<>(documented);
        documentedApi.removeIf(operation -> !operation.contains(" /api/"));

        Set<String> undocumented = new TreeSet<>(implemented);
        undocumented.removeAll(documentedApi);
        Set<String> missing = new TreeSet<>(documentedApi);
        missing.removeAll(implemented);
        assertThat(undocumented).as("endpoints missing from docs/openapi.yaml").isEmpty();
        assertThat(missing).as("documented endpoints with no implementation").isEmpty();
    }

    @Test
    void documentedEndpointsOutsideTheApiRespond() throws Exception {
        Set<String> others = new HashSet<>(documented);
        others.removeIf(operation -> operation.contains(" /api/"));
        assertThat(others).containsExactly("GET /actuator/health");
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
