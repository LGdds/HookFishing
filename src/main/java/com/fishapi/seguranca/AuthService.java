package com.fishapi.seguranca;

import com.fishapi.dto.CadastroRequest;
import com.fishapi.dto.LoginRequest;
import com.fishapi.dto.LoginResponse;
import com.fishapi.dto.UsuarioResponse;
import com.fishapi.exception.AcessoNegadoException;
import com.fishapi.exception.ConflitoException;
import com.fishapi.exception.NaoAutenticadoException;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.model.ApiKey;
import com.fishapi.model.PapelAcesso;
import com.fishapi.model.Plano;
import com.fishapi.model.Usuario;
import com.fishapi.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Cadastro e login de usuários com e-mail e senha.
 *
 * - Cadastro: cria o usuário e a API Key dele com o papel VISITANTE.
 *   Um ADMIN pode promover o usuário alterando o papel da chave em PUT /api-keys/{id}.
 * - Login: confere a senha e emite um novo valor para a API Key do usuário,
 *   que o front-end passa a enviar no header X-API-Key.
 */
@Service
@Transactional
public class AuthService {

    public static final PapelAcesso PAPEL_INICIAL = PapelAcesso.VISITANTE;
    public static final Plano PLANO_INICIAL = Plano.GRATUITO;

    private final UsuarioRepository repository;
    private final ApiKeyService apiKeyService;

    public AuthService(UsuarioRepository repository, ApiKeyService apiKeyService) {
        this.repository = repository;
        this.apiKeyService = apiKeyService;
    }

    public UsuarioResponse cadastrar(CadastroRequest request) {
        return UsuarioResponse.de(criarUsuario(request.nome(), request.email(), request.senha(), PAPEL_INICIAL, PLANO_INICIAL));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return repository.findById(id)
                .map(UsuarioResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário com id " + id + " não encontrado."));
    }

    public LoginResponse entrar(LoginRequest request) {
        Usuario usuario = repository.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> Senhas.confere(request.senha(), u.getSenhaHash()))
                .orElseThrow(() -> new NaoAutenticadoException("E-mail ou senha inválidos."));

        ApiKey apiKey = usuario.getApiKey();
        if (!apiKey.isAtiva()) {
            throw new AcessoNegadoException("O acesso deste usuário foi desativado por um administrador.");
        }

        String chave = apiKeyService.chaveParaLogin(apiKey.getId());
        return new LoginResponse(chave, ApiKeyInterceptor.HEADER, UsuarioResponse.de(usuario),
                Permissoes.mapaDe(apiKey.getPapel()));
    }

    /** Usado pelo DataLoader para criar os usuários de demonstração. */
    public void garantirUsuario(String nome, String email, String senha, PapelAcesso papel, Plano plano) {
        if (!repository.existsByEmailIgnoreCase(email)) {
            criarUsuario(nome, email, senha, papel, plano);
        }
    }

    private Usuario criarUsuario(String nome, String email, String senha, PapelAcesso papel, Plano plano) {
        String emailNormalizado = email.trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ConflitoException("Já existe uma conta com o e-mail '" + emailNormalizado + "'. Faça login.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(nome.trim());
        usuario.setEmail(emailNormalizado);
        usuario.setSenhaHash(Senhas.gerarHash(senha));
        usuario.setApiKey(apiKeyService.novaChaveDeUsuario(nome.trim(), emailNormalizado, papel, plano));
        usuario.setCriadoEm(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return repository.save(usuario);
    }
}
