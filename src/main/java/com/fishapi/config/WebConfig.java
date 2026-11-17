package com.fishapi.config;

import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.ratelimit.RateLimitInterceptor;
import com.fishapi.seguranca.ApiKeyInterceptor;
import com.fishapi.versionamento.ApiVersionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração web: CORS e a cadeia de interceptors da Parte 2.
 *
 * Ordem de execução em cada requisição da API:
 *   1. RateLimitInterceptor   -> 429 se o cliente excedeu o limite
 *   2. ApiKeyInterceptor      -> 401 sem chave / chave inválida, 403 sem permissão
 *   3. ApiVersionInterceptor  -> 400 se X-API-Version não for suportada
 *
 * As rotas /auth (cadastro e login) não exigem API Key, mas passam pelo rate limiting.
 * Swagger UI, /v3/api-docs, o front-end e o console do H2 ficam fora dessas regras.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    static final String[] ROTAS_API = {
            "/peixes", "/peixes/**",
            "/especies", "/especies/**",
            "/aquarios", "/aquarios/**",
            "/cuidadores", "/cuidadores/**",
            "/alimentacoes", "/alimentacoes/**",
            "/api-keys", "/api-keys/**"
    };

    /** Rotas públicas (cadastro e login): sem API Key, mas com rate limiting. */
    static final String[] ROTAS_PUBLICAS = {"/auth", "/auth/**"};

    private final RateLimitInterceptor rateLimitInterceptor;
    private final ApiKeyInterceptor apiKeyInterceptor;
    private final ApiVersionInterceptor apiVersionInterceptor;
    private final String[] origensPermitidas;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor,
                     ApiKeyInterceptor apiKeyInterceptor,
                     ApiVersionInterceptor apiVersionInterceptor,
                     @Value("${hookfishing.cors.origens-permitidas}") String[] origensPermitidas) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.apiKeyInterceptor = apiKeyInterceptor;
        this.apiVersionInterceptor = apiVersionInterceptor;
        this.origensPermitidas = origensPermitidas;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // Apenas as origens configuradas em hookfishing.cors.origens-permitidas
                .allowedOrigins(origensPermitidas)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Content-Type",
                        "Accept",
                        "Authorization",
                        ApiKeyInterceptor.HEADER,
                        ApiVersionInterceptor.HEADER,
                        IdempotenciaService.HEADER)
                // Headers que o JavaScript do front-end pode ler na resposta
                .exposedHeaders(
                        "Location",
                        "Retry-After",
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "X-RateLimit-Reset",
                        ApiVersionInterceptor.HEADER,
                        IdempotenciaService.HEADER,
                        IdempotenciaService.HEADER_REPETIDA)
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(ROTAS_API).addPathPatterns(ROTAS_PUBLICAS).order(1);
        registry.addInterceptor(apiKeyInterceptor).addPathPatterns(ROTAS_API).order(2);
        registry.addInterceptor(apiVersionInterceptor).addPathPatterns(ROTAS_API).addPathPatterns(ROTAS_PUBLICAS).order(3);
    }
}
