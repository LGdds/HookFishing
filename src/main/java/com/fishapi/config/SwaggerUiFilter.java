package com.fishapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;

/**
 * Entrega o Swagger personalizado da HookFishing API no endereço padrão do Swagger UI
 * (/swagger-ui/index.html, para onde /swagger-ui.html redireciona).
 *
 * A página (src/main/resources/swagger/index.html) usa os arquivos do Swagger UI servidos pelo
 * springdoc e acrescenta: tema laranja/azul, seletor entre as versões v1 e v2 e o login com
 * e-mail e senha no botão Authorize.
 *
 * É um filtro (e não um controller) para não disputar o mapeamento com o próprio springdoc.
 */
@Component
public class SwaggerUiFilter extends OncePerRequestFilter {

    private static final Resource PAGINA = new ClassPathResource("swagger/index.html");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return true;
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return !("/swagger-ui/index.html".equals(caminho) || "/swagger-ui/".equals(caminho));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        try (InputStream conteudo = PAGINA.getInputStream()) {
            StreamUtils.copy(conteudo, response.getOutputStream());
        }
    }
}
