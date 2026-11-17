package com.fishapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tratamento global de exceções (@ControllerAdvice).
 * Toda falha da API é convertida para o mesmo formato JSON (ErroResponse)
 * com o código HTTP adequado e uma mensagem clara.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- 400 Bad Request ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErroResponse.CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(this::campo)
                .toList();
        return resposta(HttpStatus.BAD_REQUEST,
                "Um ou mais campos são inválidos. Corrija os campos listados em 'campos' e envie novamente.",
                req, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST,
                "Corpo da requisição ausente ou mal formatado. Verifique se o JSON é válido e se os tipos dos campos "
                        + "estão corretos (ex.: 'idade' deve ser número inteiro; 'tipo' aceita RACAO, VIVA, VEGETAL "
                        + "ou CONGELADA; 'papel' aceita VISITANTE, CUIDADOR, BIOLOGO, GERENTE ou ADMIN).",
                req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> tipoParametro(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST,
                "Valor '" + ex.getValue() + "' inválido para o parâmetro '" + ex.getName() + "'.",
                req, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResponse> parametroAusente(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST,
                "O parâmetro obrigatório '" + ex.getParameterName() + "' não foi informado.",
                req, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErroResponse> headerAusente(MissingRequestHeaderException ex, HttpServletRequest req) {
        String mensagem = "O header obrigatório '" + ex.getHeaderName() + "' não foi informado.";
        if ("X-Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            mensagem += " Toda requisição POST precisa de uma chave única (ex.: um UUID) para garantir idempotência.";
        }
        return resposta(HttpStatus.BAD_REQUEST, mensagem, req, null);
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ErroResponse> parametroInvalido(ParametroInvalidoException ex, HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    // ---------- 401 / 403 ----------

    @ExceptionHandler(NaoAutenticadoException.class)
    public ResponseEntity<ErroResponse> naoAutenticado(NaoAutenticadoException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"X-API-Key\"")
                .body(corpo(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> acessoNegado(AcessoNegadoException ex, HttpServletRequest req) {
        return resposta(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    // ---------- 404 ----------

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> rotaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return resposta(HttpStatus.NOT_FOUND,
                "A rota '" + req.getRequestURI() + "' não existe. Consulte a documentação em /swagger-ui.html.",
                req, null);
    }

    // ---------- 405 / 406 / 415 ----------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> metodoNaoSuportado(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED,
                "O método " + ex.getMethod() + " não é suportado nesta rota. Métodos aceitos: "
                        + (ex.getSupportedHttpMethods() == null ? "-" : ex.getSupportedHttpMethods()) + ".",
                req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResponse> midiaNaoSuportada(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '" + ex.getContentType() + "' não suportado. Envie o corpo como application/json.",
                req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> midiaNaoAceitavel() {
        // Não é possível escrever um corpo no formato pedido pelo cliente, então devolvemos apenas o status.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    // ---------- 409 / 422 ----------

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> conflito(ConflitoException ex, HttpServletRequest req) {
        return resposta(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        return resposta(HttpStatus.CONFLICT,
                "A operação viola uma restrição do banco de dados (registro duplicado ou ainda vinculado a outro recurso).",
                req, null);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException ex, HttpServletRequest req) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, null);
    }

    // ---------- 429 ----------

    @ExceptionHandler(LimiteRequisicoesException.class)
    public ResponseEntity<ErroResponse> limiteExcedido(LimiteRequisicoesException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSegundos()))
                .body(corpo(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req, null));
    }

    // ---------- 500 ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> erroInesperado(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno inesperado. Tente novamente; se o problema persistir, contate o suporte.",
                req, null);
    }

    // ---------- utilitários ----------

    private ErroResponse.CampoInvalido campo(FieldError erro) {
        return new ErroResponse.CampoInvalido(erro.getField(), erro.getDefaultMessage());
    }

    private ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem, HttpServletRequest req,
                                                  List<ErroResponse.CampoInvalido> campos) {
        return ResponseEntity.status(status).body(corpo(status, mensagem, req, campos));
    }

    private ErroResponse corpo(HttpStatus status, String mensagem, HttpServletRequest req,
                               List<ErroResponse.CampoInvalido> campos) {
        return new ErroResponse(
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                req.getRequestURI(),
                campos);
    }
}
