package com.merine.rebuild.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "merine.security.public-api-docs=true")
class PublicApiDocsRegressionTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicDocsIncludeTheRedirectTargetConfigurationAndAssets() throws Exception {
        var redirect = mockMvc.perform(get("/api/docs")).andReturn().getResponse();
        assertThat(redirect.getStatus()).isEqualTo(302);
        for (String path : new String[] {redirect.getRedirectedUrl(), "/api/openapi/swagger-config",
                "/api/openapi", "/api/swagger-ui/swagger-ui.css", "/api/swagger-ui/swagger-ui-bundle.js"}) {
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus())
                    .as("匿名文档所需路径 %s", path).isEqualTo(200);
        }
        assertThat(mockMvc.perform(get("/api/system/users")).andReturn().getResponse().getStatus())
                .as("开放文档不能顺带开放业务接口").isEqualTo(401);
    }
}
