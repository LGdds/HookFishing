package com.fishapi.seguranca;

import com.fishapi.exception.AcessoNegadoException;
import com.fishapi.exception.NaoAutenticadoException;
import com.fishapi.seguranca.Permissoes.Operacao;
import com.fishapi.seguranca.Permissoes.Recurso;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Autenticação por API Key (header X-API-Key) e autorização pelo papel da chave.
 *
 * O papel (VISITANTE, CUIDADOR, BIOLOGO, GERENTE, ADMIN) define o que pode ser feito em cada recurso;
 * a matriz completa está em {@link Permissoes}.
 *
 * As exceções lançadas aqui são tratadas pelo GlobalExceptionHandler (401/403).
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    public ApiKeyInterceptor(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true; // preflight do CORS não envia a chave
        }

        String chave = extrairChave(request);
        if (chave == null || chave.isBlank()) {
            throw new NaoAutenticadoException("API Key não informada. Envie o header X-API-Key "
                    + "(no Swagger, clique em 'Authorize' e entre com e-mail e senha).");
        }

        ClienteAutenticado cliente = apiKeyService.autenticar(chave);
        verificarPermissao(cliente, request);
        request.setAttribute(ContextoRequisicao.ATRIBUTO_CLIENTE, cliente);
        return true;
    }

    /**
     * A chave pode vir no header X-API-Key ou, quando o login é feito pelo botão Authorize do Swagger,
     * no header Authorization: Bearer (a API Key do usuário funciona como token).
     */
    public static String extrairChave(HttpServletRequest request) {
        String chave = request.getHeader(HEADER);
        if (chave != null && !chave.isBlank()) {
            return chave.trim();
        }
        String autorizacao = request.getHeader("Authorization");
        if (autorizacao != null && autorizacao.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return autorizacao.substring(7).trim();
        }
        return null;
    }

    private void verificarPermissao(ClienteAutenticado cliente, HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        String metodo = request.getMethod();

        // Qualquer papel pode consultar a própria chave
        if (caminho.equals("/api-keys/me") && "GET".equals(metodo)) {
            return;
        }

        Optional<Recurso> recurso = Recurso.doCaminho(caminho);
        if (recurso.isEmpty()) {
            return;
        }

        Operacao operacao = Operacao.doMetodo(metodo);
        if (!Permissoes.permite(cliente.papel(), recurso.get(), operacao)) {
            throw new AcessoNegadoException("Seu papel " + cliente.papel() + " não permite "
                    + operacao.getVerbo() + " " + recurso.get().getRotulo() + ". Papéis permitidos: "
                    + Permissoes.listar(Permissoes.papeisPermitidos(recurso.get(), operacao)) + ".");
        }
    }
}
