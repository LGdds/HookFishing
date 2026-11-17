package com.fishapi.controller;

import com.fishapi.dto.CorsResponse;
import com.fishapi.exception.ParametroInvalidoException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Expõe a política de CORS e verifica origens com a MESMA configuração usada pelo FiltroCors.
 * Serve para testar o CORS pelo Swagger e pelo front-end, onde o navegador não deixa trocar o header Origin.
 */
@RestController
@RequestMapping("/cors")
@Tag(name = "CORS", description = """
        Política de CORS da API. O navegador só entrega a resposta ao JavaScript se a origem da página estiver \
        autorizada; chamadas feitas por servidores, curl ou Postman não passam por essa regra.

        Como o Swagger roda na mesma origem da API, ele não consegue simular outra origem. Use \
        `GET /cors?origem=...` para ver se uma origem seria aceita, ou o preflight pelo terminal:

        ```
        curl -i -X OPTIONS https://hookfishing.onrender.com/peixes \\
          -H "Origin: http://localhost:5500" \\
          -H "Access-Control-Request-Method: POST" \\
          -H "Access-Control-Request-Headers: X-API-Key, Content-Type"
        ```
        Origem permitida: `200` com `Access-Control-Allow-Origin`. Origem desconhecida: `403 Invalid CORS request`.""")
public class CorsController {

    private static final Pattern FORMATO_ORIGEM = Pattern.compile("^https?://[^/\\s?#]+$");

    private final CorsConfiguration configuracao;

    public CorsController(CorsConfiguration configuracaoCors) {
        this.configuracao = configuracaoCors;
    }

    @Operation(summary = "Consultar a política de CORS e verificar uma origem",
            description = """
                    Devolve as origens, métodos e headers autorizados. Com `origem`, informa se o navegador permitiria \
                    que uma página nesse endereço chamasse a API.

                    **Acesso:** público (não exige `X-API-Key`).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Política de CORS e resultado da verificação",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "Origem permitida", value = """
                                    {
                                      "origem": "http://localhost:5500",
                                      "permitida": true,
                                      "motivo": "A origem http://localhost:5500 está na lista de origens permitidas: o navegador recebe Access-Control-Allow-Origin e libera a resposta.",
                                      "origensPermitidas": ["http://localhost:3000", "http://localhost:5173", "http://localhost:5500", "http://127.0.0.1:5500", "http://localhost:8080", "https://hookfishing.onrender.com"],
                                      "metodosPermitidos": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
                                      "headersPermitidos": ["Content-Type", "Accept", "Authorization", "X-API-Key", "X-API-Version", "X-Idempotency-Key"],
                                      "headersExpostos": ["Location", "Retry-After", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "X-RateLimit-Policy", "X-RateLimit-Blocked-Until", "X-API-Version", "X-Idempotency-Key", "X-Idempotent-Replayed"],
                                      "cachePreflightSegundos": 3600
                                    }"""),
                            @ExampleObject(name = "Origem recusada", value = """
                                    {
                                      "origem": "https://site-externo.example",
                                      "permitida": false,
                                      "motivo": "A origem https://site-externo.example não está na lista: o preflight recebe 403 e o navegador bloqueia a chamada.",
                                      "origensPermitidas": ["http://localhost:3000", "http://localhost:5173", "http://localhost:5500", "http://127.0.0.1:5500", "http://localhost:8080", "https://hookfishing.onrender.com"],
                                      "metodosPermitidos": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
                                      "headersPermitidos": ["Content-Type", "Accept", "Authorization", "X-API-Key", "X-API-Version", "X-Idempotency-Key"],
                                      "headersExpostos": ["Location", "Retry-After", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "X-RateLimit-Policy", "X-RateLimit-Blocked-Until", "X-API-Version", "X-Idempotency-Key", "X-Idempotent-Replayed"],
                                      "cachePreflightSegundos": 3600
                                    }""")
                    })),
            @ApiResponse(responseCode = "400", description = "Origem em formato inválido (use esquema e host, ex.: http://localhost:5500)")
    })
    @GetMapping
    public ResponseEntity<CorsResponse> verificar(
            @Parameter(description = "Origem a verificar: esquema + host (+ porta), sem barra no final",
                    example = "http://localhost:5500")
            @RequestParam(required = false) String origem) {

        Boolean permitida = null;
        String motivo = "Informe o parâmetro origem (ex.: http://localhost:5500) para verificar se ela é aceita.";
        String normalizada = origem == null ? null : origem.trim();

        if (normalizada != null && !normalizada.isEmpty()) {
            if (!FORMATO_ORIGEM.matcher(normalizada).matches()) {
                throw new ParametroInvalidoException("Origem inválida: '" + normalizada
                        + "'. Use esquema e host, sem caminho e sem barra no final (ex.: http://localhost:5500).");
            }
            permitida = configuracao.checkOrigin(normalizada) != null;
            motivo = permitida
                    ? "A origem " + normalizada + " está na lista de origens permitidas: o navegador recebe "
                    + "Access-Control-Allow-Origin e libera a resposta."
                    : "A origem " + normalizada + " não está na lista: o preflight recebe 403 e o navegador bloqueia a chamada.";
        }

        return ResponseEntity.ok(new CorsResponse(
                normalizada == null || normalizada.isEmpty() ? null : normalizada,
                permitida,
                motivo,
                lista(configuracao.getAllowedOrigins()),
                lista(configuracao.getAllowedMethods()),
                lista(configuracao.getAllowedHeaders()),
                lista(configuracao.getExposedHeaders()),
                configuracao.getMaxAge()));
    }

    private static List<String> lista(List<String> valores) {
        return valores == null ? List.of() : List.copyOf(valores);
    }
}
