package com.fishapi.seguranca;

import com.fishapi.dto.ApiKeyRequest;
import com.fishapi.dto.ApiKeyResponse;
import com.fishapi.dto.ApiKeyUpdateRequest;
import com.fishapi.exception.ConflitoException;
import com.fishapi.exception.NaoAutenticadoException;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.model.ApiKey;
import com.fishapi.model.PapelAcesso;
import com.fishapi.repository.ApiKeyRepository;
import com.fishapi.repository.UsuarioRepository;
import com.fishapi.service.Paginacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ApiKeyService {

    public static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "nome", "email", "papel", "criadaEm");
    private static final String AVISO = "Guarde esta chave em local seguro: ela não será exibida novamente.";
    private static final int TAMANHO_PREFIXO = 11;

    private final ApiKeyRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Valida a chave recebida no header X-API-Key. */
    @Transactional
    public ClienteAutenticado autenticar(String chave) {
        ApiKey apiKey = repository.findByHashChave(Hashes.sha256(chave.trim()))
                .orElseThrow(() -> new NaoAutenticadoException(
                        "API Key inválida. Verifique o valor enviado no header X-API-Key."));
        if (!apiKey.isAtiva()) {
            throw new NaoAutenticadoException("Esta API Key foi desativada. Solicite uma nova chave ao administrador.");
        }
        apiKey.setUltimoUsoEm(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return new ClienteAutenticado(apiKey.getId(), apiKey.getNome(), apiKey.getPapel());
    }

    public Page<ApiKeyResponse> listar(Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        return repository.findAll(pageable).map(ApiKeyResponse::de);
    }

    public ApiKeyResponse buscarPorId(Long id) {
        return ApiKeyResponse.de(buscarEntidade(id));
    }

    @Transactional
    public ApiKeyResponse criar(ApiKeyRequest request) {
        String chave = gerarChave();
        ApiKey apiKey = new ApiKey();
        apiKey.setNome(request.nome().trim());
        apiKey.setEmail(request.email().trim().toLowerCase());
        apiKey.setPapel(request.papel());
        aplicarChave(apiKey, chave);
        apiKey.setAtiva(true);
        apiKey.setCriadaEm(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return ApiKeyResponse.comChave(repository.save(apiKey), chave, AVISO);
    }

    @Transactional
    public ApiKeyResponse atualizar(Long id, ApiKeyUpdateRequest request) {
        ApiKey apiKey = buscarEntidade(id);
        ClienteAutenticado atual = ContextoRequisicao.clienteAtual();
        if (apiKey.getId().equals(atual.apiKeyId())
                && (!Boolean.TRUE.equals(request.ativa()) || request.papel() != PapelAcesso.ADMIN)) {
            throw new ConflitoException("Você não pode desativar nem rebaixar a própria API Key que está usando nesta requisição.");
        }
        apiKey.setNome(request.nome().trim());
        apiKey.setEmail(request.email().trim().toLowerCase());
        apiKey.setPapel(request.papel());
        apiKey.setAtiva(request.ativa());
        return ApiKeyResponse.de(repository.save(apiKey));
    }

    @Transactional
    public ApiKeyResponse regenerar(Long id) {
        ApiKey apiKey = buscarEntidade(id);
        String chave = gerarChave();
        aplicarChave(apiKey, chave);
        return ApiKeyResponse.comChave(repository.save(apiKey), chave,
                "A chave anterior deixou de funcionar. " + AVISO);
    }

    @Transactional
    public void revogar(Long id) {
        ApiKey apiKey = buscarEntidade(id);
        if (apiKey.getId().equals(ContextoRequisicao.clienteAtual().apiKeyId())) {
            throw new ConflitoException("Você não pode revogar a própria API Key que está usando nesta requisição.");
        }
        if (usuarioRepository.existsByApiKeyId(id)) {
            throw new ConflitoException("Esta API Key pertence a um usuário cadastrado e não pode ser excluída. "
                    + "Para bloquear o acesso dele, desative-a (PUT /api-keys/" + id + " com \"ativa\": false).");
        }
        repository.delete(apiKey);
    }

    public ApiKeyResponse minhaChave() {
        return buscarPorId(ContextoRequisicao.clienteAtual().apiKeyId());
    }

    /** Cria uma chave com valor fixo (usada para as chaves de demonstração carregadas na inicialização). */
    @Transactional
    public void garantirChave(String nome, String email, PapelAcesso papel, String chave) {
        if (chave == null || chave.isBlank() || repository.existsByHashChave(Hashes.sha256(chave.trim()))) {
            return;
        }
        ApiKey apiKey = new ApiKey();
        apiKey.setNome(nome);
        apiKey.setEmail(email);
        apiKey.setPapel(papel);
        aplicarChave(apiKey, chave.trim());
        apiKey.setAtiva(true);
        apiKey.setCriadaEm(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        repository.save(apiKey);
    }

    /** Cria a API Key de um usuário recém-cadastrado (o valor é emitido apenas no login). */
    @Transactional
    public ApiKey novaChaveDeUsuario(String nome, String email, PapelAcesso papel) {
        ApiKey apiKey = new ApiKey();
        apiKey.setNome(nome);
        apiKey.setEmail(email);
        apiKey.setPapel(papel);
        aplicarChave(apiKey, gerarChave());
        apiKey.setAtiva(true);
        apiKey.setCriadaEm(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return repository.save(apiKey);
    }

    /** Gera um novo valor para a chave (usado no login) e devolve o valor em texto. */
    @Transactional
    public String rotacionar(ApiKey apiKey) {
        String chave = gerarChave();
        aplicarChave(apiKey, chave);
        apiKey.setUltimoUsoEm(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        repository.save(apiKey);
        return chave;
    }

    private ApiKey buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("API Key com id " + id + " não encontrada."));
    }

    private void aplicarChave(ApiKey apiKey, String chave) {
        apiKey.setHashChave(Hashes.sha256(chave));
        apiKey.setPrefixo(chave.substring(0, Math.min(TAMANHO_PREFIXO, chave.length())));
    }

    private String gerarChave() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "hf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
