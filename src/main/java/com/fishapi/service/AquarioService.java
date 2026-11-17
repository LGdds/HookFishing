package com.fishapi.service;

import com.fishapi.dto.AquarioRequest;
import com.fishapi.dto.AquarioResponse;
import com.fishapi.exception.ConflitoException;
import com.fishapi.exception.ParametroInvalidoException;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.model.Aquario;
import com.fishapi.model.Cuidador;
import com.fishapi.repository.AquarioRepository;
import com.fishapi.repository.CuidadorRepository;
import com.fishapi.repository.PeixeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AquarioService {

    public static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "nome", "capacidadeLitros");

    private final AquarioRepository repository;
    private final CuidadorRepository cuidadorRepository;
    private final PeixeRepository peixeRepository;

    public AquarioService(AquarioRepository repository,
                          CuidadorRepository cuidadorRepository,
                          PeixeRepository peixeRepository) {
        this.repository = repository;
        this.cuidadorRepository = cuidadorRepository;
        this.peixeRepository = peixeRepository;
    }

    public Page<AquarioResponse> listar(Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        return repository.findAll(pageable).map(AquarioResponse::de);
    }

    public AquarioResponse buscarPorId(Long id) {
        return AquarioResponse.de(buscarEntidade(id));
    }

    public Page<AquarioResponse> buscarPorCapacidadeMinima(Integer capacidadeMinima, Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        if (capacidadeMinima == null || capacidadeMinima < 1) {
            throw new ParametroInvalidoException("O parâmetro 'capacidadeMinima' deve ser um número inteiro maior ou igual a 1.");
        }
        return repository.findByCapacidadeLitrosGreaterThanEqual(capacidadeMinima, pageable).map(AquarioResponse::de);
    }

    @Transactional
    public AquarioResponse criar(AquarioRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome().trim())) {
            throw new ConflitoException("Já existe um aquário chamado '" + request.nome().trim() + "'.");
        }
        Aquario aquario = new Aquario();
        preencher(aquario, request);
        return AquarioResponse.de(repository.save(aquario));
    }

    @Transactional
    public AquarioResponse atualizar(Long id, AquarioRequest request) {
        Aquario aquario = buscarEntidade(id);
        if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome().trim(), id)) {
            throw new ConflitoException("Já existe outro aquário chamado '" + request.nome().trim() + "'.");
        }
        preencher(aquario, request);
        return AquarioResponse.de(repository.save(aquario));
    }

    @Transactional
    public void excluir(Long id) {
        Aquario aquario = buscarEntidade(id);
        long quantidade = peixeRepository.countByAquarioId(id);
        if (quantidade > 0) {
            throw new ConflitoException("Não é possível excluir o aquário '" + aquario.getNome() + "': ele possui "
                    + quantidade + " peixe(s). Transfira ou exclua os peixes primeiro.");
        }
        repository.delete(aquario);
    }

    private Aquario buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aquário com id " + id + " não encontrado."));
    }

    private void preencher(Aquario aquario, AquarioRequest request) {
        aquario.setNome(request.nome().trim());
        aquario.setCapacidadeLitros(request.capacidadeLitros());

        if (request.cuidadorId() == null) {
            aquario.setCuidador(null);
            return;
        }

        Cuidador cuidador = cuidadorRepository.findById(request.cuidadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Cuidador com id " + request.cuidadorId() + " não encontrado. Consulte GET /cuidadores."));

        // Regra do One-to-One: o cuidador não pode estar vinculado a outro aquário
        repository.findByCuidadorId(cuidador.getId())
                .filter(outro -> !outro.getId().equals(aquario.getId()))
                .ifPresent(outro -> {
                    throw new ConflitoException("O cuidador '" + cuidador.getNome() + "' já é responsável pelo aquário '"
                            + outro.getNome() + "' (id " + outro.getId() + "). Cada cuidador cuida de apenas um aquário.");
                });

        aquario.setCuidador(cuidador);
    }
}
