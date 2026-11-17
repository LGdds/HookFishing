package com.fishapi.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.net.URI;
import java.util.Locale;

/**
 * Filtro de CORS da API.
 *
 * Diferença para o CorsFilter padrão: requisições da MESMA origem (front-end e Swagger servidos pela própria API)
 * nunca são tratadas como CORS. O navegador manda o header Origin também em POST/PUT da mesma origem, e atrás de
 * um proxy HTTPS (Render) o Spring pode enxergar a requisição como http://host-interno:10000. Aí ele achava que
 * https://seu-servico.onrender.com era "outra origem" e respondia 403 "Invalid CORS request".
 * Aqui a comparação é feita pelo host que o navegador usou (X-Forwarded-Host ou Host), que não depende do proxy.
 *
 * Implementa Ordered para rodar antes dos interceptors e do rate limit (o Spring Boot lê a ordem da instância).
 */
public class FiltroCors extends CorsFilter implements Ordered {

    public FiltroCors(CorsConfigurationSource source) {
        super(source);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String origem = request.getHeader("Origin");
        return origem == null || mesmaOrigem(origem, request);
    }

    static boolean mesmaOrigem(String origem, HttpServletRequest request) {
        String hostOrigem;
        try {
            URI uri = URI.create(origem.trim());
            if (uri.getHost() == null) {
                return false;
            }
            hostOrigem = uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String encaminhado = request.getHeader("X-Forwarded-Host");
        String host = encaminhado != null && !encaminhado.isBlank()
                ? encaminhado.split(",")[0]
                : request.getHeader("Host");
        return host != null && semPortaPadrao(host).equals(semPortaPadrao(hostOrigem));
    }

    private static String semPortaPadrao(String host) {
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.endsWith(":443") || h.endsWith(":80")) {
            h = h.substring(0, h.lastIndexOf(':'));
        }
        return h;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
