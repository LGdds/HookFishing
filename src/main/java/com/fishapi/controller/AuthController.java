package com.fishapi.controller;

import com.fishapi.dto.CadastroRequest;
import com.fishapi.dto.LoginRequest;
import com.fishapi.dto.LoginResponse;
import com.fishapi.dto.UsuarioResponse;
import com.fishapi.exception.ErroResponse;
import com.fishapi.idempotencia.IdempotenciaService;
import com.fishapi.idempotencia.ResultadoIdempotente;
import com.fishapi.seguranca.AuthService;

import com.fishapi.exception.AcessoNegadoException;
import com.fishapi.exception.NaoAutenticadoException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.fishapi.config.ExemplosOpenApi.DESC_IDEMPOTENCY_KEY;
import static com.fishapi.config.ExemplosOpenApi.ERRO_400_IDEMPOTENCIA;
import static com.fishapi.config.ExemplosOpenApi.ERRO_422_IDEMPOTENCIA;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@RestController
@RequestMapping("/auth")
@SecurityRequirements // rotas públicas: não exigem X-API-Key
@Tag(name = "Autenticação", description = """
        Cadastro e login de usuários com **e-mail e senha**, usados pelo front-end. Estas rotas são públicas \
        (não exigem `X-API-Key`), mas continuam sujeitas ao rate limiting.

        **Como funciona:**
        - `POST /auth/cadastro` cria a conta com o papel `VISITANTE` e uma API Key própria do usuário.
        - `POST /auth/login` confere a senha e devolve um novo valor para essa API Key, que deve ser enviado no \
        header `X-API-Key` nas demais rotas. Cada login invalida a chave emitida no login anterior.
        - Para dar mais permissões a um usuário, um `ADMIN` altera o papel da chave dele em `PUT /api-keys/{id}`.

        **Usuários de demonstração:** `admin@hookfishing.com` / `admin123` · `gerente@hookfishing.com` / `gerente123` · \
        `biologo@hookfishing.com` / `biologo123` · `cuidador@hookfishing.com` / `cuidador123` · \
        `visitante@hookfishing.com` / `visitante123`
        """)
public class AuthController {

    private final AuthService service;
    private final IdempotenciaService idempotencia;

    public AuthController(AuthService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    @Operation(summary = "Criar conta (idempotente)",
            description = """
                    Cria um usuário com o papel `VISITANTE`. A senha é salva apenas como hash PBKDF2.

                    **Idempotência:** o header `X-Idempotency-Key` é obrigatório; repetir a mesma requisição \
                    não cria uma segunda conta. Depois do cadastro, faça login em `POST /auth/login`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = UsuarioResponse.class),
                            examples = @ExampleObject(name = "Conta criada", value = """
                                    {
                                      "id": 6,
                                      "nome": "Fernanda Rocha",
                                      "email": "fernanda.rocha@email.com",
                                      "papel": "VISITANTE",
                                      "apiKeyId": 11,
                                      "criadoEm": "2026-11-12T22:30:00Z",
                                      "_links": { "login": { "href": "http://localhost:8080/auth/login" } }
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou header X-Idempotency-Key ausente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = {
                                    @ExampleObject(name = "Senha curta", value = """
                                            {
                                              "timestamp": "2026-11-12T22:30:00Z",
                                              "status": 400,
                                              "erro": "Bad Request",
                                              "mensagem": "Um ou mais campos são inválidos. Corrija os campos listados em 'campos' e envie novamente.",
                                              "caminho": "/auth/cadastro",
                                              "campos": [ { "campo": "senha", "mensagem": "A senha deve ter entre 6 e 72 caracteres" } ]
                                            }"""),
                                    @ExampleObject(name = "Sem X-Idempotency-Key", value = ERRO_400_IDEMPOTENCIA)
                            })),
            @ApiResponse(responseCode = "409", description = "Já existe uma conta com esse e-mail",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "E-mail já cadastrado", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 409,
                                      "erro": "Conflict",
                                      "mensagem": "Já existe uma conta com o e-mail 'cuidador@hookfishing.com'. Faça login.",
                                      "caminho": "/auth/cadastro",
                                      "campos": []
                                    }"""))),
            @ApiResponse(responseCode = "422", description = "Chave de idempotência já usada com outro corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Chave reutilizada", value = ERRO_422_IDEMPOTENCIA)))
    })
    @PostMapping("/cadastro")
    public ResponseEntity<EntityModel<UsuarioResponse>> cadastrar(
            @Parameter(description = DESC_IDEMPOTENCY_KEY, example = "7c1e9a24-cadastro-fernanda", required = true)
            @RequestHeader(IdempotenciaService.HEADER) String chaveIdempotencia,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dados da nova conta", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CadastroRequest.class),
                            examples = @ExampleObject(name = "Fernanda Rocha", value = """
                                    { "nome": "Fernanda Rocha", "email": "fernanda.rocha@email.com", "senha": "aquario2025" }""")))
            @Valid @RequestBody CadastroRequest request) {

        ResultadoIdempotente<UsuarioResponse> resultado = idempotencia.executarPublico(
                chaveIdempotencia, "POST /auth/cadastro", request,
                () -> service.cadastrar(request), UsuarioResponse::id, service::buscarPorId);

        EntityModel<UsuarioResponse> modelo = EntityModel.of(resultado.resposta(),
                linkTo(AuthController.class).slash("login").withRel("login"));

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IdempotenciaService.HEADER, chaveIdempotencia)
                .header(IdempotenciaService.HEADER_REPETIDA, String.valueOf(resultado.repetida()))
                .body(modelo);
    }

    @Operation(summary = "Entrar com e-mail e senha",
            description = """
                    Confere as credenciais e devolve a **API Key** do usuário, que o cliente deve enviar no header \
                    `X-API-Key`. A resposta também traz o papel e as permissões do usuário.

                    Cada login devolve **a mesma chave** do usuário, então entrar no Swagger não derruba a sessão \
                    aberta no front-end. Para trocar a chave, use `POST /api-keys/me/regenerar`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login realizado",
                    content = @Content(mediaType = "application/hal+json", schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(name = "Login do cuidador", value = """
                                    {
                                      "apiKey": "hf_bHVjYXMgY3VpZGEgZG9zIHBlaXhlcw",
                                      "header": "X-API-Key",
                                      "usuario": {
                                        "id": 4,
                                        "nome": "Lucas Pereira",
                                        "email": "cuidador@hookfishing.com",
                                        "papel": "CUIDADOR",
                                        "apiKeyId": 9,
                                        "criadoEm": "2026-11-12T22:00:00Z"
                                      },
                                      "permissoes": {
                                        "peixes": ["CONSULTAR", "CADASTRAR", "EDITAR"],
                                        "alimentacoes": ["CONSULTAR", "CADASTRAR", "EDITAR"],
                                        "especies": ["CONSULTAR"],
                                        "aquarios": ["CONSULTAR"],
                                        "cuidadores": ["CONSULTAR"],
                                        "api-keys": []
                                      },
                                      "_links": { "me": { "href": "http://localhost:8080/api-keys/me" } }
                                    }"""))),
            @ApiResponse(responseCode = "400", description = "E-mail ou senha não informados",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Credenciais inválidas", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 401,
                                      "erro": "Unauthorized",
                                      "mensagem": "E-mail ou senha inválidos.",
                                      "caminho": "/auth/login",
                                      "campos": []
                                    }"""))),
            @ApiResponse(responseCode = "403", description = "Usuário com acesso desativado por um administrador",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(name = "Acesso desativado", value = """
                                    {
                                      "timestamp": "2026-11-12T22:30:00Z",
                                      "status": 403,
                                      "erro": "Forbidden",
                                      "mensagem": "O acesso deste usuário foi desativado por um administrador.",
                                      "caminho": "/auth/login",
                                      "campos": []
                                    }""")))
    })
    /**
     * Endpoint de token no formato OAuth2 (fluxo "password"), usado pelo botão Authorize do Swagger.
     * Recebe e-mail e senha como formulário e devolve a API Key do usuário como access_token;
     * o Swagger passa a enviá-la no header Authorization: Bearer.
     */
    @Hidden
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password) {

        if (!"password".equals(grantType)) {
            return erroOAuth(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "Use grant_type=password.");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return erroOAuth(HttpStatus.BAD_REQUEST, "invalid_request", "Informe o e-mail e a senha.");
        }
        try {
            LoginResponse login = service.entrar(new LoginRequest(username, password));
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("access_token", login.apiKey());
            corpo.put("token_type", "Bearer");
            corpo.put("papel", login.usuario().papel());
            corpo.put("usuario", login.usuario().nome());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corpo);
        } catch (NaoAutenticadoException e) {
            return erroOAuth(HttpStatus.BAD_REQUEST, "invalid_grant", e.getMessage());
        } catch (AcessoNegadoException e) {
            return erroOAuth(HttpStatus.BAD_REQUEST, "invalid_grant", e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> erroOAuth(HttpStatus status, String erro, String descricao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("error", erro);
        corpo.put("error_description", descricao);
        return ResponseEntity.status(status).body(corpo);
    }

    @PostMapping("/login")
    public ResponseEntity<EntityModel<LoginResponse>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Credenciais", required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = {
                                    @ExampleObject(name = "Cuidador", value = """
                                            { "email": "cuidador@hookfishing.com", "senha": "cuidador123" }"""),
                                    @ExampleObject(name = "Administrador", value = """
                                            { "email": "admin@hookfishing.com", "senha": "admin123" }"""),
                                    @ExampleObject(name = "Senha errada (gera 401)", value = """
                                            { "email": "cuidador@hookfishing.com", "senha": "senha-errada" }""")
                            }))
            @Valid @RequestBody LoginRequest request) {

        EntityModel<LoginResponse> modelo = EntityModel.of(service.entrar(request),
                linkTo(ApiKeyController.class).slash("me").withRel("me"));
        return ResponseEntity.ok(modelo);
    }
}
