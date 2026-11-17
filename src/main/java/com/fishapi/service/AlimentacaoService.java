package com.fishapi.service;

import com.fishapi.dto.AlimentacaoRequest;
import com.fishapi.dto.AlimentacaoResponse;
import com.fishapi.exception.ConflitoException;
import com.fishapi.exception.ParametroInvalidoException;
import com.fishapi.exception.RecursoNaoEncontradoException;
import com.fishapi.model.Alimentacao;
import com.fishapi.model.Peixe;
import com.fishapi.model.TipoAlimentacao;
import com.fishapi.repository.AlimentacaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AlimentacaoService {

    public static final Set<String> CAMPOS_ORDENACAO = Set.of("id", "nome", "tipo");

    private final AlimentacaoRepository repository;

    public AlimentacaoService(AlimentacaoRepository repository) {
        this.repository = repository;
    }

    public Page<AlimentacaoResponse> listar(Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        return repository.findAll(pageable).map(AlimentacaoResponse::de);
    }

    public AlimentacaoResponse buscarPorId(Long id) {
        return AlimentacaoResponse.de(buscarEntidade(id));
    }

    public Page<AlimentacaoResponse> buscarPorTipo(TipoAlimentacao tipo, Pageable pageable) {
        Paginacao.validarOrdenacao(pageable, CAMPOS_ORDENACAO);
        if (tipo == null) {
            throw new ParametroInvalidoException("Informe o parâmetro 'tipo' (RACAO, VIVA, VEGETAL ou CONGELADA).");
        }
        return repository.findByTipo(tipo, pageable).map(AlimentacaoResponse::de);
    }

    @Transactional
    public AlimentacaoResponse criar(AlimentacaoRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome().trim())) {
            throw new ConflitoException("Já existe uma alimentação chamada '" + request.nome().trim() + "'.");
        }
        Alimentacao alimentacao = new Alimentacao();
        preencher(alimentacao, request);
        return AlimentacaoResponse.de(repository.save(alimentacao));
    }

    @Transactional
    public AlimentacaoResponse atualizar(Long id, AlimentacaoRequest request) {
        Alimentacao alimentacao = buscarEntidade(id);
        if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome().trim(), id)) {
            throw new ConflitoException("Já existe outra alimentação chamada '" + request.nome().trim() + "'.");
        }
        preencher(alimentacao, request);
        return AlimentacaoResponse.de(repository.save(alimentacao));
    }

    @Transactional
    public void excluir(Long id) {
        Alimentacao alimentacao = buscarEntidade(id);
        // Many-to-Many: remove a alimentação da dieta dos peixes antes de excluí-la
        for (Peixe peixe : new ArrayList<>(alimentacao.getPeixes())) {
            peixe.getAlimentacoes().remove(alimentacao);
        }
        alimentacao.getPeixes().clear();
        repository.delete(alimentacao);
    }

    private Alimentacao buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alimentação com id " + id + " não encontrada."));
    }

    private void preencher(Alimentacao alimentacao, AlimentacaoRequest request) {
        alimentacao.setNome(request.nome().trim());
        alimentacao.setTipo(request.tipo());
    }
}
