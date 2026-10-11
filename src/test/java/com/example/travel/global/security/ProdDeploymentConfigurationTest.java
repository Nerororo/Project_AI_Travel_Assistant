package com.example.travel.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProdDeploymentConfigurationTest {

    @Test
    void productionUsesRenderPortAndVerifiedAivenIdentityEvenWhenDbUrlIsPresent() throws IOException {
        var yaml = new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yml"));
        var sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("render", Map.of(
                "PORT", "12400",
                "DB_HOST", "mysql.example.invalid",
                "DB_PORT", "17342",
                "DB_NAME", "travel",
                "JWT_ACTIVE_KEY_ID", "current",
                "JWT_ACTIVE_KEY_SECRET", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "DB_URL", "jdbc:mysql://unsafe.example.invalid/travel?sslMode=DISABLED")));
        sources.addLast(yaml.getFirst());
        var properties = new PropertySourcesPropertyResolver(sources);

        assertThat(properties.getProperty("server.address")).isEqualTo("0.0.0.0");
        assertThat(properties.getProperty("server.port")).isEqualTo("12400");
        assertThat(properties.getProperty("server.forward-headers-strategy")).isEqualTo("none");
        assertThat(properties.getProperty("spring.web.resources.static-locations"))
                .isEqualTo("file:/tmp/routy-static/,classpath:/static/");
        assertThat(properties.getProperty("routy.jwt.keys[0].id")).isEqualTo("current");
        assertThat(properties.getProperty("routy.jwt.keys[0].secret"))
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://mysql.example.invalid:17342/travel"
                        + "?sslMode=VERIFY_IDENTITY"
                        + "&trustCertificateKeyStoreUrl=file:/tmp/aiven-truststore.p12"
                        + "&trustCertificateKeyStoreType=PKCS12"
                        + "&trustCertificateKeyStorePassword=changeit"
                        + "&fallbackToSystemTrustStore=false");
    }
}
