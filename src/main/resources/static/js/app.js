'use strict';

/* =====================================================================
 * HookFishing - front-end
 *
 * Consome a API REST demonstrando os recursos da Parte 2:
 *   X-API-Key          -> obtida no login (e-mail e senha); define o papel do usuário
 *   X-Idempotency-Key  -> gerada automaticamente em todo cadastro (POST)
 *   X-API-Version      -> alternância v1 / v2 na aba Peixes
 *   X-RateLimit-*      -> barra de rate limit no topo e aviso de 429 com Retry-After (e bloqueio)
 *   Planos             -> aba Planos: preços, consumo da chave e troca de plano (monetização)
 *   CORS               -> funciona também se o front-end for aberto em outra origem permitida
 * ===================================================================== */

// Endereço da API.
// - Página servida pela própria API (localhost:8080 ou Render): mesmo endereço da página (caminhos relativos).
// - index.html aberto direto no navegador (duplo clique), pelo IntelliJ ou pelo Live Server: o usuário escolhe
//   o servidor na tela de login (local ou Render); a escolha fica salva neste navegador.
// - window.HOOKFISHING_API, se definido antes deste script, tem prioridade.
const PORTAS_DESENVOLVIMENTO = ['3000', '5173', '5500', '63342'];
const BASE_STORAGE = 'hookfishing.servidor';
const SERVIDORES = {
    local: { nome: 'Local', url: 'http://localhost:8080' },
    render: { nome: 'Render', url: 'https://hookfishing.onrender.com' }
};
const SERVIDO_PELA_API = !window.HOOKFISHING_API
    && location.protocol !== 'file:' && !PORTAS_DESENVOLVIMENTO.includes(location.port);
let API = window.HOOKFISHING_API
    ?? (SERVIDO_PELA_API ? '' : (lerStorage(BASE_STORAGE) || SERVIDORES.local.url));

const CHAVE_STORAGE = 'hookfishing.apiKey';

const estado = {
    chave: null,
    usuario: null,
    aba: 'peixes',
    pagina: 0,
    tamanho: 10,
    ordenar: null,
    busca: null,
    versao: '1',
    pagina_meta: null,
    itens: [],
    modal: null,
    timer429: null
};

/* ---------------------------------------------------------------------
 * Utilitários
 * ------------------------------------------------------------------- */

const $ = (sel, raiz = document) => raiz.querySelector(sel);
const $$ = (sel, raiz = document) => Array.from(raiz.querySelectorAll(sel));

function esc(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

function uuid() {
    if (window.crypto?.randomUUID) {
        return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = Math.random() * 16 | 0;
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
}

function lerStorage(chave) {
    try { return localStorage.getItem(chave); } catch { return null; }
}

function gravarStorage(chave, valor) {
    try {
        if (valor == null) localStorage.removeItem(chave);
        else localStorage.setItem(chave, valor);
    } catch { /* navegação privada: segue sem lembrar a chave */ }
}

function toast(mensagem, tipo = '', detalhe = '') {
    const el = document.createElement('div');
    el.className = `toast ${tipo ? 'toast--' + tipo : ''}`;
    el.innerHTML = esc(mensagem) + (detalhe ? `<small>${esc(detalhe)}</small>` : '');
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), tipo === 'erro' ? 6500 : 4000);
}

function json(valor) {
    return JSON.stringify(valor, null, 2);
}

/** Verifica, pelas permissões devolvidas em GET /api-keys/me, se o papel pode fazer a operação no recurso. */
function pode(recurso, operacao) {
    return (estado.usuario?.permissoes?.[recurso] || []).includes(operacao);
}

/* ---------------------------------------------------------------------
 * Cliente HTTP
 * ------------------------------------------------------------------- */

/**
 * Faz uma requisição à API e nunca lança exceção: devolve { ok, status, dados, headers }.
 * opcoes: { corpo, headers, semChave, chave, silencioso }
 */
async function api(metodo, caminho, opcoes = {}) {
    const headers = { Accept: 'application/hal+json, application/json', ...(opcoes.headers || {}) };
    const chave = opcoes.chave ?? estado.chave;
    if (!opcoes.semChave && chave) {
        headers['X-API-Key'] = chave;
    }

    let corpo;
    if (opcoes.corpo !== undefined) {
        headers['Content-Type'] = 'application/json';
        corpo = typeof opcoes.corpo === 'string' ? opcoes.corpo : JSON.stringify(opcoes.corpo);
    }

    let resposta;
    const inicio = performance.now();
    try {
        resposta = await fetch(API + caminho, { method: metodo, headers, body: corpo });
    } catch (e) {
        const falha = {
            ok: false, status: 0, headers: new Headers(),
            dados: { mensagem: 'Não foi possível conectar à API em ' + (API || location.origin)
                    + '. Verifique se a aplicação está rodando (e, se o front-end estiver em outra origem, se ela está liberada no CORS).' }
        };
        registrarLog(metodo, caminho, falha, headers);
        capturar(metodo, caminho, headers, corpo, falha, performance.now() - inicio);
        return falha;
    }

    const texto = await resposta.text();
    let dados = null;
    if (texto) {
        try { dados = JSON.parse(texto); } catch { dados = { mensagem: texto }; }
    }

    const resultado = { ok: resposta.ok, status: resposta.status, dados, headers: resposta.headers };
    // A barra e o aviso de 429 mostram só o limite da chave do usuário (os testes também usam chaves de demonstração)
    const daSessao = opcoes.semChave || !chave || chave === estado.chave;
    if (daSessao) {
        atualizarRateLimit(resposta.headers);
    }
    registrarLog(metodo, caminho, resultado, headers);
    capturar(metodo, caminho, headers, corpo, resultado, performance.now() - inicio);

    if (resposta.status === 429 && daSessao) {
        mostrarAviso429(resposta);
    }
    // A chave da sessão deixou de valer (trocada em outro lugar ou desativada): volta para o login com o motivo
    if (resposta.status === 401 && estado.usuario && !opcoes.semChave && chave === estado.chave) {
        sessaoEncerrada(dados?.mensagem);
    }
    return resultado;
}

/* Durante a execução de um teste, guarda cada requisição feita para exibir no detalhe do cenário. */
const HEADERS_CAPTURADOS = ['Content-Type', 'Location', 'Retry-After', 'X-API-Version', 'X-RateLimit-Limit',
    'X-RateLimit-Remaining', 'X-RateLimit-Reset', 'X-RateLimit-Policy', 'X-RateLimit-Blocked-Until',
    'X-Idempotency-Key', 'X-Idempotent-Replayed', 'Access-Control-Allow-Origin'];

function capturar(metodo, caminho, headersEnviados, corpoEnviado, resultado, ms) {
    if (!estado.captura) return;
    const enviados = { ...headersEnviados };
    if (enviados['X-API-Key']) {
        enviados['X-API-Key'] = enviados['X-API-Key'].slice(0, 8) + '••••';
    }
    const recebidos = {};
    HEADERS_CAPTURADOS.forEach(nome => {
        const valor = resultado.headers?.get?.(nome);
        if (valor) recebidos[nome] = valor;
    });
    estado.captura.push({
        metodo, caminho, ms: Math.round(ms), status: resultado.status,
        enviados, corpoEnviado: corpoEnviado ?? null, recebidos, dados: resultado.dados
    });
}

function mensagemErro(resultado) {
    const d = resultado.dados || {};
    return d.mensagem || `Erro HTTP ${resultado.status}`;
}

/* ---------------------------------------------------------------------
 * Rate limit (X-RateLimit-* e Retry-After)
 * ------------------------------------------------------------------- */

function atualizarRateLimit(headers) {
    const limite = Number(headers.get('X-RateLimit-Limit'));
    const restantes = Number(headers.get('X-RateLimit-Remaining'));
    if (!limite || Number.isNaN(restantes)) {
        return;
    }
    const proporcao = Math.max(0, Math.min(1, restantes / limite));
    const barra = $('#limite-preenchido');
    barra.style.width = (proporcao * 100) + '%';
    barra.style.background = proporcao > .4 ? '#46d3b0' : proporcao > .15 ? '#f2b84b' : '#ff6b5a';
    const reset = headers.get('X-RateLimit-Reset');
    const politica = headers.get('X-RateLimit-Policy');
    $('#limite-texto').textContent = `${restantes}/${limite}`;
    $('.limite').title = `Rate limiting (token bucket): ${restantes} de ${limite} fichas restantes. O balde enche em ${reset}s.`
        + (politica ? `\nRegra: ${politica}` : '');
}

/** Aviso de 429: contagem regressiva do Retry-After; se a chave foi bloqueada, mostra até quando. */
function mostrarAviso429(resposta) {
    const aviso = $('#aviso-429');
    clearInterval(estado.timer429);
    let restante = Number(resposta.headers.get('Retry-After') || 60);
    const bloqueadoAte = resposta.headers.get('X-RateLimit-Blocked-Until');
    const mensagem = resposta.dados?.mensagem || '';
    const atualizar = () => {
        aviso.hidden = false;
        aviso.classList.toggle('aviso-429--bloqueio', !!bloqueadoAte);
        aviso.innerHTML = bloqueadoAte
            ? `<b>Acesso bloqueado até ${esc(new Date(bloqueadoAte).toLocaleTimeString('pt-BR'))}</b> `
                + `(faltam ${restante}s). Você continuou enviando requisições depois do limite. `
                + `Aguarde: novas tentativas durante o bloqueio não são atendidas.`
            : `<b>Limite de requisições excedido (HTTP 429).</b> Retry-After: tente novamente em ${restante}s.`
                + (mensagem ? `<span class="aviso-429__detalhe">${esc(mensagem)}</span>` : '');
        if (restante-- <= 0) {
            clearInterval(estado.timer429);
            aviso.hidden = true;
        }
    };
    atualizar();
    estado.timer429 = setInterval(atualizar, 1000);
}

/* ---------------------------------------------------------------------
 * Log de requisições (painel lateral)
 * ------------------------------------------------------------------- */

const HEADERS_LOG = ['X-API-Version', 'X-RateLimit-Remaining', 'Retry-After', 'X-RateLimit-Blocked-Until', 'X-Idempotent-Replayed'];

function registrarLog(metodo, caminho, resultado, headersEnviados) {
    const lista = $('#log-lista');
    if (!lista) return;

    const item = document.createElement('li');
    item.className = 'log__item';
    const grupo = String(resultado.status).charAt(0);

    const enviados = [];
    if (headersEnviados['X-API-Version']) enviados.push(['→ X-API-Version', headersEnviados['X-API-Version']]);
    if (headersEnviados['X-Idempotency-Key']) enviados.push(['→ X-Idempotency-Key', headersEnviados['X-Idempotency-Key'].slice(0, 18) + '…']);
    if (!headersEnviados['X-API-Key']) enviados.push(['→ X-API-Key', '(ausente)']);

    const recebidos = HEADERS_LOG
        .map(h => [h, resultado.headers.get(h)])
        .filter(([, v]) => v != null)
        .map(([h, v]) => ['← ' + h, v]);

    item.innerHTML = `
        <div class="log__linha">
            <span class="log__metodo">${esc(metodo)}</span>
            <span class="log__caminho" title="${esc(caminho)}">${esc(caminho)}</span>
            <span class="log__status log__status--${grupo}">${resultado.status || 'ERRO'}</span>
        </div>
        ${enviados.length || recebidos.length ? `<div class="log__headers">${
            [...enviados, ...recebidos].map(([h, v]) => `<span>${esc(h)}: <b>${esc(v)}</b></span>`).join('')
        }</div>` : ''}`;

    lista.prepend(item);
    while (lista.children.length > 60) {
        lista.lastChild.remove();
    }
}

/* ---------------------------------------------------------------------
 * Definição das telas de cada recurso
 * ------------------------------------------------------------------- */

const FONTES = {
    especies:     { rota: '/especies',     rel: 'especies',     rotulo: e => `${e.nome} (${e.familia})` },
    aquarios:     { rota: '/aquarios',     rel: 'aquarios',     rotulo: a => `${a.nome} · ${a.capacidadeLitros} L` },
    cuidadores:   { rota: '/cuidadores',   rel: 'cuidadores',   rotulo: c => `${c.nome} · ${c.email}` },
    alimentacoes: { rota: '/alimentacoes', rel: 'alimentacoes', rotulo: a => `${a.nome} (${a.tipo})` }
};

const TIPOS_ALIMENTACAO = ['RACAO', 'VIVA', 'VEGETAL', 'CONGELADA'];
const PAPEIS = ['VISITANTE', 'CUIDADOR', 'BIOLOGO', 'GERENTE', 'ADMIN'];
const OPERACOES = { CONSULTAR: 'consultar', CADASTRAR: 'cadastrar', EDITAR: 'editar', EXCLUIR: 'excluir' };

const etiqueta = (texto, cinza = false) => `<span class="etiqueta${cinza ? ' etiqueta--cinza' : ''}">${esc(texto)}</span>`;

const ENTIDADES = {
    peixes: {
        titulo: 'Peixes', singular: 'Peixe', rota: '/peixes', rel: 'peixes', versionado: true,
        descricao: 'Cada peixe pertence a uma espécie e a um aquário (One-to-Many) e recebe várias alimentações (Many-to-Many). '
            + 'Use os botões v1/v2 para ver o versionamento por header.',
        ordenar: [['id,asc', 'ID'], ['nome,asc', 'Nome (A→Z)'], ['nome,desc', 'Nome (Z→A)'],
            ['idade,asc', 'Idade ↑'], ['idade,desc', 'Idade ↓'], ['tamanho,desc', 'Tamanho ↓']],
        busca: { param: 'nome', rotulo: 'Nome contém', tipo: 'text', exemplo: 'nemo' },
        colunas(versao) {
            if (versao === '2') {
                return [
                    ['ID', p => `<span class="id">#${p.id}</span>`],
                    ['Nome', p => `<b>${esc(p.nome)}</b>`],
                    ['Idade', p => `${p.idade} ${p.idade === 1 ? 'ano' : 'anos'} ${etiqueta(p.faixaEtaria, true)}`],
                    ['Tamanho', p => esc(p.tamanhoFormatado)],
                    ['Espécie', p => p.especie ? `${esc(p.especie.nome)}<br><small class="id">${esc(p.especie.familia)}</small>` : '–'],
                    ['Aquário', p => p.aquario ? esc(p.aquario.nome) : '–'],
                    ['Alimentações', p => (p.alimentacoes || []).map(a => etiqueta(a.nome)).join('') || '–']
                ];
            }
            return [
                ['ID', p => `<span class="id">#${p.id}</span>`],
                ['Nome', p => `<b>${esc(p.nome)}</b>`],
                ['Idade', p => p.idade],
                ['Tamanho (cm)', p => p.tamanho],
                ['especieId', p => `<span class="id">${p.especieId ?? '–'}</span>`],
                ['aquarioId', p => `<span class="id">${p.aquarioId ?? '–'}</span>`],
                ['alimentacaoIds', p => `<span class="id">[${(p.alimentacaoIds || []).join(', ')}]</span>`]
            ];
        },
        campos: [
            { nome: 'nome', rotulo: 'Nome', tipo: 'text', obrigatorio: true, min: 2, max: 100, exemplo: 'Coral' },
            { nome: 'idade', rotulo: 'Idade (anos)', tipo: 'number', obrigatorio: true, minValor: 0, passo: 1, exemplo: '1' },
            { nome: 'tamanho', rotulo: 'Tamanho (cm)', tipo: 'number', obrigatorio: true, minValor: 0.1, passo: 0.1, exemplo: '7.0' },
            { nome: 'especieId', rotulo: 'Espécie', tipo: 'relacao', fonte: 'especies', obrigatorio: true },
            { nome: 'aquarioId', rotulo: 'Aquário', tipo: 'relacao', fonte: 'aquarios', obrigatorio: true },
            { nome: 'alimentacaoIds', rotulo: 'Alimentações', tipo: 'multi', fonte: 'alimentacoes' }
        ],
        paraFormulario: p => ({
            nome: p.nome, idade: p.idade, tamanho: p.tamanho ?? p.tamanhoCm,
            especieId: p.especieId ?? p.especie?.id,
            aquarioId: p.aquarioId ?? p.aquario?.id,
            alimentacaoIds: p.alimentacaoIds ?? (p.alimentacoes || []).map(a => a.id)
        })
    },

    especies: {
        titulo: 'Espécies', singular: 'Espécie', rota: '/especies', rel: 'especies',
        descricao: 'Uma espécie possui vários peixes (One-to-Many). Não é possível excluir uma espécie que ainda tenha peixes.',
        ordenar: [['id,asc', 'ID'], ['nome,asc', 'Nome (A→Z)'], ['familia,asc', 'Família']],
        busca: { param: 'familia', rotulo: 'Família contém', tipo: 'text', exemplo: 'Pomacentridae' },
        colunas: () => [
            ['ID', e => `<span class="id">#${e.id}</span>`],
            ['Nome', e => `<b>${esc(e.nome)}</b>`],
            ['Família', e => `<i>${esc(e.familia)}</i>`],
            ['Peixes', e => e.quantidadePeixes]
        ],
        campos: [
            { nome: 'nome', rotulo: 'Nome popular', tipo: 'text', obrigatorio: true, min: 3, max: 100, exemplo: 'Peixe-anjo' },
            { nome: 'familia', rotulo: 'Família', tipo: 'text', obrigatorio: true, min: 3, max: 100, exemplo: 'Pomacanthidae' }
        ],
        paraFormulario: e => ({ nome: e.nome, familia: e.familia })
    },

    aquarios: {
        titulo: 'Aquários', singular: 'Aquário', rota: '/aquarios', rel: 'aquarios',
        descricao: 'Cada aquário tem no máximo um cuidador (One-to-One) e abriga vários peixes (One-to-Many).',
        ordenar: [['id,asc', 'ID'], ['nome,asc', 'Nome (A→Z)'], ['capacidadeLitros,desc', 'Capacidade ↓'], ['capacidadeLitros,asc', 'Capacidade ↑']],
        busca: { param: 'capacidadeMinima', rotulo: 'Capacidade mínima (L)', tipo: 'number', exemplo: '200' },
        colunas: () => [
            ['ID', a => `<span class="id">#${a.id}</span>`],
            ['Nome', a => `<b>${esc(a.nome)}</b>`],
            ['Capacidade', a => `${a.capacidadeLitros} L`],
            ['Cuidador', a => a.cuidador ? esc(a.cuidador.nome) : etiqueta('sem cuidador', true)],
            ['Peixes', a => a.quantidadePeixes]
        ],
        campos: [
            { nome: 'nome', rotulo: 'Nome', tipo: 'text', obrigatorio: true, min: 3, max: 100, exemplo: 'Aquário Quarentena' },
            { nome: 'capacidadeLitros', rotulo: 'Capacidade (litros)', tipo: 'number', obrigatorio: true, minValor: 1, passo: 1, exemplo: '80' },
            { nome: 'cuidadorId', rotulo: 'Cuidador', tipo: 'relacao', fonte: 'cuidadores', obrigatorio: false,
                ajuda: 'Opcional. Cada cuidador só pode cuidar de um aquário.' }
        ],
        paraFormulario: a => ({ nome: a.nome, capacidadeLitros: a.capacidadeLitros, cuidadorId: a.cuidador?.id })
    },

    cuidadores: {
        titulo: 'Cuidadores', singular: 'Cuidador', rota: '/cuidadores', rel: 'cuidadores',
        descricao: 'Pessoas responsáveis pelos aquários. O e-mail é único e cada cuidador cuida de no máximo um aquário.',
        ordenar: [['id,asc', 'ID'], ['nome,asc', 'Nome (A→Z)'], ['email,asc', 'E-mail']],
        busca: { param: 'email', rotulo: 'E-mail exato', tipo: 'email', exemplo: 'joao@email.com', unico: true },
        colunas: () => [
            ['ID', c => `<span class="id">#${c.id}</span>`],
            ['Nome', c => `<b>${esc(c.nome)}</b>`],
            ['E-mail', c => esc(c.email)],
            ['Aquário', c => c.aquario ? esc(c.aquario.nome) : etiqueta('livre', true)]
        ],
        campos: [
            { nome: 'nome', rotulo: 'Nome', tipo: 'text', obrigatorio: true, min: 3, max: 100, exemplo: 'Marina Costa' },
            { nome: 'email', rotulo: 'E-mail', tipo: 'email', obrigatorio: true, exemplo: 'marina.costa@email.com' }
        ],
        paraFormulario: c => ({ nome: c.nome, email: c.email })
    },

    alimentacoes: {
        titulo: 'Alimentações', singular: 'Alimentação', rota: '/alimentacoes', rel: 'alimentacoes',
        descricao: 'Relacionamento Many-to-Many com peixes. O tipo é o enum TipoAlimentacao.',
        ordenar: [['id,asc', 'ID'], ['nome,asc', 'Nome (A→Z)'], ['tipo,asc', 'Tipo']],
        busca: { param: 'tipo', rotulo: 'Tipo', tipo: 'enum', opcoes: TIPOS_ALIMENTACAO },
        colunas: () => [
            ['ID', a => `<span class="id">#${a.id}</span>`],
            ['Nome', a => `<b>${esc(a.nome)}</b>`],
            ['Tipo', a => etiqueta(a.tipo)],
            ['Peixes', a => a.quantidadePeixes]
        ],
        campos: [
            { nome: 'nome', rotulo: 'Nome', tipo: 'text', obrigatorio: true, min: 3, max: 100, exemplo: 'Krill Congelado' },
            { nome: 'tipo', rotulo: 'Tipo', tipo: 'enum', opcoes: TIPOS_ALIMENTACAO, obrigatorio: true }
        ],
        paraFormulario: a => ({ nome: a.nome, tipo: a.tipo })
    },

    'api-keys': {
        titulo: 'API Keys', singular: 'API Key', rota: '/api-keys', rel: 'apiKeys', somenteAdmin: true,
        descricao: 'Geração e gerenciamento das chaves usadas no header X-API-Key (apenas ADMIN). '
            + 'O valor completo da chave aparece uma única vez, na criação ou regeneração.',
        ordenar: [['id,asc', 'ID'], ['nome,asc', 'Nome (A→Z)'], ['papel,asc', 'Papel']],
        colunas: () => [
            ['ID', k => `<span class="id">#${k.id}</span>`],
            ['Dono', k => `<b>${esc(k.nome)}</b><br><small class="id">${esc(k.email)}</small>`],
            ['Papel', k => `<span class="selo selo--${esc(k.papel)}">${esc(k.papel)}</span>`],
            ['Prefixo', k => `<code>${esc(k.prefixo)}…</code>`],
            ['Status', k => k.ativa ? etiqueta('ativa') : etiqueta('desativada', true)],
            ['Último uso', k => k.ultimoUsoEm ? new Date(k.ultimoUsoEm).toLocaleString('pt-BR') : '–']
        ],
        campos: [
            { nome: 'nome', rotulo: 'Dono (usuário ou aplicação)', tipo: 'text', obrigatorio: true, min: 3, max: 100, exemplo: 'Front-end HookFishing' },
            { nome: 'email', rotulo: 'E-mail do responsável', tipo: 'email', obrigatorio: true, exemplo: 'frontend@hookfishing.com' },
            { nome: 'papel', rotulo: 'Papel', tipo: 'enum', opcoes: PAPEIS, obrigatorio: true,
                ajuda: 'VISITANTE: só consulta · CUIDADOR: cadastra/edita peixes e alimentações · BIOLOGO: + espécies e exclusões · '
                    + 'GERENTE: + aquários e cuidadores · ADMIN: tudo + API Keys' },
            { nome: 'ativa', rotulo: 'Chave ativa', tipo: 'boolean', somenteEdicao: true,
                ajuda: 'Desmarcada, a chave passa a receber 401.' }
        ],
        paraFormulario: k => ({ nome: k.nome, email: k.email, papel: k.papel, ativa: k.ativa }),
        acaoExtra: { rotulo: 'Regenerar', executar: regenerarChave }
    }
};

/* ---------------------------------------------------------------------
 * Login e cadastro (e-mail e senha)
 *
 * POST /auth/login devolve a API Key do usuário; a partir daí todas as
 * requisições enviam essa chave no header X-API-Key.
 * ------------------------------------------------------------------- */

function mostrarAcesso(tela) {
    const cadastro = tela === 'cadastro';
    $('#form-login').hidden = cadastro;
    $('#form-cadastro').hidden = !cadastro;
    $('#login-erro').hidden = true;
    $('#cadastro-erro').hidden = true;
    if (cadastro) {
        // Chave de idempotência do cadastro: a mesma enquanto o formulário estiver aberto
        estado.chaveCadastro = uuid();
    }
    history.replaceState(null, '', cadastro ? '#cadastro' : location.pathname + location.search);
    (cadastro ? $('#cadastro-nome') : $('#login-email')).focus();
}

function mostrarErroAcesso(seletor, mensagem, campos = []) {
    const caixa = $(seletor);
    caixa.hidden = false;
    caixa.innerHTML = esc(mensagem)
        + (campos.length ? `<ul>${campos.map(c => `<li>${esc(c.mensagem)}</li>`).join('')}</ul>` : '');
}

/** Confere uma chave já obtida (login atual ou sessão salva) e abre a aplicação. */
async function abrirSessao(chave) {
    const resultado = await api('GET', '/api-keys/me', { chave });
    if (!resultado.ok) {
        return false;
    }
    estado.chave = chave;
    estado.usuario = resultado.dados;
    gravarStorage(CHAVE_STORAGE, chave);
    abrirApp();
    return true;
}

async function entrar(email, senha) {
    $('#login-erro').hidden = true;
    if (!email || !senha) {
        mostrarErroAcesso('#login-erro', 'Informe o e-mail e a senha.');
        return false;
    }

    const botao = $('#form-login button[type=submit]');
    botao.disabled = true;
    const resultado = await api('POST', '/auth/login', { semChave: true, corpo: { email, senha } });
    botao.disabled = false;

    if (!resultado.ok) {
        mostrarErroAcesso('#login-erro', mensagemErro(resultado));
        return false;
    }
    return abrirSessao(resultado.dados.apiKey);
}

async function cadastrar() {
    const nome = $('#cadastro-nome').value.trim();
    const email = $('#cadastro-email').value.trim();
    const senha = $('#cadastro-senha').value;
    const confirmacao = $('#cadastro-confirmacao').value;
    $('#cadastro-erro').hidden = true;

    if (!nome || !email || !senha) {
        mostrarErroAcesso('#cadastro-erro', 'Preencha nome, e-mail e senha.');
        return;
    }
    if (senha !== confirmacao) {
        mostrarErroAcesso('#cadastro-erro', 'As senhas não conferem.');
        return;
    }

    const botao = $('#form-cadastro button[type=submit]');
    botao.disabled = true;
    const resultado = await api('POST', '/auth/cadastro', {
        semChave: true,
        corpo: { nome, email, senha },
        headers: { 'X-Idempotency-Key': estado.chaveCadastro }
    });
    botao.disabled = false;

    if (!resultado.ok) {
        mostrarErroAcesso('#cadastro-erro', mensagemErro(resultado), resultado.dados?.campos || []);
        return;
    }

    toast('Conta criada.', 'ok');
    $('#form-cadastro').reset();
    if (await entrar(email, senha)) {
        history.replaceState(null, '', location.pathname + location.search);
    } else {
        mostrarAcesso('login');
        $('#login-email').value = email;
    }
}

function sessaoEncerrada(motivo) {
    sair();
    mostrarErroAcesso('#login-erro', 'Sua sessão foi encerrada: a API Key usada por este navegador não é mais aceita '
        + '(ela foi trocada ou desativada). Entre novamente.' + (motivo ? ` Detalhe: ${motivo}` : ''));
}

function sair() {
    estado.chave = null;
    estado.usuario = null;
    estado.testeIdem = null;
    gravarStorage(CHAVE_STORAGE, null);
    limparAviso429();
    $('#log-lista').innerHTML = '';
    $('#limite-texto').textContent = '–';
    $('#limite-preenchido').style.width = '100%';
    $('#tela-app').hidden = true;
    $('#tela-login').hidden = false;
    $('#form-login').reset();
    mostrarAcesso('login');
}

function limparAviso429() {
    clearInterval(estado.timer429);
    $('#aviso-429').hidden = true;
}

function abrirApp() {
    limparAviso429();
    $('#tela-login').hidden = true;
    $('#tela-app').hidden = false;
    $('#usuario-nome').textContent = estado.usuario.nome;
    const selo = $('#usuario-papel');
    selo.textContent = estado.usuario.papel;
    selo.className = `selo selo--${estado.usuario.papel}`;
    atualizarSeloPlano();
    montarAbas();
    const abaValida = (estado.aba in ENTIDADES && pode(estado.aba, 'CONSULTAR')) || ['planos', 'conta', 'testes'].includes(estado.aba);
    selecionarAba(abaValida ? estado.aba : 'peixes');
}

function atualizarSeloPlano() {
    const plano = estado.usuario?.plano || 'GRATUITO';
    const selo = $('#usuario-plano');
    selo.textContent = NOMES_PLANOS[plano] || plano;
    selo.className = `selo selo-plano selo-plano--${plano}`;
    selo.title = `Plano ${NOMES_PLANOS[plano] || plano}: veja limites e preços na aba Planos`;
}

/* ---------------------------------------------------------------------
 * Abas
 * ------------------------------------------------------------------- */

function montarAbas() {
    const abas = $('#abas');
    abas.innerHTML = '';
    Object.entries(ENTIDADES).forEach(([id, def]) => {
        if (!pode(id, 'CONSULTAR')) return;
        abas.insertAdjacentHTML('beforeend',
            `<button type="button" class="aba" data-aba="${id}">${esc(def.titulo)}</button>`);
    });
    abas.insertAdjacentHTML('beforeend',
        `<button type="button" class="aba" data-aba="planos">Planos</button>`
        + `<button type="button" class="aba" data-aba="conta">Minha conta</button>`
        + `<button type="button" class="aba aba--destaque" data-aba="testes">Testes</button>`);
}

function selecionarAba(id) {
    estado.aba = id;
    $$('.aba').forEach(b => b.classList.toggle('ativa', b.dataset.aba === id));

    $('#painel-planos').hidden = id !== 'planos';
    $('#painel-conta').hidden = id !== 'conta';
    if (id === 'conta') {
        $('#painel-entidade').hidden = true;
        $('#painel-testes').hidden = true;
        montarConta();
        return;
    }
    if (id === 'testes') {
        $('#painel-entidade').hidden = true;
        $('#painel-testes').hidden = false;
        montarTestes();
        return;
    }
    if (id === 'planos') {
        $('#painel-entidade').hidden = true;
        $('#painel-testes').hidden = true;
        montarPlanos();
        return;
    }

    const def = ENTIDADES[id];
    estado.pagina = 0;
    estado.busca = null;
    estado.ordenar = def.ordenar[0][0];

    $('#painel-testes').hidden = true;
    $('#painel-entidade').hidden = false;
    $('#entidade-titulo').textContent = def.titulo;
    $('#entidade-descricao').textContent = def.descricao;
    $('#btn-novo').hidden = !pode(id, 'CADASTRAR');
    $('#versao').hidden = !def.versionado;

    $('#ordenar').innerHTML = def.ordenar.map(([v, r]) => `<option value="${v}">${esc(r)}</option>`).join('');

    const formBusca = $('#form-busca');
    formBusca.hidden = !def.busca;
    $('#btn-limpar').hidden = true;
    if (def.busca) {
        $('#busca-rotulo').textContent = def.busca.rotulo;
        $('#busca-campo').innerHTML = def.busca.tipo === 'enum'
            ? `<select id="busca-valor">${def.busca.opcoes.map(o => `<option>${o}</option>`).join('')}</select>`
            : `<input id="busca-valor" type="${def.busca.tipo}" placeholder="ex.: ${esc(def.busca.exemplo)}" ${def.busca.tipo === 'number' ? 'min="1"' : ''}>`;
    }

    carregar();
}

/* ---------------------------------------------------------------------
 * Listagem paginada
 * ------------------------------------------------------------------- */

async function carregar() {
    const def = ENTIDADES[estado.aba];
    const params = new URLSearchParams({ page: estado.pagina, size: estado.tamanho, sort: estado.ordenar });
    let caminho = def.rota;
    if (estado.busca) {
        caminho += '/buscar';
        params.set(def.busca.param, estado.busca);
    }
    const headers = def.versionado ? { 'X-API-Version': estado.versao } : {};

    $('#tabela-corpo').innerHTML = `<tr><td class="vazio" colspan="99">Carregando…</td></tr>`;

    // A busca por e-mail de cuidador devolve um único recurso (não paginado)
    const unico = estado.busca && def.busca?.unico;
    const resultado = await api('GET', unico ? `${caminho}?${def.busca.param}=${encodeURIComponent(estado.busca)}` : `${caminho}?${params}`, { headers });

    if (!resultado.ok) {
        estado.itens = [];
        estado.pagina_meta = null;
        renderizarTabela(def, resultado.status === 404 && unico ? 'Nenhum registro encontrado.' : mensagemErro(resultado));
        if (resultado.status !== 404) toast(mensagemErro(resultado), 'erro', `HTTP ${resultado.status}`);
        return;
    }

    if (unico) {
        estado.itens = [resultado.dados];
        estado.pagina_meta = { number: 0, size: 1, totalElements: 1, totalPages: 1 };
    } else {
        estado.itens = resultado.dados?._embedded?.[def.rel] ?? [];
        estado.pagina_meta = resultado.dados?.page ?? null;
    }
    renderizarTabela(def);
}

function renderizarTabela(def, mensagemVazia) {
    const colunas = def.colunas(estado.versao);
    const recurso = estado.aba;
    const podeEditar = pode(recurso, 'EDITAR');
    const podeExcluir = pode(recurso, 'EXCLUIR');
    const mostraAcoes = podeEditar || podeExcluir;

    $('#tabela-cabecalho').innerHTML = `<tr>${colunas.map(([t]) => `<th>${esc(t)}</th>`).join('')}${mostraAcoes ? '<th></th>' : ''}</tr>`;

    if (!estado.itens.length) {
        $('#tabela-corpo').innerHTML = `<tr><td class="vazio" colspan="99">${esc(mensagemVazia || 'Nenhum registro encontrado.')}</td></tr>`;
    } else {
        $('#tabela-corpo').innerHTML = estado.itens.map((item, i) => `
            <tr>
                ${colunas.map(([, f]) => `<td>${f(item) ?? ''}</td>`).join('')}
                ${mostraAcoes ? `<td class="acoes">
                    ${def.acaoExtra && podeEditar ? `<button class="btn btn--pequeno" data-extra="${i}" type="button">${esc(def.acaoExtra.rotulo)}</button>` : ''}
                    ${podeEditar ? `<button class="btn btn--pequeno" data-editar="${i}" type="button">Editar</button>` : ''}
                    ${podeExcluir ? `<button class="btn btn--pequeno btn--perigo" data-excluir="${i}" type="button">Excluir</button>` : ''}
                </td>` : ''}
            </tr>`).join('');
    }

    const meta = estado.pagina_meta;
    const botoes = $$('[data-pagina]');
    if (!meta) {
        $('#paginacao-info').textContent = '';
        botoes.forEach(b => b.disabled = true);
        return;
    }
    const total = meta.totalPages || 0;
    $('#paginacao-info').textContent = total
        ? `Página ${meta.number + 1} de ${total} · ${meta.totalElements} registro(s)`
        : '0 registros';
    botoes.forEach(b => {
        const alvo = b.dataset.pagina;
        b.disabled = (alvo === 'first' || alvo === 'prev') ? meta.number <= 0 : meta.number >= total - 1;
    });
}

function irParaPagina(alvo) {
    const meta = estado.pagina_meta;
    if (!meta) return;
    const total = meta.totalPages || 1;
    estado.pagina = { first: 0, prev: meta.number - 1, next: meta.number + 1, last: total - 1 }[alvo];
    carregar();
}

/* ---------------------------------------------------------------------
 * Formulário (criar / editar)
 * ------------------------------------------------------------------- */

async function carregarFonte(nome) {
    const fonte = FONTES[nome];
    const r = await api('GET', `${fonte.rota}?size=50&sort=nome,asc`);
    return r.ok ? (r.dados?._embedded?.[fonte.rel] ?? []) : [];
}

async function abrirFormulario(item) {
    const def = ENTIDADES[estado.aba];
    const editando = !!item;
    const valores = editando ? def.paraFormulario(item) : {};

    const fontes = {};
    for (const campo of def.campos) {
        if ((campo.tipo === 'relacao' || campo.tipo === 'multi') && !fontes[campo.fonte]) {
            fontes[campo.fonte] = await carregarFonte(campo.fonte);
        }
    }

    // Uma nova chave de idempotência para cada formulário de cadastro.
    // Se o usuário clicar em "Salvar" de novo (ex.: rede lenta), a mesma chave é reenviada e nada é duplicado.
    estado.modal = { def, item, editando, chaveIdempotencia: editando ? null : uuid() };

    $('#modal-titulo').textContent = `${editando ? 'Editar' : 'Novo'}: ${def.singular}`
        + (editando ? ` #${item.id}` : '');
    $('#modal-erro').hidden = true;
    $('#modal-idem').innerHTML = editando ? '' : `X-Idempotency-Key:<br>${esc(estado.modal.chaveIdempotencia)}`;
    $('#modal-salvar').hidden = false;
    $('#modal-salvar').textContent = 'Salvar';
    $$('#modal .modal__rodape [data-fechar]').forEach(btn => btn.textContent = 'Cancelar');

    $('#modal-corpo').innerHTML = def.campos
        .filter(c => !(c.somenteEdicao && !editando))
        .map(c => renderizarCampo(c, valores[c.nome], fontes))
        .join('');

    $('#modal').showModal();
    $('#modal-corpo input, #modal-corpo select')?.focus();
}

function renderizarCampo(campo, valor, fontes) {
    const id = `campo-${campo.nome}`;
    const req = campo.obrigatorio ? 'required' : '';
    const ajuda = campo.ajuda ? `<span class="ajuda">${esc(campo.ajuda)}</span>` : '';
    let controle;

    switch (campo.tipo) {
        case 'relacao': {
            const opcoes = fontes[campo.fonte] || [];
            controle = `<select id="${id}" name="${campo.nome}" ${req}>
                ${campo.obrigatorio ? '<option value="">Selecione…</option>' : '<option value="">(nenhum)</option>'}
                ${opcoes.map(o => `<option value="${o.id}" ${Number(valor) === o.id ? 'selected' : ''}>#${o.id} · ${esc(FONTES[campo.fonte].rotulo(o))}</option>`).join('')}
            </select>`;
            break;
        }
        case 'multi': {
            const opcoes = fontes[campo.fonte] || [];
            const marcados = new Set((valor || []).map(Number));
            controle = `<div class="opcoes-multi" id="${id}">${opcoes.map(o => `
                <label><input type="checkbox" name="${campo.nome}" value="${o.id}" ${marcados.has(o.id) ? 'checked' : ''}> ${esc(FONTES[campo.fonte].rotulo(o))}</label>`).join('')
                || '<span class="ajuda">Nenhuma cadastrada.</span>'}</div>`;
            break;
        }
        case 'enum':
            controle = `<select id="${id}" name="${campo.nome}" ${req}>
                ${valor ? '' : '<option value="">Selecione…</option>'}
                ${campo.opcoes.map(o => `<option ${o === valor ? 'selected' : ''}>${o}</option>`).join('')}
            </select>`;
            break;
        case 'boolean':
            return `<div class="campo" data-campo="${campo.nome}">
                <label class="check"><input type="checkbox" id="${id}" name="${campo.nome}" ${valor ? 'checked' : ''}> ${esc(campo.rotulo)}</label>
                ${ajuda}<span class="campo__erro"></span></div>`;
        default:
            controle = `<input id="${id}" name="${campo.nome}" type="${campo.tipo}" ${req}
                value="${esc(valor ?? '')}" placeholder="ex.: ${esc(campo.exemplo ?? '')}"
                ${campo.min ? `minlength="${campo.min}"` : ''} ${campo.max ? `maxlength="${campo.max}"` : ''}
                ${campo.minValor != null ? `min="${campo.minValor}"` : ''} ${campo.passo ? `step="${campo.passo}"` : ''}>`;
    }

    return `<div class="campo" data-campo="${campo.nome}">
        <label for="${id}">${esc(campo.rotulo)}${campo.obrigatorio ? ' *' : ''}</label>
        ${controle}${ajuda}<span class="campo__erro"></span>
    </div>`;
}

function lerFormulario(def, editando) {
    const corpo = {};
    for (const campo of def.campos) {
        if (campo.somenteEdicao && !editando) continue;
        const el = $(`#campo-${campo.nome}`);
        switch (campo.tipo) {
            case 'multi':
                corpo[campo.nome] = $$(`input[name="${campo.nome}"]:checked`).map(i => Number(i.value));
                break;
            case 'relacao':
                corpo[campo.nome] = el.value ? Number(el.value) : null;
                break;
            case 'number':
                corpo[campo.nome] = el.value === '' ? null : Number(el.value);
                break;
            case 'boolean':
                corpo[campo.nome] = el.checked;
                break;
            case 'enum':
                corpo[campo.nome] = el.value || null;
                break;
            default:
                corpo[campo.nome] = el.value;
        }
    }
    return corpo;
}

async function salvarFormulario(evento) {
    evento.preventDefault();
    const { def, item, editando, chaveIdempotencia } = estado.modal;
    const corpo = lerFormulario(def, editando);
    const botao = $('#modal-salvar');
    botao.disabled = true;
    botao.textContent = 'Salvando…';

    const resultado = editando
        ? await api('PUT', `${def.rota}/${item.id}`, { corpo })
        : await api('POST', def.rota, { corpo, headers: { 'X-Idempotency-Key': chaveIdempotencia } });

    botao.disabled = false;
    botao.textContent = 'Salvar';

    if (!resultado.ok) {
        mostrarErroFormulario(resultado);
        return;
    }

    if (def.rel === 'apiKeys' && resultado.dados?.chave) {
        mostrarChaveGerada(resultado.dados);
    } else {
        $('#modal').close();
    }

    const repetida = resultado.headers.get('X-Idempotent-Replayed') === 'true';
    if (repetida) {
        toast('Requisição repetida: a API devolveu o registro já criado e nada foi duplicado.', 'alerta',
            `X-Idempotent-Replayed: true · id ${resultado.dados?.id}`);
    } else {
        toast(editando ? 'Alterações salvas.' : `Cadastrado com sucesso (id ${resultado.dados?.id}).`, 'ok',
            `HTTP ${resultado.status}`);
    }
    carregar();
}

function mostrarErroFormulario(resultado) {
    const dados = resultado.dados || {};
    const caixa = $('#modal-erro');
    $$('.campo').forEach(c => {
        c.classList.remove('invalido');
        $('.campo__erro', c).textContent = '';
    });
    (dados.campos || []).forEach(({ campo, mensagem }) => {
        const el = $(`.campo[data-campo="${campo}"]`);
        if (el) {
            el.classList.add('invalido');
            $('.campo__erro', el).textContent = mensagem;
        }
    });
    caixa.hidden = false;
    caixa.innerHTML = `<b>HTTP ${resultado.status}</b> · ${esc(mensagemErro(resultado))}`
        + ((dados.campos || []).length
            ? `<ul>${dados.campos.map(c => `<li><b>${esc(c.campo)}</b>: ${esc(c.mensagem)}</li>`).join('')}</ul>` : '');
}

function mostrarChaveGerada(chave) {
    $('#modal-titulo').textContent = 'Chave gerada';
    $('#modal-erro').hidden = true;
    $('#modal-idem').textContent = '';
    $('#modal-salvar').hidden = true;
    $$('#modal .modal__rodape [data-fechar]').forEach(btn => btn.textContent = 'Fechar');
    $('#modal-corpo').innerHTML = `
        <div class="chave-gerada">
            <p>${esc(chave.nome)} · papel <b>${esc(chave.papel)}</b></p>
            <code id="chave-valor">${esc(chave.chave)}</code>
            <p>${esc(chave.aviso || 'Guarde esta chave: ela não será exibida novamente.')}</p>
        </div>
        <button type="button" class="btn" id="btn-copiar-chave">Copiar chave</button>`;
    $('#btn-copiar-chave').addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(chave.chave);
            toast('Chave copiada.', 'ok');
        } catch {
            toast('Não foi possível copiar automaticamente. Selecione o texto e copie.', 'alerta');
        }
    });
    if (!$('#modal').open) $('#modal').showModal();
}

async function regenerarChave(item) {
    if (!confirm(`Gerar um novo valor para a chave de "${item.nome}"? O valor atual deixará de funcionar.`)) return;
    const resultado = await api('POST', `/api-keys/${item.id}/regenerar`);
    if (!resultado.ok) {
        toast(mensagemErro(resultado), 'erro', `HTTP ${resultado.status}`);
        return;
    }
    estado.modal = { def: ENTIDADES['api-keys'], item, editando: true };
    mostrarChaveGerada(resultado.dados);
    carregar();
}

async function excluir(item) {
    const def = ENTIDADES[estado.aba];
    const nome = item.nome ?? `#${item.id}`;
    if (!confirm(`Excluir "${nome}"?`)) return;

    const resultado = await api('DELETE', `${def.rota}/${item.id}`);
    if (resultado.status === 204) {
        toast(`"${nome}" excluído.`, 'ok', 'HTTP 204 No Content');
        if (estado.itens.length === 1 && estado.pagina > 0) estado.pagina--;
        carregar();
    } else {
        toast(mensagemErro(resultado), 'erro', `HTTP ${resultado.status}`);
    }
}

/* ---------------------------------------------------------------------
 * Painel "Testes"
 *
 * Cada cenário faz requisições reais e compara o status obtido com o esperado.
 * ------------------------------------------------------------------- */

/* ---------------------------------------------------------------------
 * Planos (monetização) e consumo do rate limit
 * ------------------------------------------------------------------- */

const NOMES_PLANOS = { GRATUITO: 'Gratuito', BASICO: 'Básico', PROFISSIONAL: 'Profissional' };

function duracao(segundos) {
    if (segundos < 60) return `${segundos} s`;
    const min = Math.floor(segundos / 60);
    const s = segundos % 60;
    return s ? `${min} min ${s} s` : `${min} min`;
}

async function montarPlanos() {
    const painel = $('#painel-planos');
    painel.innerHTML = '<p class="painel__descricao">Carregando planos...</p>';

    const [planos, limites] = await Promise.all([api('GET', '/planos'), api('GET', '/planos/limites')]);
    if (!planos.ok) {
        painel.innerHTML = `<p class="erro">${esc(mensagemErro(planos))}</p>`;
        return;
    }
    const lista = planos.dados?._embedded?.planos || [];
    const atual = estado.usuario?.plano || 'GRATUITO';
    const l = limites.ok ? limites.dados : null;
    const proporcao = l ? Math.max(0, Math.min(1, l.fichasRestantes / l.capacidade)) : 0;

    painel.innerHTML = `
        <div class="painel__cabecalho">
            <div>
                <h1>Planos</h1>
                <p class="painel__descricao">A API é freemium: toda conta começa no plano Gratuito. Os planos pagos
                    aumentam o limite de requisições da sua API Key. A contratação aqui é uma simulação, sem cobrança.</p>
            </div>
        </div>

        <div class="planos">
            ${lista.map(p => `
                <article class="plano ${p.codigo === atual ? 'plano--atual' : ''}">
                    <header>
                        <h2>${esc(p.nome)}</h2>
                        ${p.codigo === atual ? '<span class="plano__marca">Seu plano</span>' : ''}
                    </header>
                    <p class="plano__preco">${esc(p.preco)}</p>
                    <p class="plano__descricao">${esc(p.descricao)}</p>
                    <dl class="plano__limites">
                        <div><dt>De uma vez</dt><dd>${p.capacidade} requisições</dd></div>
                        <div><dt>Recarga</dt><dd>${p.recargaPorMinuto} por minuto (${esc(p.ritmo)})</dd></div>
                    </dl>
                    <button class="btn ${p.codigo === atual ? '' : 'btn--primario'}" type="button"
                            data-plano="${esc(p.codigo)}" ${p.codigo === atual ? 'disabled' : ''}>
                        ${p.codigo === atual ? 'Plano atual' : (p.precoMensal > 0 ? 'Contratar' : 'Voltar ao Gratuito')}
                    </button>
                </article>`).join('')}
        </div>

        ${l ? `
        <section class="consumo">
            <h2>Seu consumo agora</h2>
            <div class="consumo__barra"><div style="width:${proporcao * 100}%"></div></div>
            <p><b>${l.fichasRestantes} de ${l.capacidade}</b> requisições disponíveis.
                ${l.segundosAteEncher ? `O balde enche de novo em ${duracao(l.segundosAteEncher)}.` : 'O balde está cheio.'}</p>

            <h3>O que acontece quando o limite estoura</h3>
            <ol class="consumo__etapas">
                <li><b>Limite atingido.</b> A API responde <code>429 Too Many Requests</code> com o header
                    <code>Retry-After</code>: quantos segundos esperar pela próxima ficha (no seu plano, ${esc(l.ritmo)}).</li>
                <li><b>Insistiu?</b> ${l.penalidadeTentativas} requisições seguidas recusadas bloqueiam a chave por
                    <b>${duracao(l.penalidadeSegundos)}</b>, mesmo que o balde recarregue. O 429 passa a trazer
                    <code>X-RateLimit-Blocked-Until</code> com o horário de liberação.</li>
                <li><b>Reincidiu?</b> Um novo bloqueio em menos de ${duracao(l.janelaReincidenciaSegundos)} dobra o tempo
                    (${duracao(l.penalidadeSegundos * 2)}, ${duracao(l.penalidadeSegundos * 4)}...), até no máximo
                    <b>${duracao(l.penalidadeMaximaSegundos)}</b>.</li>
                <li><b>Esperou o Retry-After?</b> A contagem de tentativas zera na próxima requisição atendida.</li>
            </ol>
        </section>` : ''}`;
}

async function contratarPlano(plano) {
    const r = await api('PUT', '/planos/assinatura', { corpo: { plano } });
    if (!r.ok) {
        toast('Não foi possível trocar o plano.', 'erro', mensagemErro(r));
        return;
    }
    const me = await api('GET', '/api-keys/me');
    if (me.ok) {
        estado.usuario = me.dados;
    }
    atualizarSeloPlano();
    toast(r.dados?.mensagem || 'Plano alterado.', 'ok', r.dados?.cobranca || '');
    montarPlanos();
}

/* ---------------------------------------------------------------------
 * Minha conta: dados do usuário, API Key e permissões
 * ------------------------------------------------------------------- */

function dataHora(iso) {
    if (!iso) return 'nunca';
    return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

function chaveMascarada(chave) {
    return chave ? chave.slice(0, 11) + '•'.repeat(Math.max(8, chave.length - 11)) : '';
}

async function montarConta() {
    const painel = $('#painel-conta');
    const [me, limites] = await Promise.all([api('GET', '/api-keys/me'), api('GET', '/planos/limites')]);
    if (!me.ok) {
        painel.innerHTML = `<p class="erro">${esc(mensagemErro(me))}</p>`;
        return;
    }
    estado.usuario = me.dados;
    const u = me.dados;
    const l = limites.ok ? limites.dados : null;
    const base = API || location.origin;
    const recursos = Object.keys(ROTULOS_RECURSOS);
    const ops = Object.keys(OPERACOES);

    painel.innerHTML = `
        <div class="painel__cabecalho">
            <div>
                <h1>Minha conta</h1>
                <p class="painel__descricao">Seus dados de acesso, a API Key usada pelas requisições e o que o seu papel permite.</p>
            </div>
        </div>

        <div class="conta">
            <section class="cartao conta__perfil">
                <div class="conta__avatar" aria-hidden="true">${esc((u.nome || '?').trim().split(/\s+/).map(p => p[0]).slice(0, 2).join('').toUpperCase())}</div>
                <div>
                    <h2>${esc(u.nome)}</h2>
                    <p class="id">${esc(u.email)}</p>
                    <div class="conta__selos">
                        <span class="selo selo--${esc(u.papel)}">${esc(u.papel)}</span>
                        <span class="selo selo-plano selo-plano--${esc(u.plano || 'GRATUITO')}">${esc(NOMES_PLANOS[u.plano] || 'Gratuito')}</span>
                    </div>
                </div>
                <dl class="conta__dados">
                    <div><dt>Conta criada em</dt><dd>${esc(dataHora(u.criadaEm))}</dd></div>
                    <div><dt>Último uso da chave</dt><dd>${esc(dataHora(u.ultimoUsoEm))}</dd></div>
                    <div><dt>Plano</dt><dd>${esc(NOMES_PLANOS[u.plano] || 'Gratuito')} · <button type="button" class="link" data-ir-aba="planos">ver planos</button></dd></div>
                </dl>
            </section>

            <section class="cartao">
                <header class="cartao__cabecalho">
                    <div>
                        <h2>API Key</h2>
                        <p>Envie no header <code>X-API-Key</code> em todas as requisições.</p>
                    </div>
                    <span class="status-chave ${u.ativa ? 'status-chave--ativa' : ''}">${u.ativa ? 'Ativa' : 'Desativada'}</span>
                </header>

                <div class="chave-campo">
                    <code id="conta-chave" data-visivel="false">${esc(chaveMascarada(estado.chave))}</code>
                    <button type="button" class="btn btn--pequeno" id="btn-mostrar-chave">Mostrar</button>
                    <button type="button" class="btn btn--pequeno" id="btn-copiar-minha-chave">Copiar</button>
                </div>
                <p class="conta__nota">Prefixo <code>${esc(u.prefixo || '')}</code>. A API guarda apenas o hash da chave: o valor
                    completo só aparece aqui porque está salvo neste navegador. Entrar de novo devolve a mesma chave; para trocá-la, use o botão abaixo.</p>

                <h3>Exemplo de uso</h3>
                <pre class="codigo">curl ${esc(base)}/peixes \\
  -H "X-API-Key: ${esc(chaveMascarada(estado.chave))}" \\
  -H "X-API-Version: 1"</pre>

                <div class="conta__perigo">
                    <div>
                        <b>Gerar nova chave</b>
                        <p>Use se a chave vazou. O valor atual deixa de funcionar na hora, em qualquer lugar onde estiver configurado.</p>
                    </div>
                    <button type="button" class="btn btn--perigo" id="btn-regenerar-minha-chave">Gerar nova chave</button>
                </div>
            </section>

            ${l ? `
            <section class="cartao">
                <header class="cartao__cabecalho">
                    <div><h2>Consumo</h2><p>Limite de requisições do plano ${esc(NOMES_PLANOS[u.plano] || 'Gratuito')}.</p></div>
                </header>
                <div class="consumo__barra"><div style="width:${Math.max(0, Math.min(100, l.fichasRestantes / l.capacidade * 100))}%"></div></div>
                <p><b>${l.fichasRestantes} de ${l.capacidade}</b> requisições disponíveis agora · recarga de ${l.recargaPorMinuto} por minuto (${esc(l.ritmo)}).</p>
            </section>` : ''}

            <section class="cartao conta__permissoes">
                <header class="cartao__cabecalho">
                    <div><h2>Permissões</h2><p>O que o papel ${esc(u.papel)} pode fazer em cada recurso.</p></div>
                </header>
                <div class="tabela-wrap">
                    <table class="tabela matriz">
                        <thead><tr><th>Recurso</th>${ops.map(o => `<th>${esc(OPERACOES[o])}</th>`).join('')}</tr></thead>
                        <tbody>
                        ${recursos.map(r => `<tr><th scope="row">${esc(ROTULOS_RECURSOS[r])}</th>${ops.map(o => {
                            const ok = (u.permissoes?.[r] || []).includes(o);
                            return `<td><span class="matriz__marca matriz__marca--${ok ? 'sim' : 'nao'}">${ok ? 'Sim' : 'Não'}</span></td>`;
                        }).join('')}</tr>`).join('')}
                        </tbody>
                    </table>
                </div>
            </section>
        </div>`;
}

async function regenerarMinhaChave() {
    const botao = $('#btn-regenerar-minha-chave');
    botao.disabled = true;
    const r = await api('POST', '/api-keys/me/regenerar');
    botao.disabled = false;
    if (!r.ok || !r.dados?.chave) {
        toast('Não foi possível gerar uma nova chave.', 'erro', mensagemErro(r));
        return;
    }
    estado.chave = r.dados.chave;
    gravarStorage(CHAVE_STORAGE, r.dados.chave);
    toast('Nova chave gerada. A anterior deixou de funcionar.', 'ok');
    await montarConta();
    const campo = $('#conta-chave');
    campo.textContent = estado.chave;
    campo.dataset.visivel = 'true';
    $('#btn-mostrar-chave').textContent = 'Ocultar';
}

const ROTULOS_RECURSOS = {
    peixes: 'Peixes', alimentacoes: 'Alimentações', especies: 'Espécies', aquarios: 'Aquários',
    cuidadores: 'Cuidadores', 'api-keys': 'API Keys'
};

// Chaves fixas criadas pela API para demonstração (usadas nos cenários de papéis)
const CHAVES_DEMO = {
    BIOLOGO: 'hf_biologo_demo_2025',
    CUIDADOR: 'hf_cuidador_demo_2025',
    VISITANTE: 'hf_visitante_demo_2025'
};

// O corpo das respostas aparece no painel de requisições do cenário; o texto de resultado traz só a análise
function corpo() {
    return '';
}

async function primeiroItem(rota, rel) {
    const r = await api('GET', `${rota}?size=1&sort=id,asc`);
    return r.dados?._embedded?.[rel]?.[0] ?? null;
}

/** Chave usada nos testes de idempotência: a do usuário, se ele puder cadastrar alimentações; senão, a de demonstração. */
function chaveParaCadastro() {
    return pode('alimentacoes', 'CADASTRAR') ? estado.chave : CHAVES_DEMO.BIOLOGO;
}

function resultadoSimples(r, esperado, extra = '') {
    return {
        passou: r.status === esperado,
        obtido: `HTTP ${r.status || 'sem resposta'}`,
        detalhe: (extra ? extra + '\n\n' : '') + corpo(r)
    };
}

const GRUPOS_TESTE = [
    {
        id: 'idempotencia', titulo: 'Idempotência',
        descricao: 'POST com X-Idempotency-Key não duplica registros.',
        casos: ['idem-repetir', 'idem-outro-corpo', 'idem-sem-chave']
    },
    {
        id: 'autenticacao', titulo: 'Autenticação e autorização',
        descricao: 'API Key obrigatória e permissões por papel.',
        casos: ['auth-sem', 'auth-invalida', 'auth-login', 'auth-cuidador', 'auth-visitante', 'auth-biologo', 'auth-me']
    },
    {
        id: 'limites', titulo: 'Planos e limite de requisições',
        descricao: 'Token bucket por plano, Retry-After e bloqueio progressivo.',
        casos: ['planos-listar', 'planos-limites', 'rate', 'rate-bloqueio'],
        // Esvaziam o balde da chave de demonstração do visitante: só rodam sob demanda
        manuais: ['rate', 'rate-bloqueio']
    },
    {
        id: 'cors', titulo: 'CORS',
        descricao: 'Origens autorizadas a chamar a API pelo navegador.',
        casos: ['cors-permitida', 'cors-negada', 'cors-front']
    },
    {
        id: 'versionamento', titulo: 'Versionamento',
        descricao: 'Formato da resposta escolhido pelo header X-API-Version.',
        casos: ['versao-comparar', 'versao-invalida']
    },
    {
        id: 'erros', titulo: 'Validação e tratamento de erros',
        descricao: 'Respostas de erro padronizadas pelo @ControllerAdvice.',
        casos: ['erro-validacao', 'erro-enum', 'erro-404', 'erro-405', 'erro-409']
    }
];

const CASOS = {
    'idem-repetir': {
        nome: 'Reenvio com a mesma chave devolve o registro original',
        requisicao: 'POST /alimentacoes',
        esperado: '201 · mesmo id',
        async executar() {
            const chave = uuid();
            const corpoReq = { nome: `Ração Teste ${Math.floor(Math.random() * 9000 + 1000)}`, tipo: 'RACAO' };
            estado.testeIdem = { chave, corpoReq };
            const opcoes = { chave: chaveParaCadastro(), corpo: corpoReq, headers: { 'X-Idempotency-Key': chave } };
            const a = await api('POST', '/alimentacoes', opcoes);
            const b = await api('POST', '/alimentacoes', opcoes);
            const mesmoId = a.ok && b.ok && a.dados?.id === b.dados?.id;
            return {
                passou: a.status === 201 && b.status === 201 && mesmoId,
                obtido: `${a.status} / ${b.status}` + (mesmoId ? `, id ${a.dados.id}` : ''),
                detalhe: `X-Idempotency-Key: ${chave}\n`
                    + `1º envio: HTTP ${a.status}, id ${a.dados?.id ?? '-'}, X-Idempotent-Replayed: ${a.headers.get('X-Idempotent-Replayed')}\n`
                    + `2º envio: HTTP ${b.status}, id ${b.dados?.id ?? '-'}, X-Idempotent-Replayed: ${b.headers.get('X-Idempotent-Replayed')}\n\n`
                    + corpo(b)
            };
        }
    },
    'idem-outro-corpo': {
        nome: 'Mesma chave com corpo diferente é rejeitada',
        requisicao: 'POST /alimentacoes',
        esperado: '422',
        async executar() {
            if (!estado.testeIdem) {
                await CASOS['idem-repetir'].executar();
            }
            const { chave, corpoReq } = estado.testeIdem;
            const r = await api('POST', '/alimentacoes', {
                chave: chaveParaCadastro(),
                corpo: { ...corpoReq, tipo: 'VEGETAL' },
                headers: { 'X-Idempotency-Key': chave }
            });
            return resultadoSimples(r, 422, `X-Idempotency-Key reutilizada: ${chave}`);
        }
    },
    'idem-sem-chave': {
        nome: 'POST sem X-Idempotency-Key é rejeitado',
        requisicao: 'POST /alimentacoes',
        esperado: '400',
        async executar() {
            const r = await api('POST', '/alimentacoes', { chave: chaveParaCadastro(), corpo: { nome: 'Sem chave', tipo: 'RACAO' } });
            return resultadoSimples(r, 400);
        }
    },
    'auth-sem': {
        nome: 'Requisição sem credencial',
        requisicao: 'GET /peixes',
        esperado: '401',
        async executar() {
            const r = await api('GET', '/peixes', { semChave: true });
            return resultadoSimples(r, 401, `WWW-Authenticate: ${r.headers.get('WWW-Authenticate') ?? '-'}`);
        }
    },
    'auth-invalida': {
        nome: 'API Key inexistente',
        requisicao: 'GET /peixes',
        esperado: '401',
        async executar() {
            return resultadoSimples(await api('GET', '/peixes', { chave: 'hf_chave_que_nao_existe' }), 401);
        }
    },
    'auth-login': {
        nome: 'Login com senha incorreta',
        requisicao: 'POST /auth/login',
        esperado: '401',
        async executar() {
            const r = await api('POST', '/auth/login', {
                semChave: true, corpo: { email: 'cuidador@hookfishing.com', senha: 'senha-incorreta' }
            });
            return resultadoSimples(r, 401);
        }
    },
    'auth-cuidador': {
        nome: 'Cuidador não pode excluir peixe',
        requisicao: 'DELETE /peixes/{id}',
        esperado: '403',
        async executar() {
            const peixe = await primeiroItem('/peixes', 'peixes');
            const r = await api('DELETE', `/peixes/${peixe?.id ?? 1}`, { chave: CHAVES_DEMO.CUIDADOR });
            return resultadoSimples(r, 403, `Chave usada: ${CHAVES_DEMO.CUIDADOR}`);
        }
    },
    'auth-visitante': {
        nome: 'Visitante não pode cadastrar alimentação',
        requisicao: 'POST /alimentacoes',
        esperado: '403',
        async executar() {
            const r = await api('POST', '/alimentacoes', {
                chave: CHAVES_DEMO.VISITANTE,
                corpo: { nome: 'Krill Congelado', tipo: 'CONGELADA' },
                headers: { 'X-Idempotency-Key': uuid() }
            });
            return resultadoSimples(r, 403, `Chave usada: ${CHAVES_DEMO.VISITANTE}`);
        }
    },
    'auth-biologo': {
        nome: 'Biólogo não pode alterar aquário',
        requisicao: 'PUT /aquarios/1',
        esperado: '403',
        async executar() {
            const r = await api('PUT', '/aquarios/1', {
                chave: CHAVES_DEMO.BIOLOGO,
                corpo: { nome: 'Aquário Principal', capacidadeLitros: 250, cuidadorId: 1 }
            });
            return resultadoSimples(r, 403, `Chave usada: ${CHAVES_DEMO.BIOLOGO}`);
        }
    },
    'auth-me': {
        nome: 'Consulta da própria API Key',
        requisicao: 'GET /api-keys/me',
        esperado: '200',
        async executar() {
            return resultadoSimples(await api('GET', '/api-keys/me'), 200);
        }
    },
    'planos-listar': {
        nome: 'Catálogo de planos é público',
        requisicao: 'GET /planos',
        esperado: '200 · 3 planos',
        async executar() {
            const r = await api('GET', '/planos', { semChave: true });
            const planos = r.dados?._embedded?.planos || [];
            return {
                passou: r.status === 200 && planos.length === 3,
                obtido: `HTTP ${r.status}, ${planos.length} plano(s)`,
                detalhe: planos.map(p => `${p.codigo}: ${p.preco}, ${p.capacidade} de uma vez, ${p.recargaPorMinuto}/min`).join('\n')
                    + `\n\nX-RateLimit-Policy: ${r.headers.get('X-RateLimit-Policy')}`
            };
        }
    },
    'planos-limites': {
        nome: 'Consumo da chave segue o plano',
        requisicao: 'GET /planos/limites',
        esperado: '200',
        async executar() {
            const r = await api('GET', '/planos/limites');
            return {
                passou: r.status === 200 && r.dados?.plano === (estado.usuario?.plano || 'GRATUITO'),
                obtido: `HTTP ${r.status}, ${r.dados?.plano ?? '-'} ${r.dados ? `(${r.dados.fichasRestantes}/${r.dados.capacidade})` : ''}`,
                detalhe: `X-RateLimit-Policy: ${r.headers.get('X-RateLimit-Policy')}\n\n` + corpo(r)
            };
        }
    },
    rate: {
        nome: 'Rajada acima da capacidade do plano',
        requisicao: 'GET /especies',
        esperado: '429 · Retry-After',
        async executar() {
            const contagem = {};
            let bloqueio = null;
            let total = 0;
            for (let i = 1; i <= 100; i++) {
                const r = await api('GET', '/especies?size=1', { chave: CHAVES_DEMO.VISITANTE });
                total = i;
                contagem[r.status] = (contagem[r.status] || 0) + 1;
                if (r.status === 429 || r.status === 0) {
                    bloqueio = r;
                    break;
                }
            }
            const resumo = Object.entries(contagem).map(([s, n]) => `${n}x HTTP ${s}`).join(', ');
            if (!bloqueio || bloqueio.status !== 429) {
                return { passou: false, obtido: 'limite não atingido', detalhe: `${total} requisições: ${resumo}` };
            }
            const retry = bloqueio.headers.get('Retry-After');
            return {
                passou: !!retry,
                obtido: `429 na ${total}ª, Retry-After ${retry}s`,
                detalhe: `Chave usada: ${CHAVES_DEMO.VISITANTE}\n${total} requisições: ${resumo}\n`
                    + `Retry-After: ${retry}\n`
                    + `X-RateLimit-Limit: ${bloqueio.headers.get('X-RateLimit-Limit')}\n`
                    + `X-RateLimit-Remaining: ${bloqueio.headers.get('X-RateLimit-Remaining')}\n`
                    + `X-RateLimit-Policy: ${bloqueio.headers.get('X-RateLimit-Policy')}\n`
                    + (bloqueio.headers.get('X-RateLimit-Blocked-Until')
                        ? `X-RateLimit-Blocked-Until: ${bloqueio.headers.get('X-RateLimit-Blocked-Until')}\n` : '')
                    + '\n' + corpo(bloqueio)
            };
        }
    },
    'rate-bloqueio': {
        nome: 'Insistência após o 429 bloqueia a chave',
        requisicao: 'GET /especies',
        esperado: '429 · Blocked-Until',
        async executar() {
            const linhas = [];
            let bloqueio = null;
            for (let i = 1; i <= 100; i++) {
                const r = await api('GET', '/especies?size=1', { chave: CHAVES_DEMO.VISITANTE });
                if (r.status === 0) {
                    return { passou: false, obtido: 'sem resposta', detalhe: corpo(r) };
                }
                if (r.status === 429) {
                    linhas.push(`${i}ª: HTTP 429, Retry-After ${r.headers.get('Retry-After')}s`
                        + (r.headers.get('X-RateLimit-Blocked-Until') ? ' (bloqueado)' : ''));
                }
                if (r.headers.get('X-RateLimit-Blocked-Until')) {
                    bloqueio = r;
                    break;
                }
            }
            if (!bloqueio) {
                return { passou: false, obtido: 'sem bloqueio', detalhe: linhas.join('\n') };
            }
            const ate = bloqueio.headers.get('X-RateLimit-Blocked-Until');
            return {
                passou: true,
                obtido: `bloqueado por ${bloqueio.headers.get('Retry-After')}s`,
                detalhe: `Chave usada: ${CHAVES_DEMO.VISITANTE}\n${linhas.join('\n')}\n\n`
                    + `X-RateLimit-Blocked-Until: ${ate} (${new Date(ate).toLocaleTimeString('pt-BR')} no seu horário)\n\n`
                    + corpo(bloqueio)
            };
        }
    },
    'cors-permitida': {
        nome: 'Origem configurada é aceita',
        requisicao: 'GET /cors?origem=http://localhost:5500',
        esperado: '200 · permitida',
        async executar() {
            const r = await api('GET', '/cors?origem=' + encodeURIComponent('http://localhost:5500'), { semChave: true });
            return {
                passou: r.status === 200 && r.dados?.permitida === true,
                obtido: `HTTP ${r.status || 'sem resposta'}` + (r.dados ? ` · ${r.dados.permitida ? 'permitida' : 'recusada'}` : ''),
                detalhe: r.dados?.motivo || ''
            };
        }
    },
    'cors-negada': {
        nome: 'Origem desconhecida é recusada',
        requisicao: 'GET /cors?origem=https://site-externo.example',
        esperado: '200 · recusada',
        async executar() {
            const r = await api('GET', '/cors?origem=' + encodeURIComponent('https://site-externo.example'), { semChave: true });
            return {
                passou: r.status === 200 && r.dados?.permitida === false,
                obtido: `HTTP ${r.status || 'sem resposta'}` + (r.dados ? ` · ${r.dados.permitida ? 'permitida' : 'recusada'}` : ''),
                detalhe: r.dados?.motivo || ''
            };
        }
    },
    'cors-front': {
        nome: 'Front-end atual consegue chamar a API',
        requisicao: 'GET /peixes',
        esperado: '200',
        async executar() {
            const r = await api('GET', '/peixes?size=1');
            const outraOrigem = API !== '' && !API.startsWith(location.origin);
            const permitido = r.headers.get('Access-Control-Allow-Origin');
            return {
                passou: r.ok,
                obtido: `HTTP ${r.status || 'sem resposta'}` + (outraOrigem ? ` · ${permitido ?? 'sem Allow-Origin'}` : ' · mesma origem'),
                detalhe: outraOrigem
                    ? `Front-end em ${location.origin}, API em ${API}. A resposta trouxe Access-Control-Allow-Origin: ${permitido}.`
                    : `Front-end e API em ${location.origin} (mesma origem): o navegador não aplica CORS a esta chamada.`
            };
        }
    },
    'versao-comparar': {
        nome: 'Mesmo recurso em v1 e v2',
        requisicao: 'GET /peixes/{id}',
        esperado: '200 · formatos distintos',
        async executar() {
            const peixe = await primeiroItem('/peixes', 'peixes');
            if (!peixe) {
                return { passou: false, obtido: 'sem peixes', detalhe: 'Cadastre um peixe para executar este cenário.' };
            }
            const v1 = await api('GET', `/peixes/${peixe.id}`, { headers: { 'X-API-Version': '1' } });
            const v2 = await api('GET', `/peixes/${peixe.id}`, { headers: { 'X-API-Version': '2' } });
            const diferentes = v1.ok && v2.ok && 'especieId' in v1.dados && 'especie' in v2.dados;
            return {
                passou: diferentes,
                obtido: `${v1.status} / ${v2.status}`,
                detalhe: `X-API-Version: 1\n${corpo(v1)}\n\nX-API-Version: 2\n${corpo(v2)}`
            };
        }
    },
    'versao-invalida': {
        nome: 'Versão não suportada',
        requisicao: 'GET /peixes',
        esperado: '400',
        async executar() {
            return resultadoSimples(await api('GET', '/peixes?size=1', { headers: { 'X-API-Version': '3' } }), 400);
        }
    },
    'erro-validacao': {
        nome: 'Campos inválidos retornam a lista de erros',
        requisicao: 'POST /peixes',
        esperado: '400 · campos',
        async executar() {
            const r = await api('POST', '/peixes', {
                chave: chaveParaCadastro(),
                corpo: { nome: '', idade: -1, tamanho: 0, especieId: 1, aquarioId: 1 },
                headers: { 'X-Idempotency-Key': uuid() }
            });
            return {
                passou: r.status === 400 && (r.dados?.campos || []).length > 0,
                obtido: `HTTP ${r.status}, ${(r.dados?.campos || []).length} campo(s)`,
                detalhe: corpo(r)
            };
        }
    },
    'erro-enum': {
        nome: 'Valor fora do enum',
        requisicao: 'POST /alimentacoes',
        esperado: '400',
        async executar() {
            const r = await api('POST', '/alimentacoes', {
                chave: chaveParaCadastro(),
                corpo: { nome: 'Minhoca', tipo: 'PEIXE' },
                headers: { 'X-Idempotency-Key': uuid() }
            });
            return resultadoSimples(r, 400);
        }
    },
    'erro-404': {
        nome: 'Recurso inexistente',
        requisicao: 'GET /peixes/99999',
        esperado: '404',
        async executar() {
            return resultadoSimples(await api('GET', '/peixes/99999'), 404);
        }
    },
    'erro-405': {
        nome: 'Método HTTP não suportado',
        requisicao: 'PATCH /peixes/1',
        esperado: '405',
        async executar() {
            return resultadoSimples(await api('PATCH', '/peixes/1', { corpo: { nome: 'Nemo' } }), 405);
        }
    },
    'erro-409': {
        nome: 'Cadastro duplicado',
        requisicao: 'POST /alimentacoes',
        esperado: '409',
        async executar() {
            const existente = await primeiroItem('/alimentacoes', 'alimentacoes');
            if (!existente) {
                return { passou: false, obtido: 'sem alimentações', detalhe: 'Cadastre uma alimentação para executar este cenário.' };
            }
            const r = await api('POST', '/alimentacoes', {
                chave: chaveParaCadastro(),
                corpo: { nome: existente.nome, tipo: existente.tipo },
                headers: { 'X-Idempotency-Key': uuid() }
            });
            return resultadoSimples(r, 409, `Nome já existente: ${existente.nome}`);
        }
    }
};

const ICONES = {
    executar: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4.5 3.2v9.6L12.5 8z" fill="currentColor"/></svg>',
    seta: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M6 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>'
};

const resultadosTestes = {};

function grupoDoCaso(id) {
    return GRUPOS_TESTE.find(g => g.casos.includes(id));
}

function ehManual(id) {
    return (grupoDoCaso(id)?.manuais || []).includes(id);
}

function rotaDoCaso(caso) {
    const [metodo, ...resto] = caso.requisicao.split(' ');
    return `<span class="metodo metodo--${esc(metodo)}">${esc(metodo)}</span><span class="rota">${esc(resto.join(' '))}</span>`;
}

function montarTestes() {
    const u = estado.usuario || {};
    const permissoes = Object.entries(u.permissoes || {})
        .map(([recurso, ops]) => `<tr><th>${esc(ROTULOS_RECURSOS[recurso] || recurso)}</th>`
            + `<td>${ops.length ? esc(ops.map(o => OPERACOES[o] || o).join(', ')) : '<span class="id">sem acesso</span>'}</td></tr>`)
        .join('');

    $('#painel-testes').innerHTML = `
        <div class="suite__topo">
            <div>
                <h1>Testes da API</h1>
                <p class="painel__descricao">Cenários executados contra <code>${esc(API || location.origin)}</code>.
                    Cada resultado mostra a requisição enviada e a resposta recebida.</p>
            </div>
            <div class="suite__acoes">
                <div class="segmentado" role="group" aria-label="Filtrar cenários">
                    <button type="button" data-filtro="todos" aria-pressed="true">Todos</button>
                    <button type="button" data-filtro="falhou" aria-pressed="false">Falhas</button>
                    <button type="button" data-filtro="pendente" aria-pressed="false">Pendentes</button>
                </div>
                <button id="btn-executar-todos" class="btn btn--primario" type="button">Executar todos</button>
            </div>
        </div>

        <div class="metricas" id="testes-metricas"></div>

        <div class="suite__contexto">
            <span>Executando como <b>${esc(u.nome)}</b></span>
            <span class="selo selo--${esc(u.papel)}">${esc(u.papel)}</span>
            <span class="selo selo-plano selo-plano--${esc(u.plano || 'GRATUITO')}">${esc(NOMES_PLANOS[u.plano] || 'Gratuito')}</span>
            <details class="suite__permissoes">
                <summary>Permissões desta conta</summary>
                <table class="permissoes-tabela"><tbody>${permissoes}</tbody></table>
            </details>
        </div>

        ${GRUPOS_TESTE.map(grupo => `
            <section class="suite" data-suite="${grupo.id}">
                <header class="suite__cabecalho">
                    <div>
                        <h2>${esc(grupo.titulo)}</h2>
                        <p>${esc(grupo.descricao)}</p>
                    </div>
                    <span class="suite__contagem" data-contagem="${grupo.id}"></span>
                    <button class="btn btn--pequeno" type="button" data-executar-suite="${grupo.id}">Executar suíte</button>
                </header>
                <div class="casos__cabecalho" aria-hidden="true">
                    <span></span><span>Cenário</span><span>Esperado</span><span>Obtido</span><span>Tempo</span><span></span>
                </div>
                <ol class="casos">
                ${grupo.casos.map(id => `
                    <li class="caso" data-caso="${id}">
                        <div class="caso__linha">
                            <span class="caso__icone" aria-hidden="true"></span>
                            <div class="caso__titulo">
                                <b>${esc(CASOS[id].nome)}</b>
                                <span class="caso__rota">${rotaDoCaso(CASOS[id])}</span>
                                ${ehManual(id) ? '<span class="caso__manual" title="Usa a chave de demonstração do visitante e esvazia o limite dela. Não roda em Executar todos.">Manual</span>' : ''}
                            </div>
                            <span class="caso__esperado">${esc(CASOS[id].esperado)}</span>
                            <span class="caso__obtido">-</span>
                            <span class="caso__tempo">-</span>
                            <span class="caso__botoes">
                                <button class="icone-btn" type="button" data-executar="${id}" title="Executar" aria-label="Executar ${esc(CASOS[id].nome)}">${ICONES.executar}</button>
                                <button class="icone-btn caso__expandir" type="button" data-detalhe="${id}" title="Ver requisições" aria-label="Ver requisições" aria-expanded="false" disabled>${ICONES.seta}</button>
                            </span>
                        </div>
                        <div class="caso__painel" hidden></div>
                    </li>`).join('')}
                </ol>
            </section>`).join('')}`;

    Object.entries(resultadosTestes).forEach(([id, r]) => mostrarResultado(id, r));
    atualizarResumoTestes();
}

function blocoCodigo(valor) {
    if (valor === null || valor === undefined || valor === '') return '<pre class="codigo codigo--vazio">sem corpo</pre>';
    let texto = valor;
    if (typeof valor === 'string') {
        try { texto = json(JSON.parse(valor)); } catch { /* texto puro */ }
    } else {
        const { _links, ...resto } = valor;
        texto = json(resto);
    }
    return `<pre class="codigo">${esc(texto)}</pre>`;
}

function listaHeaders(headers) {
    const linhas = Object.entries(headers || {});
    if (!linhas.length) return '<p class="id">nenhum header relevante</p>';
    return `<dl class="headers">${linhas.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join('')}</dl>`;
}

function classeStatus(status) {
    if (!status) return 'erro';
    if (status < 300) return 'ok';
    if (status < 500) return 'aviso';
    return 'erro';
}

function renderizarPainel(id, r) {
    const capturas = r.capturas || [];
    const LIMITE = 4;
    const exibidas = capturas.length > LIMITE ? capturas.slice(-LIMITE) : capturas;
    return `
        ${r.detalhe?.trim() ? `<div class="caso__analise"><span>Análise</span><pre>${esc(r.detalhe.trim())}</pre></div>` : ''}
        ${capturas.length > LIMITE ? `<p class="caso__nota">${capturas.length} requisições feitas; exibindo as ${LIMITE} últimas.</p>` : ''}
        ${exibidas.map((c, i) => `
            <details class="troca" ${i === exibidas.length - 1 ? 'open' : ''}>
                <summary>
                    <span class="metodo metodo--${esc(c.metodo)}">${esc(c.metodo)}</span>
                    <span class="rota">${esc(c.caminho)}</span>
                    <span class="codigo-http codigo-http--${classeStatus(c.status)}">${c.status || 'sem resposta'}</span>
                    <span class="troca__tempo">${c.ms} ms</span>
                </summary>
                <div class="troca__corpo">
                    <div>
                        <h4>Requisição</h4>
                        ${listaHeaders(c.enviados)}
                        ${c.corpoEnviado ? blocoCodigo(c.corpoEnviado) : ''}
                    </div>
                    <div>
                        <h4>Resposta</h4>
                        ${listaHeaders(c.recebidos)}
                        ${blocoCodigo(c.dados)}
                    </div>
                </div>
            </details>`).join('')}`;
}

function mostrarResultado(id, r) {
    const item = $(`.caso[data-caso="${id}"]`);
    if (!item) return;
    item.dataset.situacao = r.passou ? 'passou' : 'falhou';
    $('.caso__obtido', item).textContent = r.obtido || '-';
    $('.caso__tempo', item).textContent = `${r.ms} ms`;
    $('.caso__painel', item).innerHTML = renderizarPainel(id, r);
    $('[data-detalhe]', item).disabled = false;
}

async function executarCaso(id) {
    const item = $(`.caso[data-caso="${id}"]`);
    item.dataset.situacao = 'rodando';
    $('.caso__obtido', item).textContent = 'Executando...';
    $(`[data-executar="${id}"]`, item).disabled = true;

    estado.captura = [];
    const inicio = performance.now();
    let resultado;
    try {
        resultado = await CASOS[id].executar();
    } catch (e) {
        resultado = { passou: false, obtido: 'erro no cenário', detalhe: String(e) };
    }
    resultado.ms = Math.round(performance.now() - inicio);
    resultado.capturas = estado.captura;
    estado.captura = null;

    resultadosTestes[id] = resultado;
    mostrarResultado(id, resultado);
    $(`[data-executar="${id}"]`, item).disabled = false;
    atualizarResumoTestes();
}

function atualizarResumoTestes() {
    const itens = $$('.caso[data-caso]');
    if (!itens.length) return;
    const passaram = itens.filter(i => i.dataset.situacao === 'passou').length;
    const falharam = itens.filter(i => i.dataset.situacao === 'falhou').length;
    const executados = passaram + falharam;
    const tempo = Object.values(resultadosTestes).reduce((t, r) => t + (r.ms || 0), 0);
    const pct = Math.round(executados / itens.length * 100);

    $('#testes-metricas').innerHTML = `
        <div class="metrica"><span>Cenários</span><b>${itens.length}</b></div>
        <div class="metrica metrica--ok"><span>Aprovados</span><b>${passaram}</b></div>
        <div class="metrica metrica--erro"><span>Falhas</span><b>${falharam}</b></div>
        <div class="metrica"><span>Tempo total</span><b>${tempo ? (tempo / 1000).toFixed(1) + ' s' : '-'}</b></div>
        <div class="metrica metrica--progresso">
            <span>Execução</span>
            <div class="progresso" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100">
                <div class="progresso__ok" style="width:${passaram / itens.length * 100}%"></div>
                <div class="progresso__erro" style="width:${falharam / itens.length * 100}%"></div>
            </div>
            <small>${executados} de ${itens.length} executados</small>
        </div>`;

    GRUPOS_TESTE.forEach(g => {
        const alvo = $(`[data-contagem="${g.id}"]`);
        if (!alvo) return;
        const doGrupo = g.casos.map(id => $(`.caso[data-caso="${id}"]`));
        const ok = doGrupo.filter(i => i.dataset.situacao === 'passou').length;
        const falha = doGrupo.filter(i => i.dataset.situacao === 'falhou').length;
        alvo.textContent = ok + falha ? `${ok}/${g.casos.length} aprovados` : `${g.casos.length} cenários`;
        alvo.className = 'suite__contagem' + (falha ? ' suite__contagem--erro' : ok === g.casos.length ? ' suite__contagem--ok' : '');
    });
    aplicarFiltroTestes();
}

function aplicarFiltroTestes() {
    const filtro = estado.filtroTestes || 'todos';
    $$('.caso[data-caso]').forEach(item => {
        const situacao = item.dataset.situacao || 'pendente';
        item.hidden = filtro !== 'todos' && !(filtro === situacao || (filtro === 'pendente' && situacao === 'rodando'));
    });
    $$('.suite').forEach(suite => {
        suite.hidden = !$$('.caso', suite).some(i => !i.hidden);
    });
}

async function executarLista(ids, botao) {
    if (botao) botao.disabled = true;
    try {
        for (const id of ids) {
            await executarCaso(id);
        }
    } finally {
        if (botao) botao.disabled = false;
    }
}

function executarTodos() {
    const ids = GRUPOS_TESTE.flatMap(g => g.casos.filter(id => !(g.manuais || []).includes(id)));
    return executarLista(ids, $('#btn-executar-todos'));
}

/* ---------------------------------------------------------------------
 * Eventos
 * ------------------------------------------------------------------- */

function registrarEventos() {
    $('#servidor').addEventListener('change', e => {
        if (e.target.name === 'servidor') {
            const outro = e.target.value === 'outro';
            $('#servidor-url').hidden = !outro;
            if (outro) {
                $('#servidor-url').focus();
            } else {
                trocarServidor(SERVIDORES[e.target.value].url);
            }
        } else if (e.target.id === 'servidor-url') {
            trocarServidor(e.target.value);
        }
    });
    $('#servidor-testar').addEventListener('click', verificarServidor);

    $('#form-login').addEventListener('submit', e => {
        e.preventDefault();
        entrar($('#login-email').value.trim(), $('#login-senha').value);
    });
    $('#form-cadastro').addEventListener('submit', e => {
        e.preventDefault();
        cadastrar();
    });
    $$('[data-ir]').forEach(link => link.addEventListener('click', e => {
        e.preventDefault();
        mostrarAcesso(link.dataset.ir);
    }));
    $$('.contas-teste tbody tr').forEach(linha => linha.addEventListener('click', () => {
        const [email, senha] = $$('td', linha).map(td => td.textContent.trim());
        $('#login-email').value = email;
        $('#login-senha').value = senha;
        $('#login-senha').focus();
    }));

    $('#btn-sair').addEventListener('click', sair);
    $('#btn-limpar-log').addEventListener('click', () => { $('#log-lista').innerHTML = ''; });

    $('#abas').addEventListener('click', e => {
        const aba = e.target.closest('[data-aba]');
        if (aba) selecionarAba(aba.dataset.aba);
    });

    $('#form-busca').addEventListener('submit', e => {
        e.preventDefault();
        const valor = $('#busca-valor').value.trim();
        if (!valor) return;
        estado.busca = valor;
        estado.pagina = 0;
        $('#btn-limpar').hidden = false;
        carregar();
    });
    $('#btn-limpar').addEventListener('click', () => {
        estado.busca = null;
        estado.pagina = 0;
        $('#btn-limpar').hidden = true;
        const campo = $('#busca-valor');
        if (campo.tagName === 'INPUT') campo.value = '';
        carregar();
    });

    $('#ordenar').addEventListener('change', e => { estado.ordenar = e.target.value; estado.pagina = 0; carregar(); });
    $('#tamanho').addEventListener('change', e => { estado.tamanho = Number(e.target.value); estado.pagina = 0; carregar(); });

    $('#versao').addEventListener('click', e => {
        const botao = e.target.closest('[data-versao]');
        if (!botao) return;
        estado.versao = botao.dataset.versao;
        $$('.versao__btn').forEach(b => b.classList.toggle('ativo', b === botao));
        carregar();
    });

    $$('[data-pagina]').forEach(b => b.addEventListener('click', () => irParaPagina(b.dataset.pagina)));

    $('#btn-novo').addEventListener('click', () => abrirFormulario(null));

    $('#tabela-corpo').addEventListener('click', e => {
        const botao = e.target.closest('button');
        if (!botao) return;
        const def = ENTIDADES[estado.aba];
        if (botao.dataset.editar != null) abrirFormulario(estado.itens[botao.dataset.editar]);
        if (botao.dataset.excluir != null) excluir(estado.itens[botao.dataset.excluir]);
        if (botao.dataset.extra != null) def.acaoExtra.executar(estado.itens[botao.dataset.extra]);
    });

    $('#painel-conta').addEventListener('click', async e => {
        if (e.target.closest('#btn-mostrar-chave')) {
            const campo = $('#conta-chave');
            const visivel = campo.dataset.visivel === 'true';
            campo.textContent = visivel ? chaveMascarada(estado.chave) : estado.chave;
            campo.dataset.visivel = String(!visivel);
            e.target.textContent = visivel ? 'Mostrar' : 'Ocultar';
        } else if (e.target.closest('#btn-copiar-minha-chave')) {
            try {
                await navigator.clipboard.writeText(estado.chave);
                toast('Chave copiada.', 'ok');
            } catch {
                toast('Não foi possível copiar automaticamente. Clique em Mostrar e copie o texto.', 'alerta');
            }
        } else if (e.target.closest('#btn-regenerar-minha-chave')) {
            regenerarMinhaChave();
        } else if (e.target.closest('[data-ir-aba]')) {
            selecionarAba(e.target.closest('[data-ir-aba]').dataset.irAba);
        }
    });
    $('#usuario-nome').addEventListener('click', () => selecionarAba('conta'));

    $('#painel-planos').addEventListener('click', e => {
        const botao = e.target.closest('[data-plano]');
        if (botao && !botao.disabled) {
            botao.disabled = true;
            contratarPlano(botao.dataset.plano);
        }
    });

    $('#modal-form').addEventListener('submit', salvarFormulario);
    $$('[data-fechar]').forEach(b => b.addEventListener('click', () => $('#modal').close()));

    $('#painel-testes').addEventListener('click', e => {
        const executar = e.target.closest('[data-executar]');
        if (executar) {
            executarCaso(executar.dataset.executar);
            return;
        }
        const suite = e.target.closest('[data-executar-suite]');
        if (suite) {
            const grupo = GRUPOS_TESTE.find(g => g.id === suite.dataset.executarSuite);
            executarLista(grupo.casos.filter(id => !(grupo.manuais || []).includes(id)), suite);
            return;
        }
        const detalhe = e.target.closest('[data-detalhe]');
        if (detalhe) {
            const painel = $('.caso__painel', detalhe.closest('.caso'));
            painel.hidden = !painel.hidden;
            detalhe.setAttribute('aria-expanded', String(!painel.hidden));
            return;
        }
        const filtro = e.target.closest('[data-filtro]');
        if (filtro) {
            estado.filtroTestes = filtro.dataset.filtro;
            $$('[data-filtro]').forEach(b => b.setAttribute('aria-pressed', String(b === filtro)));
            aplicarFiltroTestes();
            return;
        }
        if (e.target.closest('#btn-executar-todos')) {
            executarTodos();
        }
    });
}

/* ---------------------------------------------------------------------
 * Início
 * ------------------------------------------------------------------- */

/* ---------------------------------------------------------------------
 * Escolha do servidor (quando o index.html é aberto fora da API)
 * ------------------------------------------------------------------- */

function montarSeletorServidor() {
    const bloco = $('#servidor');
    if (SERVIDO_PELA_API || window.HOOKFISHING_API) {
        bloco.hidden = true;
        return;
    }
    bloco.hidden = false;
    const atual = Object.keys(SERVIDORES).find(k => SERVIDORES[k].url === API) || 'outro';
    $$('[name="servidor"]').forEach(r => { r.checked = r.value === atual; });
    $('#servidor-url').value = atual === 'outro' ? API : '';
    $('#servidor-url').hidden = atual !== 'outro';
    verificarServidor();
}

async function verificarServidor() {
    const status = $('#servidor-status');
    status.className = 'servidor__status';
    status.textContent = 'Verificando...';
    const alvo = API;
    try {
        // Qualquer resposta HTTP (até 401/404) prova que o servidor está no ar e liberou esta origem no CORS
        await fetch(alvo + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
        if (alvo !== API) return;
        status.className = 'servidor__status servidor__status--ok';
        status.textContent = `Conectado a ${alvo}`;
    } catch {
        if (alvo !== API) return;
        status.className = 'servidor__status servidor__status--erro';
        status.textContent = alvo.includes('onrender.com')
            ? `Sem resposta de ${alvo}. No plano gratuito o Render pode levar até 1 minuto para acordar: tente de novo.`
            : `Sem resposta de ${alvo}. Confira se a aplicação está rodando no IntelliJ.`;
    }
}

function trocarServidor(url) {
    const limpa = (url || '').trim().replace(/\/+$/, '');
    if (!limpa || limpa === API) return;
    API = limpa;
    gravarStorage(BASE_STORAGE, limpa);
    // A chave salva pertence ao servidor anterior
    gravarStorage(CHAVE_STORAGE, null);
    $('#link-swagger').href = API + '/swagger-ui/index.html';
    $('#login-erro').hidden = true;
    verificarServidor();
}

(async function iniciar() {
    registrarEventos();
    $('#link-swagger').href = (API || '') + '/swagger-ui/index.html';
    montarSeletorServidor();

    const salva = lerStorage(CHAVE_STORAGE);
    if (salva && await abrirSessao(salva)) {
        return;
    }
    gravarStorage(CHAVE_STORAGE, null);
    $('#tela-login').hidden = false;
    mostrarAcesso(location.hash === '#cadastro' ? 'cadastro' : 'login');
})();
