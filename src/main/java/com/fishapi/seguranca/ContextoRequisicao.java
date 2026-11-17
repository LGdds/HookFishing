package com.fishapi.seguranca;

import com.fishapi.exception.NaoAutenticadoException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Acesso ao cliente autenticado (guardado como atributo da requisição pelo ApiKeyInterceptor). */
public final class ContextoRequisicao {

    public static final String ATRIBUTO_CLIENTE = "hookfishing.cliente";

    private ContextoRequisicao() {
    }

    public static ClienteAutenticado clienteAtual() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        Object cliente = atributos == null ? null
                : atributos.getAttribute(ATRIBUTO_CLIENTE, RequestAttributes.SCOPE_REQUEST);
        if (cliente instanceof ClienteAutenticado autenticado) {
            return autenticado;
        }
        throw new NaoAutenticadoException("Requisição sem API Key autenticada. Informe o header X-API-Key.");
    }
}
