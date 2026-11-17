package com.fishapi.ratelimit;

import com.fishapi.exception.LimiteRequisicoesException;
import com.fishapi.seguranca.ApiKeyInterceptor;
import com.fishapi.seguranca.Hashes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica o rate limiting antes de qualquer outra verificação.
 *
 * O cliente é identificado pela API Key (quando enviada) ou pelo IP.
 * Todas as respostas recebem os headers:
 *   X-RateLimit-Limit      -> capacidade do balde (token bucket)
 *   X-RateLimit-Remaining  -> fichas (requisições) que ainda restam
 *   X-RateLimit-Reset      -> segundos até o balde ficar cheio de novo
 * Quando o limite estoura: HTTP 429 + header Retry-After (tratado no GlobalExceptionHandler).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        RateLimiter.Consumo consumo = rateLimiter.consumir(identificarCliente(request));

        response.setHeader("X-RateLimit-Limit", String.valueOf(consumo.limite()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(consumo.restantes()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(consumo.resetSegundos()));

        if (!consumo.permitido()) {
            throw new LimiteRequisicoesException(consumo.retryAfterSegundos(), consumo.limite(), rateLimiter.getRecargaPorSegundo());
        }
        return true;
    }

    private String identificarCliente(HttpServletRequest request) {
        String apiKey = ApiKeyInterceptor.extrairChave(request);
        if (apiKey != null && !apiKey.isBlank()) {
            return "chave:" + Hashes.sha256(apiKey.trim()).substring(0, 32);
        }
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return "ip:" + encaminhado.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
