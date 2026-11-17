package com.fishapi.config;

import com.fishapi.model.*;
import com.fishapi.repository.*;
import com.fishapi.seguranca.ApiKeyService;
import com.fishapi.seguranca.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Carrega dados iniciais ao subir a aplicação (o H2 é em memória).
 * Os exemplos da documentação Swagger usam exatamente estes registros.
 *
 * IDs gerados:
 *   Espécies:     1 Peixe-palhaço | 2 Cirurgião-patela | 3 Betta | 4 Tetra-neon
 *   Cuidadores:   1 João Silva | 2 Ana Souza | 3 Carlos Lima (sem aquário)
 *   Aquários:     1 Aquário Principal | 2 Aquário Recifal | 3 Aquário Comunitário
 *   Alimentações: 1 Ração Tropical | 2 Artêmia Viva | 3 Spirulina em Flocos | 4 Larva de Mosquito Congelada
 *   Peixes:       1 Nemo | 2 Marlin | 3 Dory | 4 Azulão | 5 Faísca
 *   API Keys:     1 ADMIN | 2 GERENTE | 3 BIOLOGO | 4 CUIDADOR | 5 VISITANTE (chaves fixas do Swagger)
 *                 6 a 10: chaves dos usuários abaixo
 *   Usuários:     1 Marina Alves (ADMIN) | 2 Rafael Costa (GERENTE) | 3 Beatriz Lima (BIOLOGO)
 *                 4 Lucas Pereira (CUIDADOR) | 5 Júlia Santos (VISITANTE)
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final EspecieRepository especieRepository;
    private final CuidadorRepository cuidadorRepository;
    private final AquarioRepository aquarioRepository;
    private final AlimentacaoRepository alimentacaoRepository;
    private final PeixeRepository peixeRepository;
    private final ApiKeyService apiKeyService;
    private final AuthService authService;

    private final String chaveAdmin;
    private final String chaveGerente;
    private final String chaveBiologo;
    private final String chaveCuidador;
    private final String chaveVisitante;

    public DataLoader(EspecieRepository especieRepository,
                      CuidadorRepository cuidadorRepository,
                      AquarioRepository aquarioRepository,
                      AlimentacaoRepository alimentacaoRepository,
                      PeixeRepository peixeRepository,
                      ApiKeyService apiKeyService,
                      AuthService authService,
                      @Value("${hookfishing.api-keys.admin}") String chaveAdmin,
                      @Value("${hookfishing.api-keys.gerente}") String chaveGerente,
                      @Value("${hookfishing.api-keys.biologo}") String chaveBiologo,
                      @Value("${hookfishing.api-keys.cuidador}") String chaveCuidador,
                      @Value("${hookfishing.api-keys.visitante}") String chaveVisitante) {
        this.especieRepository = especieRepository;
        this.cuidadorRepository = cuidadorRepository;
        this.aquarioRepository = aquarioRepository;
        this.alimentacaoRepository = alimentacaoRepository;
        this.peixeRepository = peixeRepository;
        this.apiKeyService = apiKeyService;
        this.authService = authService;
        this.chaveAdmin = chaveAdmin;
        this.chaveGerente = chaveGerente;
        this.chaveBiologo = chaveBiologo;
        this.chaveCuidador = chaveCuidador;
        this.chaveVisitante = chaveVisitante;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Chaves fixas para testar no Swagger (uma por papel)
        apiKeyService.garantirChave("Swagger - ADMIN", "demo@hookfishing.com", PapelAcesso.ADMIN, Plano.PROFISSIONAL, chaveAdmin);
        apiKeyService.garantirChave("Swagger - GERENTE", "demo@hookfishing.com", PapelAcesso.GERENTE, Plano.BASICO, chaveGerente);
        apiKeyService.garantirChave("Swagger - BIOLOGO", "demo@hookfishing.com", PapelAcesso.BIOLOGO, Plano.BASICO, chaveBiologo);
        apiKeyService.garantirChave("Swagger - CUIDADOR", "demo@hookfishing.com", PapelAcesso.CUIDADOR, Plano.GRATUITO, chaveCuidador);
        apiKeyService.garantirChave("Swagger - VISITANTE", "demo@hookfishing.com", PapelAcesso.VISITANTE, Plano.GRATUITO, chaveVisitante);

        // Usuários para entrar no front-end com e-mail e senha (um por papel)
        authService.garantirUsuario("Marina Alves", "admin@hookfishing.com", "admin123", PapelAcesso.ADMIN, Plano.PROFISSIONAL);
        authService.garantirUsuario("Rafael Costa", "gerente@hookfishing.com", "gerente123", PapelAcesso.GERENTE, Plano.BASICO);
        authService.garantirUsuario("Beatriz Lima", "biologo@hookfishing.com", "biologo123", PapelAcesso.BIOLOGO, Plano.BASICO);
        authService.garantirUsuario("Lucas Pereira", "cuidador@hookfishing.com", "cuidador123", PapelAcesso.CUIDADOR, Plano.GRATUITO);
        authService.garantirUsuario("Júlia Santos", "visitante@hookfishing.com", "visitante123", PapelAcesso.VISITANTE, Plano.GRATUITO);

        if (especieRepository.count() > 0) {
            return;
        }

        Especie palhaco = especie("Peixe-palhaço", "Pomacentridae");
        Especie cirurgiao = especie("Cirurgião-patela", "Acanthuridae");
        Especie betta = especie("Betta", "Osphronemidae");
        Especie neon = especie("Tetra-neon", "Characidae");

        Cuidador joao = cuidador("João Silva", "joao@email.com");
        Cuidador ana = cuidador("Ana Souza", "ana.souza@email.com");
        cuidador("Carlos Lima", "carlos.lima@email.com");

        Aquario principal = aquario("Aquário Principal", 200, joao);
        Aquario recifal = aquario("Aquário Recifal", 350, ana);
        Aquario comunitario = aquario("Aquário Comunitário", 120, null);

        Alimentacao racao = alimentacao("Ração Tropical", TipoAlimentacao.RACAO);
        Alimentacao artemia = alimentacao("Artêmia Viva", TipoAlimentacao.VIVA);
        Alimentacao spirulina = alimentacao("Spirulina em Flocos", TipoAlimentacao.VEGETAL);
        Alimentacao larva = alimentacao("Larva de Mosquito Congelada", TipoAlimentacao.CONGELADA);

        peixe("Nemo", 2, 8.5, palhaco, principal, List.of(racao, artemia));
        peixe("Marlin", 5, 10.0, palhaco, principal, List.of(racao));
        peixe("Dory", 3, 25.0, cirurgiao, recifal, List.of(racao, spirulina));
        peixe("Azulão", 1, 6.0, betta, comunitario, List.of(larva));
        peixe("Faísca", 1, 3.5, neon, comunitario, List.of(racao, spirulina));

        log.info("Dados iniciais carregados. Swagger: http://localhost:8080/swagger-ui.html | "
                + "API Keys de demonstração -> ADMIN: {} | GERENTE: {} | BIOLOGO: {} | CUIDADOR: {} | VISITANTE: {}",
                chaveAdmin, chaveGerente, chaveBiologo, chaveCuidador, chaveVisitante);
    }

    private Especie especie(String nome, String familia) {
        Especie e = new Especie();
        e.setNome(nome);
        e.setFamilia(familia);
        return especieRepository.save(e);
    }

    private Cuidador cuidador(String nome, String email) {
        Cuidador c = new Cuidador();
        c.setNome(nome);
        c.setEmail(email);
        return cuidadorRepository.save(c);
    }

    private Aquario aquario(String nome, int capacidade, Cuidador cuidador) {
        Aquario a = new Aquario();
        a.setNome(nome);
        a.setCapacidadeLitros(capacidade);
        a.setCuidador(cuidador);
        return aquarioRepository.save(a);
    }

    private Alimentacao alimentacao(String nome, TipoAlimentacao tipo) {
        Alimentacao a = new Alimentacao();
        a.setNome(nome);
        a.setTipo(tipo);
        return alimentacaoRepository.save(a);
    }

    private void peixe(String nome, int idade, double tamanho, Especie especie, Aquario aquario,
                       List<Alimentacao> alimentacoes) {
        Peixe p = new Peixe();
        p.setNome(nome);
        p.setIdade(idade);
        p.setTamanho(tamanho);
        p.setEspecie(especie);
        p.setAquario(aquario);
        p.getAlimentacoes().addAll(alimentacoes);
        peixeRepository.save(p);
    }
}
