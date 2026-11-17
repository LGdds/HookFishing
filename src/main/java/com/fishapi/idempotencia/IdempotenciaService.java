package com.fishapi.idempotencia;

import com.fishapi.exception.ConflitoException;
import com.fishapi.exception.ParametroInvalidoException;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.exception.RegraNegocioException;
import com.fishapi.model.RegistroIdempotencia;
import com.fishapi.repository.RegistroIdempotenciaRepository;
import com.fishapi.seguranca.ContextoRequisicao;
import com.fishapi.seguranca.Hashes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Idempotência para operações POST usando o header X-Idempotency-Key.
 *
 * Fluxo:
 * 1. Primeira requisição com a chave -> registra a chave como EM_PROCESSAMENTO, executa a criação,
 *    salva o ID do recurso criado e marca como CONCLUIDA.
 * 2. Mesma chave + mesmo corpo -> NÃO cria de novo; devolve o recurso criado na primeira vez
 *    (header X-Idempotent-Replayed: true).
 * 3. Mesma chave + corpo diferente -> 422 (a chave já pertence a outra operação).
 * 4. Mesma chave enquanto a primeira ainda está em processamento -> 409.
 *
 * As chaves são separadas por API Key (um cliente não enxerga as chaves de outro)
 * e expiram após o período configurado em hookfishing.idempotencia.validade-horas.
 */
@Service
public class IdempotenciaService {

    public static final String HEADER = "X-Idempotency-Key";
    public static final String HEADER_REPETIDA = "X-Idempotent-Replayed";

    private static final Pattern FORMATO_CHAVE = Pattern.compile("^[A-Za-z0-9_\\-:.]{8,100}$");

    private final RegistroIdempotenciaRepository repository;
    private final long validadeHoras;

    public IdempotenciaService(RegistroIdempotenciaRepository repository,
                               @Value("${hookfishing.idempotencia.validade-horas:24}") long validadeHoras) {
        this.repository = repository;
        this.validadeHoras = validadeHoras;
    }

    /**
     * @param chave      valor do header X-Idempotency-Key
     * @param operacao   identificação da operação (ex.: "POST /peixes")
     * @param requisicao corpo da requisição (usado para detectar reutilização da chave com outros dados)
     * @param criar      executa a criação de fato
     * @param extrairId  obtém o ID do recurso criado
     * @param buscar     recupera o recurso já criado (usado nas repetições)
     */
    public <R> ResultadoIdempotente<R> executar(String chave,
                                                String operacao,
                                                Object requisicao,
                                                Supplier<R> criar,
                                                Function<R, Long> extrairId,
                                                Function<Long, R> buscar) {
        String cliente = String.valueOf(ContextoRequisicao.clienteAtual().apiKeyId());
        return executarComoCliente(cliente, chave, operacao, requisicao, criar, extrairId, buscar);
    }

    /**
     * Variante para rotas públicas (ex.: POST /auth/cadastro), que não têm API Key:
     * as chaves de idempotência ficam num espaço próprio, "publico".
     */
    public <R> ResultadoIdempotente<R> executarPublico(String chave,
                                                       String operacao,
                                                       Object requisicao,
                                                       Supplier<R> criar,
                                                       Function<R, Long> extrairId,
                                                       Function<Long, R> buscar) {
        return executarComoCliente("publico", chave, operacao, requisicao, criar, extrairId, buscar);
    }

    private <R> ResultadoIdempotente<R> executarComoCliente(String cliente,
                                                            String chave,
                                                            String operacao,
                                                            Object requisicao,
                                                            Supplier<R> criar,
                                                            Function<R, Long> extrairId,
                                                            Function<Long, R> buscar) {
        validarFormato(chave);
        String hash = Hashes.sha256(operacao + "|" + requisicao);
        Instant agora = Instant.now();

        Optional<RegistroIdempotencia> existente = repository.findByChaveAndCliente(chave, cliente);
        if (existente.isPresent()) {
            RegistroIdempotencia registro = existente.get();
            if (registro.getExpiraEm().isBefore(agora)) {
                repository.delete(registro);
                repository.flush();
            } else {
                return repetir(registro, operacao, hash, buscar);
            }
        }

        RegistroIdempotencia registro = new RegistroIdempotencia();
        registro.setChave(chave);
        registro.setCliente(cliente);
        registro.setOperacao(operacao);
        registro.setHashRequisicao(hash);
        registro.setStatus(RegistroIdempotencia.Status.EM_PROCESSAMENTO);
        registro.setCriadoEm(agora.truncatedTo(ChronoUnit.SECONDS));
        registro.setExpiraEm(agora.plus(validadeHoras, ChronoUnit.HOURS));

        try {
            registro = repository.saveAndFlush(registro);
        } catch (DataIntegrityViolationException e) {
            // Outra requisição com a mesma chave chegou ao mesmo tempo
            throw new ConflitoException("Já existe uma requisição com a chave de idempotência '" + chave
                    + "' sendo processada. Aguarde e tente novamente.");
        }

        try {
            R resposta = criar.get();
            registro.setRecursoId(extrairId.apply(resposta));
            registro.setStatus(RegistroIdempotencia.Status.CONCLUIDA);
            repository.save(registro);
            return new ResultadoIdempotente<>(resposta, false);
        } catch (RuntimeException e) {
            // A criação falhou (validação, 404, 409...): libera a chave para o cliente corrigir e tentar de novo
            repository.delete(registro);
            throw e;
        }
    }

    private <R> ResultadoIdempotente<R> repetir(RegistroIdempotencia registro, String operacao, String hash,
                                                Function<Long, R> buscar) {
        if (!registro.getOperacao().equals(operacao) || !registro.getHashRequisicao().equals(hash)) {
            throw new RegraNegocioException("A chave de idempotência '" + registro.getChave()
                    + "' já foi usada em " + registro.getOperacao() + " com outro corpo de requisição. "
                    + "Use uma nova chave (ex.: um novo UUID) para uma nova operação.");
        }
        if (registro.getStatus() == RegistroIdempotencia.Status.EM_PROCESSAMENTO) {
            throw new ConflitoException("A requisição com a chave de idempotência '" + registro.getChave()
                    + "' ainda está em processamento. Aguarde e tente novamente.");
        }
        try {
            return new ResultadoIdempotente<>(buscar.apply(registro.getRecursoId()), true);
        } catch (RecursoNaoEncontradoException e) {
            throw new ConflitoException("A chave de idempotência '" + registro.getChave()
                    + "' já foi usada para criar o recurso de id " + registro.getRecursoId()
                    + ", que depois foi excluído. Use uma nova chave para criar outro recurso.");
        }
    }

    private void validarFormato(String chave) {
        if (chave == null || !FORMATO_CHAVE.matcher(chave).matches()) {
            throw new ParametroInvalidoException("Header X-Idempotency-Key inválido. Use de 8 a 100 caracteres "
                    + "(letras, números, '-', '_', ':' ou '.'), por exemplo um UUID: 3f2b8c1e-7d4a-4f6b-9c2e-1a5d8e7f9b30.");
        }
    }
}
