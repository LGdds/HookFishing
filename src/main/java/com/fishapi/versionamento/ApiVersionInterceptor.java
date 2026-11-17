package com.fishapi.versionamento;

import com.fishapi.exception.ParametroInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Versionamento via header personalizado X-API-Version.
 *
 * - Sem header            -> versão 1 (padrão)
 * - X-API-Version: 1      -> versão 1
 * - X-API-Version: 2      -> versão 2
 * - Qualquer outro valor  -> HTTP 400
 *
 * A versão usada é devolvida no header de resposta X-API-Version.
 * Os endpoints GET /peixes e GET /peixes/{id} respondem de forma diferente em cada versão.
 */
@Component
public class ApiVersionInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-API-Version";
    public static final String VERSAO_PADRAO = "1";
    public static final Set<String> VERSOES_SUPORTADAS = Set.of("1", "2");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String versao = resolver(request.getHeader(HEADER));
        response.setHeader(HEADER, versao);
        return true;
    }

    /** Normaliza o valor do header (aceita "2" ou "v2") e valida. */
    public static String resolver(String valor) {
        if (valor == null || valor.isBlank()) {
            return VERSAO_PADRAO;
        }
        String versao = valor.trim().toLowerCase();
        if (versao.startsWith("v")) {
            versao = versao.substring(1);
        }
        if (!VERSOES_SUPORTADAS.contains(versao)) {
            throw new ParametroInvalidoException("Versão da API não suportada: '" + valor.trim()
                    + "'. Use o header X-API-Version com o valor 1 ou 2 (sem o header, a versão 1 é usada).");
        }
        return versao;
    }
}
