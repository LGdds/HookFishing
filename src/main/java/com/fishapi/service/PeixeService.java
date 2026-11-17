package com.fishapi.service;

import com.fishapi.dto.PeixeRequest;
import com.fishapi.dto.PeixeResponse;
import com.fishapi.dto.PeixeV2Response;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.model.Alimentacao;
import com.fishapi.model.Peixe;
import com.fishapi.repository.AlimentacaoRepository;
import com.fishapi.repository.AquarioRepository;
import com.fishapi.repository.EspecieRepository;
import com.fishapi.repository.PeixeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class PeixeService {

    public static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "nome", "idade", "tamanho");

    private final PeixeRepository repository;
    private final EspecieRepository especieRepository;
    private final AquarioRepository aquarioRepository;
    private final AlimentacaoRepository alimentacaoRepository;

    public PeixeService(PeixeRepository repository,
                        EspecieRepository especieRepository,
                        AquarioRepository aquarioRepository,
                        AlimentacaoRepository alimentacaoRepository) {
        this.repository = repository;
        this.especieRepository = especieRepository;
        this.aquarioRepository = aquarioRepository;
        this.alimentacaoRepository = alimentacaoRepository;
    }

    public Page<PeixeResponse> listar(Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        return repository.findAll(pageable).map(PeixeResponse::de);
    }

    public Page<PeixeV2Response> listarV2(Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        return repository.findAll(pageable).map(PeixeV2Response::de);
    }

    public PeixeResponse buscarPorId(Long id) {
        return PeixeResponse.de(buscarEntidade(id));
    }

    public PeixeV2Response buscarPorIdV2(Long id) {
        return PeixeV2Response.de(buscarEntidade(id));
    }

    public Page<PeixeResponse> buscarPorNome(String nome, Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        String termo = Paginacao.textoObrigatorio(nome, "nome");
        return repository.findByNomeContainingIgnoreCase(termo, pageable).map(PeixeResponse::de);
    }

    @Transactional
    public PeixeResponse criar(PeixeRequest request) {
        Peixe peixe = new Peixe();
        preencher(peixe, request);
        return PeixeResponse.de(repository.save(peixe));
    }

    @Transactional
    public PeixeResponse atualizar(Long id, PeixeRequest request) {
        Peixe peixe = buscarEntidade(id);
        preencher(peixe, request);
        return PeixeResponse.de(repository.save(peixe));
    }

    @Transactional
    public void excluir(Long id) {
        Peixe peixe = buscarEntidade(id);
        // As linhas da tabela peixe_alimentacao são removidas automaticamente (Peixe é o dono do Many-to-Many)
        repository.delete(peixe);
    }

    private Peixe buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Peixe com id " + id + " não encontrado."));
    }

    private void preencher(Peixe peixe, PeixeRequest request) {
        peixe.setNome(request.nome().trim());
        peixe.setIdade(request.idade());
        peixe.setTamanho(request.tamanho());

        peixe.setEspecie(especieRepository.findById(request.especieId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Espécie com id " + request.especieId() + " não encontrada. Consulte GET /especies.")));

        peixe.setAquario(aquarioRepository.findById(request.aquarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Aquário com id " + request.aquarioId() + " não encontrado. Consulte GET /aquarios.")));

        Set<Long> ids = request.alimentacaoIds() == null
                ? Set.of()
                : new LinkedHashSet<>(request.alimentacaoIds());

        List<Alimentacao> alimentacoes = alimentacaoRepository.findAllById(ids);
        if (alimentacoes.size() != ids.size()) {
            Set<Long> encontrados = new HashSet<>();
            alimentacoes.forEach(a -> encontrados.add(a.getId()));
            List<Long> faltando = ids.stream().filter(i -> !encontrados.contains(i)).toList();
            throw new RecursoNaoEncontradoException(
                    "Alimentação(ões) com id " + faltando + " não encontrada(s). Consulte GET /alimentacoes.");
        }

        peixe.getAlimentacoes().clear();
        peixe.getAlimentacoes().addAll(alimentacoes);
    }
}
