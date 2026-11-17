package com.fishapi.service;

import com.fishapi.dto.EspecieRequest;
import com.fishapi.dto.EspecieResponse;
import com.fishapi.exception.ConflitoException;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.model.Especie;
import com.fishapi.repository.EspecieRepository;
import com.fishapi.repository.PeixeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EspecieService {

    public static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "nome", "familia");

    private final EspecieRepository repository;
    private final PeixeRepository peixeRepository;

    public EspecieService(EspecieRepository repository, PeixeRepository peixeRepository) {
        this.repository = repository;
        this.peixeRepository = peixeRepository;
    }

    public Page<EspecieResponse> listar(Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        return repository.findAll(pageable).map(EspecieResponse::de);
    }

    public EspecieResponse buscarPorId(Long id) {
        return EspecieResponse.de(buscarEntidade(id));
    }

    public Page<EspecieResponse> buscarPorFamilia(String familia, Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        String termo = Paginacao.textoObrigatorio(familia, "familia");
        return repository.findByFamiliaContainingIgnoreCase(termo, pageable).map(EspecieResponse::de);
    }

    @Transactional
    public EspecieResponse criar(EspecieRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome().trim())) {
            throw new ConflitoException("Já existe uma espécie chamada '" + request.nome().trim() + "'.");
        }
        Especie especie = new Especie();
        preencher(especie, request);
        return EspecieResponse.de(repository.save(especie));
    }

    @Transactional
    public EspecieResponse atualizar(Long id, EspecieRequest request) {
        Especie especie = buscarEntidade(id);
        if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome().trim(), id)) {
            throw new ConflitoException("Já existe outra espécie chamada '" + request.nome().trim() + "'.");
        }
        preencher(especie, request);
        return EspecieResponse.de(repository.save(especie));
    }

    @Transactional
    public void excluir(Long id) {
        Especie especie = buscarEntidade(id);
        long quantidade = peixeRepository.countByEspecieId(id);
        if (quantidade > 0) {
            throw new ConflitoException("Não é possível excluir a espécie '" + especie.getNome() + "': existem "
                    + quantidade + " peixe(s) cadastrados com ela. Altere ou exclua esses peixes primeiro.");
        }
        repository.delete(especie);
    }

    private Especie buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Espécie com id " + id + " não encontrada."));
    }

    private void preencher(Especie especie, EspecieRequest request) {
        especie.setNome(request.nome().trim());
        especie.setFamilia(request.familia().trim());
    }
}
