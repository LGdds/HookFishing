package com.fishapi.service;

import com.fishapi.dto.CuidadorRequest;
import com.fishapi.dto.CuidadorResponse;
import com.fishapi.exception.ConflitoException;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.model.Cuidador;
import com.fishapi.repository.AquarioRepository;
import com.fishapi.repository.CuidadorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class CuidadorService {

    public static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "nome", "email");

    private final CuidadorRepository repository;
    private final AquarioRepository aquarioRepository;

    public CuidadorService(CuidadorRepository repository, AquarioRepository aquarioRepository) {
        this.repository = repository;
        this.aquarioRepository = aquarioRepository;
    }

    public Page<CuidadorResponse> listar(Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        return repository.findAll(pageable).map(CuidadorResponse::de);
    }

    public CuidadorResponse buscarPorId(Long id) {
        return CuidadorResponse.de(buscarEntidade(id));
    }

    public CuidadorResponse buscarPorEmail(String email) {
        String termo = Paginacao.textoObrigatorio(email, "email");
        return repository.findByEmailIgnoreCase(termo)
                .map(CuidadorResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum cuidador encontrado com o e-mail '" + termo + "'."));
    }

    @Transactional
    public CuidadorResponse criar(CuidadorRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe um cuidador com o e-mail '" + email + "'.");
        }
        Cuidador cuidador = new Cuidador();
        preencher(cuidador, request);
        return CuidadorResponse.de(repository.save(cuidador));
    }

    @Transactional
    public CuidadorResponse atualizar(Long id, CuidadorRequest request) {
        Cuidador cuidador = buscarEntidade(id);
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflitoException("Já existe outro cuidador com o e-mail '" + email + "'.");
        }
        preencher(cuidador, request);
        return CuidadorResponse.de(repository.save(cuidador));
    }

    @Transactional
    public void excluir(Long id) {
        Cuidador cuidador = buscarEntidade(id);
        aquarioRepository.findByCuidadorId(id).ifPresent(aquario -> {
            throw new ConflitoException("Não é possível excluir o cuidador '" + cuidador.getNome()
                    + "': ele é responsável pelo aquário '" + aquario.getNome() + "' (id " + aquario.getId()
                    + "). Remova-o do aquário (PUT /aquarios/" + aquario.getId() + " sem cuidadorId) primeiro.");
        });
        repository.delete(cuidador);
    }

    private Cuidador buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cuidador com id " + id + " não encontrado."));
    }

    private void preencher(Cuidador cuidador, CuidadorRequest request) {
        cuidador.setNome(request.nome().trim());
        cuidador.setEmail(request.email().trim().toLowerCase());
    }
}
