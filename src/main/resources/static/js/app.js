'use strict';

/* =====================================================================
 * HookFishing - front-end
 *
 * Consome a API REST demonstrando os recursos da Parte 2:
 *   X-API-Key          -> obtida no login (e-mail e senha); define o papel do usuário
 *   X-Idempotency-Key  -> gerada automaticamente em todo cadastro (POST)
 *   X-API-Version      -> alternância v1 / v2 na aba Peixes
 *   X-RateLimit-*      -> barra de rate limit no topo e aviso de 429 com Retry-After
 *   CORS               -> funciona também se o front-end for aberto em outra origem permitida
 * ===================================================================== */

// Quando servido pela própria API (porta 8080) usa caminhos relativos; caso contrário, chama a API em localhost:8080.
const API = window.HOOKFISHING_API
    ?? (location.protocol !== 'file:' && location.port === '8080' ? '' : 'http://localhost:8080');

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
    try {
        resposta = await fetch(API + caminho, { method: metodo, headers, body: corpo });
    } catch (e) {
        const falha = {
            ok: false, status: 0, headers: new Headers(),
            dados: { mensagem: 'Não foi possível conectar à API em ' + (API || location.origin)
                    + '. Verifique se a aplicação está rodando (e, se o front-end estiver em outra origem, se ela está liberada no CORS).' }
        };
        registrarLog(metodo, caminho, falha, headers);
        return falha;
    }

    const texto = await resposta.text();
    let dados = null;
    if (texto) {
        try { dados = JSON.parse(texto); } catch { dados = { mensagem: texto }; }
    }

    const resultado = { ok: resposta.ok, status: resposta.status, dados, headers: resposta.headers };
    atualizarRateLimit(resposta.headers);
    registrarLog(metodo, caminho, resultado, headers);

    if (resposta.status === 429) {
        mostrarAviso429(Number(resposta.headers.get('Retry-After') || 60));
    }
    return resultado;
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
    $('#limite-texto').textContent = `${restantes}/${limite}`;
    $('.limite').title = `Rate limiting (token bucket): ${restantes} de ${limite} fichas restantes. O balde enche em ${reset}s.`;
}

function mostrarAviso429(segundos) {
    const aviso = $('#aviso-429');
    clearInterval(estado.timer429);
    let restante = segundos;
    const atualizar = () => {
        aviso.hidden = false;
        aviso.textContent = `Limite de requisições excedido (HTTP 429). `
            + `Retry-After: tente novamente em ${restante}s.`;
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

const HEADERS_LOG = ['X-API-Version', 'X-RateLimit-Remaining', 'Retry-After', 'X-Idempotent-Replayed'];

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
    montarAbas();
    selecionarAba(estado.aba in ENTIDADES && pode(estado.aba, 'CONSULTAR') ? estado.aba : 'peixes');
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
        `<button type="button" class="aba aba--destaque" data-aba="testes">Testes da Parte 2</button>`);
}

function selecionarAba(id) {
    estado.aba = id;
    $$('.aba').forEach(b => b.classList.toggle('ativa', b.dataset.aba === id));

    if (id === 'testes') {
        $('#painel-entidade').hidden = true;
        $('#painel-testes').hidden = false;
        montarTestes();
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
 * Painel "Testes da Parte 2"
 *
 * Cada cenário faz requisições reais e compara o status obtido com o esperado.
 * ------------------------------------------------------------------- */

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

function corpo(r) {
    if (!r?.dados) return '(sem corpo)';
    const { _links, ...resto } = r.dados;
    return json(resto);
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
        id: 'idempotencia', titulo: 'Idempotência', requisito: '4.1',
        descricao: 'Requisições POST com a mesma X-Idempotency-Key não criam registros duplicados.',
        casos: ['idem-repetir', 'idem-outro-corpo', 'idem-sem-chave']
    },
    {
        id: 'autenticacao', titulo: 'Autenticação e papéis', requisito: '4.2',
        descricao: 'A API exige X-API-Key e libera cada operação conforme o papel da chave.',
        casos: ['auth-sem', 'auth-invalida', 'auth-login', 'auth-cuidador', 'auth-visitante', 'auth-biologo', 'auth-me']
    },
    {
        id: 'rate', titulo: 'Limite de requisições', requisito: '4.3',
        descricao: 'Token bucket: cada chave tem um balde de 60 fichas, reabastecido com 1 ficha por segundo. '
            + 'Sem fichas, a API responde 429 com o header Retry-After. Este cenário esvazia o balde da chave atual '
            + 'e não entra em "Executar todos".',
        casos: ['rate']
    },
    {
        id: 'cors', titulo: 'CORS', requisito: '4.4',
        descricao: 'Apenas as origens configuradas podem chamar a API pelo navegador.',
        casos: ['cors']
    },
    {
        id: 'versionamento', titulo: 'Versionamento', requisito: '4.5',
        descricao: 'O header X-API-Version escolhe a versão da resposta de GET /peixes e GET /peixes/{id}.',
        casos: ['versao-comparar', 'versao-invalida']
    },
    {
        id: 'erros', titulo: 'Validações e tratamento de erros', requisito: '5',
        descricao: 'Erros são tratados pelo @ControllerAdvice e seguem sempre o mesmo formato.',
        casos: ['erro-validacao', 'erro-enum', 'erro-404', 'erro-405', 'erro-409']
    }
];

const CASOS = {
    'idem-repetir': {
        nome: 'Mesmo POST enviado duas vezes',
        requisicao: 'POST /alimentacoes (2x, mesma chave)',
        esperado: '201 nas duas, mesmo id',
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
        nome: 'Mesma chave com outro corpo',
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
        nome: 'POST sem X-Idempotency-Key',
        requisicao: 'POST /alimentacoes',
        esperado: '400',
        async executar() {
            const r = await api('POST', '/alimentacoes', { chave: chaveParaCadastro(), corpo: { nome: 'Sem chave', tipo: 'RACAO' } });
            return resultadoSimples(r, 400);
        }
    },
    'auth-sem': {
        nome: 'Requisição sem X-API-Key',
        requisicao: 'GET /peixes',
        esperado: '401',
        async executar() {
            const r = await api('GET', '/peixes', { semChave: true });
            return resultadoSimples(r, 401, `WWW-Authenticate: ${r.headers.get('WWW-Authenticate') ?? '-'}`);
        }
    },
    'auth-invalida': {
        nome: 'Chave inexistente',
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
        nome: 'CUIDADOR excluindo um peixe',
        requisicao: 'DELETE /peixes/{id}',
        esperado: '403',
        async executar() {
            const peixe = await primeiroItem('/peixes', 'peixes');
            const r = await api('DELETE', `/peixes/${peixe?.id ?? 1}`, { chave: CHAVES_DEMO.CUIDADOR });
            return resultadoSimples(r, 403, `Chave usada: ${CHAVES_DEMO.CUIDADOR}`);
        }
    },
    'auth-visitante': {
        nome: 'VISITANTE cadastrando alimentação',
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
        nome: 'BIOLOGO alterando um aquário',
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
        nome: 'Consulta da própria chave',
        requisicao: 'GET /api-keys/me',
        esperado: '200',
        async executar() {
            return resultadoSimples(await api('GET', '/api-keys/me'), 200);
        }
    },
    rate: {
        nome: 'Requisições seguidas até o limite',
        requisicao: 'GET /especies (repetido)',
        esperado: '429 + Retry-After',
        async executar() {
            const contagem = {};
            let bloqueio = null;
            let total = 0;
            for (let i = 1; i <= 200; i++) {
                const r = await api('GET', '/especies?size=1');
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
                detalhe: `${total} requisições: ${resumo}\n`
                    + `Retry-After: ${retry}\n`
                    + `X-RateLimit-Limit: ${bloqueio.headers.get('X-RateLimit-Limit')}\n`
                    + `X-RateLimit-Remaining: ${bloqueio.headers.get('X-RateLimit-Remaining')}\n\n`
                    + corpo(bloqueio)
            };
        }
    },
    cors: {
        nome: 'Origem do front-end',
        requisicao: 'GET /peixes',
        esperado: 'origem permitida',
        async executar() {
            const r = await api('GET', '/peixes?size=1');
            const outraOrigem = API !== '' && !API.startsWith(location.origin);
            const permitido = r.headers.get('Access-Control-Allow-Origin');
            const detalhe = [
                `Origem do front-end: ${location.origin}`,
                `Endereço da API: ${API || location.origin}`,
                `Access-Control-Allow-Origin: ${permitido ?? '(não enviado: mesma origem)'}`,
                '',
                outraOrigem
                    ? 'O front-end está em outra origem e a chamada funcionou: a API liberou esta origem.'
                    : 'O front-end está na mesma origem da API, então o navegador não aplica CORS. '
                        + 'Para ver o CORS em ação, abra o index.html pelo Live Server (porta 5500).',
                '',
                'Preflight pelo terminal:',
                'curl -i -X OPTIONS http://localhost:8080/peixes \\',
                '  -H "Origin: http://localhost:5500" \\',
                '  -H "Access-Control-Request-Method: POST" \\',
                '  -H "Access-Control-Request-Headers: X-API-Key, X-Idempotency-Key, Content-Type"'
            ].join('\n');
            return {
                passou: r.ok,
                obtido: outraOrigem ? `HTTP ${r.status}, ${permitido ?? 'sem header'}` : `HTTP ${r.status}, mesma origem`,
                detalhe
            };
        }
    },
    'versao-comparar': {
        nome: 'Mesmo peixe em v1 e v2',
        requisicao: 'GET /peixes/{id}',
        esperado: '200 e 200, formatos diferentes',
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
        nome: 'Versão inexistente',
        requisicao: 'GET /peixes com X-API-Version: 3',
        esperado: '400',
        async executar() {
            return resultadoSimples(await api('GET', '/peixes?size=1', { headers: { 'X-API-Version': '3' } }), 400);
        }
    },
    'erro-validacao': {
        nome: 'Campos inválidos',
        requisicao: 'POST /peixes',
        esperado: '400 com lista de campos',
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
        nome: 'Método não suportado',
        requisicao: 'PATCH /peixes/1',
        esperado: '405',
        async executar() {
            return resultadoSimples(await api('PATCH', '/peixes/1', { corpo: { nome: 'Nemo' } }), 405);
        }
    },
    'erro-409': {
        nome: 'Nome duplicado',
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

function montarTestes() {
    const permissoes = Object.entries(estado.usuario?.permissoes || {})
        .map(([recurso, ops]) => `<tr><td>${esc(ROTULOS_RECURSOS[recurso] || recurso)}</td>`
            + `<td>${ops.length ? esc(ops.map(o => OPERACOES[o] || o).join(', ')) : '<span class="id">sem acesso</span>'}</td></tr>`)
        .join('');

    $('#painel-testes').innerHTML = `
        <div class="painel__cabecalho">
            <div>
                <h1>Testes da Parte 2</h1>
                <p class="painel__descricao">Cenários que verificam os requisitos da Parte 2 com requisições reais à API.
                    O resultado compara o status HTTP obtido com o esperado.</p>
            </div>
            <div class="testes__acoes">
                <span id="testes-resumo" class="testes__resumo"></span>
                <button id="btn-executar-todos" class="btn btn--primario" type="button">Executar todos</button>
            </div>
        </div>

        <div class="testes__conta">
            <div>
                <span class="testes__rotulo">Conta atual</span>
                <b>${esc(estado.usuario?.nome)}</b>
                <span class="selo selo--${esc(estado.usuario?.papel)}">${esc(estado.usuario?.papel)}</span>
            </div>
            <table class="permissoes-tabela"><tbody>${permissoes}</tbody></table>
        </div>

        ${GRUPOS_TESTE.map(grupo => `
            <section class="grupo-teste">
                <header class="grupo-teste__cabecalho">
                    <h2>${esc(grupo.titulo)}</h2>
                </header>
                <p class="grupo-teste__descricao">${esc(grupo.descricao)}</p>
                <div class="tabela-wrap">
                    <table class="tabela tabela--testes">
                        <thead><tr><th>Cenário</th><th>Requisição</th><th>Esperado</th><th>Resultado</th><th></th></tr></thead>
                        <tbody>
                        ${grupo.casos.map(id => `
                            <tr data-caso="${id}">
                                <td>${esc(CASOS[id].nome)}</td>
                                <td><code>${esc(CASOS[id].requisicao)}</code></td>
                                <td>${esc(CASOS[id].esperado)}</td>
                                <td class="caso__resultado"><span class="status status--pendente">Não executado</span></td>
                                <td class="acoes">
                                    <button class="btn btn--pequeno" type="button" data-executar="${id}">Executar</button>
                                    <button class="btn btn--pequeno btn--fantasma-escuro" type="button" data-detalhe="${id}" hidden>Detalhes</button>
                                </td>
                            </tr>
                            <tr class="caso__detalhe" data-detalhe-de="${id}" hidden>
                                <td colspan="5"><pre></pre></td>
                            </tr>`).join('')}
                        </tbody>
                    </table>
                </div>
            </section>`).join('')}`;
    atualizarResumoTestes();
}

async function executarCaso(id) {
    const linha = $(`tr[data-caso="${id}"]`);
    const celula = $('.caso__resultado', linha);
    const botao = $(`[data-executar="${id}"]`, linha);
    botao.disabled = true;
    celula.innerHTML = '<span class="status status--rodando">Executando</span>';

    let resultado;
    try {
        resultado = await CASOS[id].executar();
    } catch (e) {
        resultado = { passou: false, obtido: 'erro no teste', detalhe: String(e) };
    }

    linha.dataset.situacao = resultado.passou ? 'passou' : 'falhou';
    celula.innerHTML = `<span class="status status--${resultado.passou ? 'ok' : 'falha'}">${resultado.passou ? 'Passou' : 'Falhou'}</span>`
        + `<span class="caso__obtido">${esc(resultado.obtido)}</span>`;
    $(`tr[data-detalhe-de="${id}"] pre`).textContent = resultado.detalhe || '';
    $(`[data-detalhe="${id}"]`, linha).hidden = !resultado.detalhe;
    botao.disabled = false;
    atualizarResumoTestes();
}

function atualizarResumoTestes() {
    const linhas = $$('tr[data-caso]');
    const executados = linhas.filter(l => l.dataset.situacao);
    const passaram = executados.filter(l => l.dataset.situacao === 'passou').length;
    const resumo = $('#testes-resumo');
    if (resumo) {
        resumo.textContent = executados.length
            ? `${passaram} de ${executados.length} cenário(s) executado(s) passaram`
            : `${linhas.length} cenários`;
    }
}

async function executarTodos() {
    const botao = $('#btn-executar-todos');
    botao.disabled = true;
    try {
        for (const grupo of GRUPOS_TESTE) {
            if (grupo.id === 'rate') continue;
            for (const id of grupo.casos) {
                await executarCaso(id);
            }
        }
    } finally {
        botao.disabled = false;
    }
}

/* ---------------------------------------------------------------------
 * Eventos
 * ------------------------------------------------------------------- */

function registrarEventos() {
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

    $('#modal-form').addEventListener('submit', salvarFormulario);
    $$('[data-fechar]').forEach(b => b.addEventListener('click', () => $('#modal').close()));

    $('#painel-testes').addEventListener('click', e => {
        const executar = e.target.closest('[data-executar]');
        if (executar) {
            executarCaso(executar.dataset.executar);
            return;
        }
        const detalhe = e.target.closest('[data-detalhe]');
        if (detalhe) {
            const linha = $(`tr[data-detalhe-de="${detalhe.dataset.detalhe}"]`);
            linha.hidden = !linha.hidden;
            detalhe.textContent = linha.hidden ? 'Detalhes' : 'Ocultar';
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

(async function iniciar() {
    registrarEventos();
    $('#link-swagger').href = (API || '') + '/swagger-ui/index.html';

    const salva = lerStorage(CHAVE_STORAGE);
    if (salva && await abrirSessao(salva)) {
        return;
    }
    gravarStorage(CHAVE_STORAGE, null);
    $('#tela-login').hidden = false;
    mostrarAcesso(location.hash === '#cadastro' ? 'cadastro' : 'login');
})();
