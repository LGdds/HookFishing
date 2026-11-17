package com.fishapi.config;

import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.ratelimit.RateLimitInterceptor;
import com.fishapi.seguranca.ApiKeyInterceptor;
import com.fishapi.versionamento.ApiVersionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Configuração web: CORS e a cadeia de interceptors da Parte 2.
 *
 * Ordem de execução em cada requisição da API:
 *   0. CorsFilter             -> responde o preflight (OPTIONS) e põe os headers de CORS em TODAS as respostas,
 *                                inclusive 401, 403 e 429 (senão o navegador nem deixa o front ler o erro)
 *   1. RateLimitInterceptor   -> 429 se o cliente estourou o limite do plano ou está bloqueado
 *   2. ApiKeyInterceptor      -> 401 sem chave / chave inválida, 403 sem permissão
 *   3. ApiVersionInterceptor  -> 400 se X-API-Version não for suportada
 *
 * Rotas públicas (sem API Key, mas com rate limiting): /auth/**, GET /planos e GET /cors.
 * O front-end, o Swagger, /v3/api-docs e o console do H2 ficam fora dessas regras.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    static final String[] ROTAS_API = {
            "/peixes", "/peixes/**",
            "/especies", "/especies/**",
            "/aquarios", "/aquarios/**",
            "/cuidadores", "/cuidadores/**",
            "/alimentacoes", "/alimentacoes/**",
            "/api-keys", "/api-keys/**",
            "/planos/**"
    };

    /** Rotas públicas: sem API Key, mas com rate limiting. */
    static final String[] ROTAS_PUBLICAS = {"/auth", "/auth/**", "/planos", "/cors"};

    private final RateLimitInterceptor rateLimitInterceptor;
    private final ApiKeyInterceptor apiKeyInterceptor;
    private final ApiVersionInterceptor apiVersionInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor,
                     ApiKeyInterceptor apiKeyInterceptor,
                     ApiVersionInterceptor apiVersionInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.apiKeyInterceptor = apiKeyInterceptor;
        this.apiVersionInterceptor = apiVersionInterceptor;
    }

    /**
     * CORS: libera SÓ as origens configuradas (variável de ambiente HOOKFISHING_CORS_ORIGINS ou
     * hookfishing.cors.origens-permitidas), SÓ os métodos usados e SÓ os headers que a API entende.
     * No Render, a URL pública do serviço entra sozinha: o Render define a variável RENDER_EXTERNAL_URL
     * (ex.: https://hookfishing-api.onrender.com), lida em hookfishing.cors.url-publica.
     *
     * Requisições da mesma origem (front-end e Swagger servidos pela API) passam direto: veja FiltroCors.
     */
    @Bean
    public FiltroCors corsFilter(CorsConfiguration configuracaoCors) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuracaoCors);
        return new FiltroCors(source);
    }

    /** Configuração de CORS, compartilhada pelo FiltroCors e pelo GET /cors (que verifica origens). */
    @Bean
    public CorsConfiguration configuracaoCors(
            @Value("${hookfishing.cors.origens-permitidas}") String origensPermitidas,
            @Value("${hookfishing.cors.url-publica:}") String urlPublica) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream((origensPermitidas + "," + urlPublica).split(","))
                .map(String::trim)
                .map(origem -> origem.endsWith("/") ? origem.substring(0, origem.length() - 1) : origem)
                .filter(origem -> !origem.isEmpty())
                .distinct()
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Content-Type",
                "Accept",
                "Authorization",
                ApiKeyInterceptor.HEADER,
                ApiVersionInterceptor.HEADER,
                IdempotenciaService.HEADER));
        // Headers de resposta que o JavaScript do front-end pode ler
        config.setExposedHeaders(List.of(
                "Location",
                "Retry-After",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "X-RateLimit-Policy",
                "X-RateLimit-Blocked-Until",
                ApiVersionInterceptor.HEADER,
                IdempotenciaService.HEADER,
                IdempotenciaService.HEADER_REPETIDA));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        return config;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(ROTAS_API).addPathPatterns(ROTAS_PUBLICAS).order(1);
        // GET /planos (tabela de preços) é público; /planos/limites e /planos/assinatura exigem a chave
        registry.addInterceptor(apiKeyInterceptor).addPathPatterns(ROTAS_API).excludePathPatterns("/planos").order(2);
        registry.addInterceptor(apiVersionInterceptor).addPathPatterns(ROTAS_API).addPathPatterns(ROTAS_PUBLICAS).order(3);
    }
}
