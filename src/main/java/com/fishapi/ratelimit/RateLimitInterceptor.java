package com.fishapi.ratelimit;

import com.fishapi.exception.LimiteRequisicoesException;
import com.fishapi.model.Plano;
import com.fishapi.seguranca.ApiKeyInterceptor;
import com.fishapi.seguranca.ApiKeyService;
import com.fishapi.seguranca.ClienteAutenticado;
import com.fishapi.seguranca.ContextoRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Optional;

/**
 * Aplica o rate limiting antes de qualquer outra verificação.
 *
 * - Com API Key válida: balde da CHAVE, do tamanho do PLANO dela (GRATUITO, BASICO, PROFISSIONAL).
 * - Sem chave (ou com chave inválida): balde ANONIMO do IP. Atrás de proxy (Render), o IP vem do X-Forwarded-For.
 * - Depois, o balde GLOBAL da API.
 *
 * Toda resposta recebe:
 *   X-RateLimit-Limit       capacidade do balde
 *   X-RateLimit-Remaining   fichas restantes
 *   X-RateLimit-Reset       segundos até o balde encher
 *   X-RateLimit-Policy      plano e recarga (ex.: GRATUITO;capacidade=20;recarga=30/min)
 * No 429: Retry-After (segundos) e, se o cliente foi bloqueado por insistir, X-RateLimit-Blocked-Until (ISO-8601, UTC).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String ATRIBUTO_CONSUMO = "hookfishing.ratelimit.consumo";

    private final RateLimiter rateLimiter;
    private final ApiKeyService apiKeyService;

    public RateLimitInterceptor(RateLimiter rateLimiter, ApiKeyService apiKeyService) {
        this.rateLimiter = rateLimiter;
        this.apiKeyService = apiKeyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String id;
        RateLimiter.Politica politica;
        Optional<ClienteAutenticado> cliente = apiKeyService.tentarAutenticar(ApiKeyInterceptor.extrairChave(request));
        if (cliente.isPresent()) {
            Plano plano = cliente.get().plano() == null ? Plano.GRATUITO : cliente.get().plano();
            id = "chave:" + cliente.get().apiKeyId();
            politica = new RateLimiter.Politica(plano.name(), plano.getCapacidade(), plano.getRecargaPorMinuto());
            // Evita uma segunda consulta no ApiKeyInterceptor
            request.setAttribute(ContextoRequisicao.ATRIBUTO_CLIENTE, cliente.get());
        } else {
            id = "ip:" + ip(request);
            politica = rateLimiter.getPoliticaAnonima();
        }

        RateLimiter.Consumo consumo = rateLimiter.consumir(id, politica);
        escreverHeaders(response, consumo);
        request.setAttribute(ATRIBUTO_CONSUMO, consumo);

        if (!consumo.permitido()) {
            response.setHeader("Retry-After", String.valueOf(consumo.retryAfterSegundos()));
            if (consumo.bloqueado()) {
                response.setHeader("X-RateLimit-Blocked-Until", Instant.ofEpochMilli(consumo.bloqueadoAteMillis()).toString());
            }
            throw new LimiteRequisicoesException(mensagem(consumo), consumo.retryAfterSegundos());
        }

        RateLimiter.Consumo global = rateLimiter.consumirGlobal();
        if (!global.permitido()) {
            response.setHeader("Retry-After", String.valueOf(global.retryAfterSegundos()));
            throw new LimiteRequisicoesException("A API está recebendo muitas requisições de vários clientes ao mesmo tempo "
                    + "(limite global). Tente novamente em " + global.retryAfterSegundos() + " segundo(s).",
                    global.retryAfterSegundos());
        }
        return true;
    }

    private static void escreverHeaders(HttpServletResponse response, RateLimiter.Consumo consumo) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(consumo.politica().capacidade()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(consumo.restantes()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(consumo.resetSegundos()));
        response.setHeader("X-RateLimit-Policy", consumo.politica().descricao());
    }

    private String mensagem(RateLimiter.Consumo consumo) {
        RateLimiter.Politica p = consumo.politica();
        String plano = "ANONIMO".equals(p.nome()) ? "do acesso sem chave (por IP)" : "do plano " + p.nome();

        if (consumo.bloqueado()) {
            String inicio = consumo.novoBloqueio()
                    ? "Acesso bloqueado por " + consumo.retryAfterSegundos() + " segundo(s): foram "
                        + rateLimiter.getPenalidadeTentativas() + " requisições seguidas depois de estourar o limite " + plano + ". "
                    : "Acesso bloqueado temporariamente por excesso de requisições (limite " + plano + "). ";
            return inicio + "Liberação em " + consumo.retryAfterSegundos() + " segundo(s) ("
                    + Instant.ofEpochMilli(consumo.bloqueadoAteMillis()) + " UTC). Novos bloqueios em menos de "
                    + rateLimiter.getJanelaReincidenciaSegundos() / 60 + " minutos dobram o tempo, até "
                    + rateLimiter.getPenalidadeMaximaSegundos() / 60 + " minutos.";
        }

        return "Limite " + plano + " excedido: " + p.capacidade() + " requisições de uma vez e recarga de "
                + p.recargaPorMinuto() + " por minuto (" + p.ritmo() + "). Próxima requisição liberada em "
                + consumo.retryAfterSegundos() + " segundo(s). Atenção: mais " + consumo.tentativasAteBloqueio()
                + " tentativa(s) antes disso bloqueiam o acesso por " + rateLimiter.getPenalidadeSegundos() + " segundos."
                + ("ANONIMO".equals(p.nome()) ? "" : " Para limites maiores, veja os planos em GET /planos.");
    }

    /** Atrás de proxy (ex.: Render), o IP real vem no X-Forwarded-For. */
    private static String ip(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
