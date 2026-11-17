package com.fishapi.controller;

import com.fishapi.dto.AssinaturaRequest;
import com.fishapi.dto.AssinaturaResponse;
import com.fishapi.dto.LimitesResponse;
import com.fishapi.dto.PlanoResponse;
import com.fishapi.exception.ErroResponse;
import com.fishapi.model.Plano;
import com.fishapi.ratelimit.RateLimitInterceptor;
import com.fishapi.ratelimit.RateLimiter;
import com.fishapi.seguranca.ApiKeyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/planos")
@Tag(name = "Planos e limites", description = """
        Modelo de monetização da API (**freemium**): todo cadastro começa no plano `GRATUITO` e pode migrar para \
        um plano pago com limites maiores de requisições.

        | Plano | Preço | De uma vez | Recarga |
        |---|---|---|---|
        | `GRATUITO` | Grátis | 20 | 30 por minuto |
        | `BASICO` | R$ 29,90/mês | 60 | 120 por minuto |
        | `PROFISSIONAL` | R$ 99,90/mês | 200 | 600 por minuto |

        **Exemplos do que dá para fazer aqui:**
        - `GET /planos`: tabela de preços (rota pública)
        - `GET /planos/limites`: quanto do limite a sua chave já consumiu e as regras de bloqueio
        - `PUT /planos/assinatura`: troca o plano da sua chave (simulação, sem cobrança real)
        """)
public class PlanoController {

    private final ApiKeyService apiKeyService;
    private final RateLimiter rateLimiter;

    public PlanoController(ApiKeyService apiKeyService, RateLimiter rateLimiter) {
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
    }

    @Operation(summary = "Listar planos e preços",
            description = "Tabela de preços da API. Rota pública: não exige `X-API-Key`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planos disponíveis",
                    content = @Content(mediaType = "application/hal+json", examples = @ExampleObject(name = "Planos", value = """
                            {
                              "_embedded": {
                                "planos": [
                                  { "codigo": "GRATUITO", "nome": "Gratuito", "precoMensal": 0.00, "preco": "Grátis", "capacidade": 20,
                                    "recargaPorMinuto": 30, "ritmo": "1 a cada 2 s", "descricao": "Para estudar e testar a API. Uso leve, sem custo." },
                                  { "codigo": "BASICO", "nome": "Básico", "precoMensal": 29.90, "preco": "R$ 29,90/mês", "capacidade": 60,
                                    "recargaPorMinuto": 120, "ritmo": "2 por segundo", "descricao": "Para aquários pequenos e aplicações com poucos usuários." },
                                  { "codigo": "PROFISSIONAL", "nome": "Profissional", "precoMensal": 99.90, "preco": "R$ 99,90/mês", "capacidade": 200,
                                    "recargaPorMinuto": 600, "ritmo": "10 por segundo", "descricao": "Para redes de aquários e integrações com alto volume de requisições." }
                                ]
                              },
                              "_links": { "self": { "href": "http://localhost:8080/planos" }, "limites": { "href": "http://localhost:8080/planos/limites" } }
                            }""")))
    })
    @GetMapping
    public ResponseEntity<CollectionModel<PlanoResponse>> listar() {
        List<PlanoResponse> planos = Arrays.stream(Plano.values()).map(PlanoResponse::de).toList();
        return ResponseEntity.ok(CollectionModel.of(planos,
                linkTo(PlanoController.class).withSelfRel(),
                linkTo(PlanoController.class).slash("limites").withRel("limites"),
                linkTo(PlanoController.class).slash("assinatura").withRel("assinatura")));
    }

    @Operation(summary = "Consultar o meu consumo de requisições",
            description = """
                    Mostra o plano da chave, quantas fichas restam no balde, em quanto tempo ele enche e as regras de \
                    bloqueio por insistência. Esta consulta também consome 1 ficha.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consumo atual da chave")
    })
    @GetMapping("/limites")
    public ResponseEntity<EntityModel<LimitesResponse>> limites(HttpServletRequest request) {
        RateLimiter.Consumo consumo = (RateLimiter.Consumo) request.getAttribute(RateLimitInterceptor.ATRIBUTO_CONSUMO);
        RateLimiter.Politica p = consumo.politica();
        String regras = "Plano " + p.nome() + ": " + p.capacidade() + " requisições de uma vez, recarga de "
                + p.recargaPorMinuto() + " por minuto (" + p.ritmo() + "). Ao estourar, a API responde 429 com Retry-After. "
                + rateLimiter.getPenalidadeTentativas() + " tentativas seguidas com o limite estourado bloqueiam a chave por "
                + rateLimiter.getPenalidadeSegundos() + " s; bloqueios repetidos em até "
                + rateLimiter.getJanelaReincidenciaSegundos() / 60 + " min dobram o tempo, até "
                + rateLimiter.getPenalidadeMaximaSegundos() / 60 + " min.";

        LimitesResponse corpo = new LimitesResponse(p.nome(), p.capacidade(), p.recargaPorMinuto(), p.ritmo(),
                consumo.restantes(), consumo.resetSegundos(), rateLimiter.getPenalidadeTentativas(),
                rateLimiter.getPenalidadeSegundos(), rateLimiter.getPenalidadeMaximaSegundos(),
                rateLimiter.getJanelaReincidenciaSegundos(), regras);

        return ResponseEntity.ok(EntityModel.of(corpo,
                linkTo(PlanoController.class).slash("limites").withSelfRel(),
                linkTo(PlanoController.class).withRel("planos")));
    }

    @Operation(summary = "Trocar o plano da minha chave",
            description = """
                    Simula a contratação de um plano para a API Key usada na requisição. O novo limite vale a partir \
                    da próxima requisição. Não há cobrança real: em produção, esta etapa passaria por um meio de pagamento.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano alterado"),
            @ApiResponse(responseCode = "400", description = "Plano ausente ou inexistente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    @PutMapping("/assinatura")
    public ResponseEntity<EntityModel<AssinaturaResponse>> assinar(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Plano desejado", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AssinaturaRequest.class),
                            examples = {
                                    @ExampleObject(name = "Contratar o Básico", value = "{ \"plano\": \"BASICO\" }"),
                                    @ExampleObject(name = "Voltar ao Gratuito", value = "{ \"plano\": \"GRATUITO\" }")
                            }))
            @Valid @RequestBody AssinaturaRequest request) {

        apiKeyService.alterarPlanoDaChaveAtual(request.plano());
        PlanoResponse plano = PlanoResponse.de(request.plano());
        AssinaturaResponse corpo = new AssinaturaResponse(
                "Plano alterado para " + plano.nome() + ". O novo limite já vale a partir da próxima requisição.",
                "Simulação acadêmica: nenhuma cobrança real é feita.",
                plano);
        return ResponseEntity.ok(EntityModel.of(corpo,
                linkTo(PlanoController.class).slash("limites").withRel("limites"),
                linkTo(PlanoController.class).withRel("planos")));
    }
}
