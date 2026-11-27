<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-texto-branca.png">
    <img src="assets/logo-texto-colorida.png" alt="Logo do Xepa+" width="280">
  </picture>
</p>

<div align="center">

# Xepa+ API

**API REST do Xepa+: liga o site dos feirantes e consumidores ao banco de dados.**

![Java 17](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Swagger](https://img.shields.io/badge/OpenAPI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)

**[Documentação interativa (Swagger)](https://xepamais.com.br/api/docs)** · **[Site](https://xepamais.com.br)** · **[Banco de dados](https://github.com/XepaMais/database)** · **[Front-end](https://github.com/XepaMais/frontend)**

</div>

## Sumário

1. [Sobre](#1-sobre)
2. [Tecnologias e técnicas](#2-tecnologias-e-técnicas)
3. [Arquitetura](#3-arquitetura)
4. [Autenticação e segurança](#4-autenticação-e-segurança)
5. [Formato das respostas e erros](#5-formato-das-respostas-e-erros)
6. [Endpoints](#6-endpoints)
7. [Persistência de dados](#7-persistência-de-dados)
8. [LGPD](#8-lgpd)
9. [Rodar localmente](#9-rodar-localmente)
10. [Testes](#10-testes)
11. [Publicação (AWS)](#11-publicação-aws)

---

## 1. Sobre

O Xepa+ conecta feirantes que têm sobras no fim da feira (as *xepas*) a consumidores que querem comprar alimento fresco mais barato. Esta API concentra **todas as regras de negócio**:
- cadastro e login;
- catálogo e mapa de feiras;
- carrinho, checkout, pagamento e retirada;
- chat entre consumidor e feirante;
- financeiro do feirante (lucros, gastos, saques, planilha);
- planos e comissão;
- assistente com IA (Xepinha);
- direitos de privacidade (LGPD).

O site em React nunca acessa o banco direto: tudo passa por aqui.

## 2. Tecnologias e técnicas

| Tecnologia | Uso no Xepa+ |
| --- | --- |
| **Java 17 + Spring Boot 4** | Aplicação e configuração |
| **Spring Web MVC** | Endpoints REST |
| **Spring Data JPA / Hibernate** | Acesso ao banco com entidades e repositórios |
| **MySQL 8** | Banco relacional (36 tabelas + 1 view) |
| **springdoc-openapi** | Documentação Swagger gerada a partir do código |
| **PBKDF2-HMAC-SHA256** | Hash das senhas (210.000 iterações, sal aleatório) |
| **HMAC-SHA256** | Assinatura do token de login |
| **Google Gemini** | Respostas da Xepinha (chamado só pelo servidor) |
| **JUnit 5 + MockMvc + H2** | 34 testes de integração |
| **Nginx + Let's Encrypt** | HTTPS na frente da API em produção |
| **Lombok, Maven Wrapper** | Menos código repetido; build sem instalar Maven |

**Boas práticas aplicadas**
- **Camadas:** cada módulo tem Controller (HTTP), Service (regras) e Repository (banco). Os DTOs são `record`s, então as entidades nunca saem direto na resposta. A senha, por exemplo, nunca aparece.
- **Lógica comum encapsulada em um lugar só:**
  - um interceptor confere o dono da conta em todas as rotas;
  - um tratador global padroniza os erros;
  - a sessão e o limite de tentativas são serviços reutilizados.
- **Transações (`@Transactional`):** o cadastro, o checkout e o financeiro gravam tudo ou nada.
- **Concorrência:**
  - `@Version` na xepa impede vender duas vezes a última unidade;
  - o saque trava a linha do feirante, então dois saques ao mesmo tempo não passam do saldo.
- **Atualização do banco sem parar o site:** `AtualizacaoBanco` cria no MySQL as colunas e tabelas novas ao iniciar, conferindo antes o que já existe.

## 3. Arquitetura

```text
Navegador ──HTTPS──> Vercel (site React) ──HTTPS /api──> Nginx (api.xepamais.com.br)
                                                           └─> Spring Boot :8080 (só local) ──> MySQL 8
```

```text
src/main/java/br/com/xepamais/api/
├── auth/          cadastro, login, token, sessões, limite de tentativas, hash de senha
├── conta/         perfil, senha, dispositivos conectados, privacidade (LGPD)
├── consumidor/    carrinho, endereço, checkout, pedidos, impacto; vendas do feirante
├── xepa/          catálogo e ofertas do feirante
├── feira/         mapa, feiras próximas, feiras do feirante
├── financeiro/    rendas, despesas, saques, resumo, histórico e planilha CSV
├── chat/          conversas consumidor ↔ feirante
├── avaliacao/     avaliação de pedidos e perfil público do feirante
├── plano/         planos e comissão
├── receita/       receitas para aproveitar a xepa
├── xepinha/       assistente com IA (Gemini)
├── imagem/        envio e leitura de fotos
├── suporte/       formulário de suporte
├── admin/         painel administrativo (token próprio)
├── config/        CORS, Swagger, tratador de erros, atualização do banco
└── database/      entidades JPA (model) e repositórios (repository)
```

## 4. Autenticação e segurança

1. `POST /api/auth/login` (ou o cadastro) devolve um **token** assinado pela API, válido por 30 dias.
2. O site envia o token em toda rota da conta: `Authorization: Bearer <token>`.
3. O `ContaInterceptor` confere:
   - a assinatura e a validade do token;
   - se a sessão continua ativa (não foi desconectada em "Dispositivos conectados");
   - se o `{idConta}` da rota é **o mesmo** do token.

| Situação | Resposta |
| --- | --- |
| Sem token, token inválido ou sessão encerrada | `401` `"Sessão expirada. Entre novamente."` |
| Token de outra conta | `403` `"Você não tem acesso a esta conta."` |
| 5 senhas erradas para o mesmo e-mail | `429` por 15 minutos |

**Outras proteções:**
- **HTTPS** de ponta a ponta. A API só ouve dentro do servidor, atrás do Nginx.
- **CORS** liberado só para os domínios do site (`APP_CORS_ALLOWED_ORIGINS`).
- **Segredos** em variáveis de ambiente, nunca no Git: banco, chave do token, chave do Gemini e login do admin.
- **Demo da Xepinha** com limite por visitante e teto geral por hora.
- **Painel admin** com login próprio, hash da senha e bloqueio depois de 10 tentativas.

## 5. Formato das respostas e erros

- Corpo em **JSON (UTF-8)**.
- Datas no formato `AAAA-MM-DD` e data e hora em ISO-8601.
- Valores em reais como número com ponto decimal (`12.50`).
- A planilha (`/planilha`) é CSV para Excel (`;` e vírgula decimal).

**Todos os erros** seguem o mesmo formato (`config/TratadorErros.java`):

```json
{
  "status": 400,
  "erro": "Bad Request",
  "message": "Para criar a conta, aceite os Termos de Uso e a Política de Privacidade.",
  "caminho": "/api/auth/cadastro-consumidor",
  "momento": "2026-11-27T10:00:00"
}
```

O `message` é o texto mostrado ao usuário. Se a conexão cair, o site mostra "Sem conexão com o servidor. Verifique sua internet e tente de novo."

| Código | Quando |
| --- | --- |
| `200` / `201` / `204` | Sucesso / criado / sem conteúdo |
| `400` | Dados inválidos ou JSON malformado |
| `401` / `403` | Sem sessão / conta de outra pessoa |
| `404` | Recurso ou rota inexistente |
| `409` | Conflito (e-mail já cadastrado, xepa alterada por outra pessoa) |
| `429` | Muitas tentativas |
| `503` | IA indisponível ou painel admin não configurado |

## 6. Endpoints

São 84 endpoints. Os parâmetros e exemplos de cada um estão no **[Swagger](https://xepamais.com.br/api/docs)** (JSON OpenAPI em `/api/docs/openapi`). Legenda de acesso: 🌐 público · 🔒 dono da conta (token) · 🛠 admin.

### Autenticação
| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| POST | `/api/auth/cadastro-consumidor` | 🌐 | Cria consumidor: `nome, cpf, email, senha, telefone, aceiteTermos (obrigatório), aceiteMarketing` |
| POST | `/api/auth/cadastro-feirante` | 🌐 | Cria feirante: os campos acima mais `nomeFantasia, cnpj, ccm, matricula, grupoComercio` |
| POST | `/api/auth/login` | 🌐 | `email, senha` → `id, nome, email, tipo, token, plano` |

### Catálogo, mapa e conteúdo público
| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| GET | `/api/xepas?busca=&categoria=&idFeira=` | 🌐 | Xepas à venda (com estoque e não vencidas) |
| GET | `/api/xepas/categorias` | 🌐 | Categorias |
| GET | `/api/xepas/{id}` | 🌐 | Detalhe da xepa |
| GET | `/api/feiras?busca=` | 🌐 | Feiras do mapa (busca sem acento) |
| GET | `/api/feiras/proximas?lat=&lng=&raioKm=` | 🌐 | Feiras perto de um ponto, da mais perto para a mais longe |
| GET | `/api/feirantes/{idConta}` | 🌐 | Perfil público do feirante com avaliações |
| GET | `/api/planos` | 🌐 | Planos Semente, Broto e Colheita |
| GET | `/api/receitas` · `/api/receitas/{id}` | 🌐 | Receitas |
| GET | `/api/imagens/{id}` | 🌐 | Foto |
| POST | `/api/imagens` | 🔒 | Envia foto (`dataUrl` já reduzida) |
| POST | `/api/suporte` | 🌐 | Mensagem de suporte |
| POST | `/api/xepinha/demo` | 🌐 | Xepinha no modo demonstração (limitado) |
| GET | `/api/financeiro/categorias` | 🌐 | Origens de renda e categorias de gasto |

### Conta, dispositivos e privacidade (LGPD)
| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| GET / PUT | `/api/conta/{idConta}/perfil` | 🔒 | Ver e editar perfil (e dados da banca) |
| PUT | `/api/conta/{idConta}/senha` | 🔒 | Trocar senha (`senhaAtual, novaSenha`) |
| GET | `/api/conta/{idConta}/dispositivos` | 🔒 | Aparelhos conectados |
| DELETE | `/api/conta/{idConta}/dispositivos/{id}` | 🔒 | Desconecta um aparelho |
| DELETE | `/api/conta/{idConta}/dispositivos` | 🔒 | Desconecta todos os outros |
| POST | `/api/conta/{idConta}/sair` | 🔒 | Encerra o login deste aparelho |
| GET | `/api/conta/{idConta}/dados` | 🔒 | **Baixa todos os dados pessoais (JSON)** |
| GET / PUT | `/api/conta/{idConta}/consentimento` | 🔒 | Data e versão do aceite; liga ou desliga as novidades por e-mail |
| DELETE | `/api/conta/{idConta}` | 🔒 | **Exclui a conta** (`{ "senha" }`): anonimiza e desconecta tudo |

### Consumidor
| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| GET / PUT / DELETE | `/api/consumidor/{idConta}/carrinho` | 🔒 | Ver, substituir e esvaziar o carrinho |
| PUT / DELETE | `/api/consumidor/{idConta}/carrinho/itens/{idXepa}` | 🔒 | Definir quantidade / remover item |
| GET / PUT | `/api/consumidor/{idConta}/endereco` | 🔒 | Retirada ou entrega e endereço |
| POST | `/api/consumidor/{idConta}/pedidos` | 🔒 | **Checkout:** um pedido por feirante, pagamento, renda do feirante e chat |
| GET | `/api/consumidor/{idConta}/pedidos` · `/pedidos/{id}` | 🔒 | Histórico e detalhe (com código de retirada) |
| POST | `/api/consumidor/{idConta}/pedidos/{id}/cancelar` | 🔒 | Cancela antes da retirada (estorno) |
| PUT | `/api/consumidor/{idConta}/pedidos/{id}/avaliacao` | 🔒 | Nota de 1 a 5 e comentário |
| GET | `/api/consumidor/{idConta}/impacto` | 🔒 | Kg salvos, economia e refeições |

### Feirante
| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| GET / POST | `/api/feirante/{idConta}/xepas` | 🔒 | Minhas xepas / publicar xepa |
| PUT / DELETE | `/api/feirante/{idConta}/xepas/{id}` | 🔒 | Editar / cancelar xepa |
| GET / POST | `/api/feiras/feirante/{idConta}` | 🔒 | Minhas feiras (com ajudantes) / adicionar |
| PUT / DELETE | `/api/feiras/feirante/{idConta}/{idFeira}` | 🔒 | Editar / sair da feira |
| GET | `/api/feirante/{idConta}/pedidos` | 🔒 | Vendas recebidas |
| POST | `/api/feirante/{idConta}/pedidos/{id}/retirada` | 🔒 | Confirma retirada com o código do consumidor |
| POST | `/api/feirante/{idConta}/pedidos/{id}/cancelar` | 🔒 | Cancela a venda |
| GET / PUT | `/api/feirante/{idConta}/plano` | 🔒 | Plano atual / trocar plano |
| GET | `/api/feirante/{idConta}/xepinha/conversas` | 🔒 | Conversas com a Xepinha (Broto e Colheita) |
| POST | `/api/feirante/{idConta}/xepinha/mensagens` | 🔒 | Pergunta à Xepinha |

### Financeiro do feirante
| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| GET / POST | `/api/financeiro/{idConta}/rendas?inicio=&fim=` | 🔒 | Lucros (`origem, descricao, valor, data`) |
| PUT / DELETE | `/api/financeiro/{idConta}/rendas/{id}` | 🔒 | Editar / excluir. Rendas de pedidos não podem ser alteradas |
| GET / POST | `/api/financeiro/{idConta}/despesas?inicio=&fim=` | 🔒 | Gastos (`categoria, descricao, quantidade, valorUnitario, data`) |
| PUT / DELETE | `/api/financeiro/{idConta}/despesas/{id}` | 🔒 | Editar / excluir |
| GET | `/api/financeiro/{idConta}/saldo` | 🔒 | Recebido pelo app, sacado e disponível |
| GET / POST | `/api/financeiro/{idConta}/saques` | 🔒 | Histórico / pedir saque via Pix (`valor, tipoChave, chave`) |
| GET | `/api/financeiro/{idConta}/resumo` | 🔒 | Hoje, semana e mês |
| GET | `/api/financeiro/{idConta}/historico?inicio=&fim=&tipo=&categoria=&busca=` | 🔒 | Lucros e gastos com totais |
| GET | `/api/financeiro/{idConta}/planilha?...` | 🔒 | O mesmo histórico em CSV |

### Conversas (consumidor ↔ feirante)
`{papel}` é `consumidor` ou `feirante`.

| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| GET | `/api/{papel}/{idConta}/conversas` · `/conversas/{idChat}` | 🔒 | Lista e conversa |
| POST | `/api/consumidor/{idConta}/conversas` | 🔒 | Abre dúvida sobre uma xepa (`idXepa`) |
| POST | `/api/{papel}/{idConta}/conversas/{idChat}/mensagens` | 🔒 | Envia mensagem |
| POST | `/api/{papel}/{idConta}/conversas/{idChat}/leitura` | 🔒 | Marca como lida |
| POST | `/api/{papel}/{idConta}/conversas/{idChat}/finalizar` | 🔒 | Finaliza a conversa |

### Admin
| Método | Caminho | | Descrição |
| --- | --- | --- | --- |
| POST | `/api/admin/login` | 🌐 | Login do painel (`ADMIN_EMAIL` / `ADMIN_SENHA_HASH`) |
| GET | `/api/admin/usuarios` | 🛠 | Resumo de usuários |
| GET | `/api/admin/tabelas` · `/tabelas/{tabela}` | 🛠 | Tabelas e linhas (colunas sensíveis ocultas) |
| POST / PUT / DELETE | `/api/admin/tabelas/{tabela}[/{id}]` | 🛠 | Manutenção de dados |

> **Sobre as ações com POST** (`/cancelar`, `/retirada`, `/leitura`, `/finalizar`, `/sair`): são operações que mudam o estado e disparam efeitos colaterais, como estorno, renda, finalização do chat e encerramento de sessão. Por isso usam POST em sub-recursos de ação, em vez de um PUT genérico de status.

## 7. Persistência de dados

- **Onde:** MySQL 8 (`db_xepa`). A estrutura, os seeds e o dicionário de dados estão no **[repositório database](https://github.com/XepaMais/database)**.
- **Como:** Spring Data JPA com `ddl-auto: none`. A estrutura é controlada pelos scripts do banco e pelo `config/AtualizacaoBanco.java`, que cria o que falta ao iniciar.
- **Entre sessões e aparelhos:** perfil, carrinho, pedidos, feiras, xepas, financeiro, conversas e fotos ficam no banco. O usuário encontra tudo ao entrar de outro aparelho.
- **Velocidade:**
  - índices compostos para catálogo, financeiro, chat e vendas;
  - o catálogo carrega feirante, produto e feira na mesma consulta (`join fetch`);
  - o total da despesa é calculado e guardado pelo banco.
- **Dados iniciais:** se faltarem, a API cria os planos (`PlanosIniciais`) e as receitas (`ReceitasIniciais`).
- **Crescimento:** veja o [plano de escalabilidade](https://github.com/XepaMais/documentacao/blob/main/10-escalabilidade.md).

## 8. LGPD

- O **cadastro exige o aceite** dos Termos e da Política de Privacidade. A data e a versão (`LGPD_VERSAO_TERMOS`) ficam gravadas na conta.
- O **consentimento de marketing** é opcional e revogável: `PUT /api/conta/{id}/consentimento`.
- **Acesso e portabilidade:** `GET /api/conta/{id}/dados`.
- **Eliminação:** `DELETE /api/conta/{id}` anonimiza os dados pessoais, tira as ofertas do ar e encerra as sessões. Pedidos e pagamentos ficam guardados sem identificar a pessoa, por obrigação fiscal.
- Política completa: [11-lgpd](https://github.com/XepaMais/documentacao/blob/main/11-lgpd.md).

## 9. Rodar localmente

**Pré-requisitos:** Java 17 e MySQL 8. Crie o banco com os scripts do [repositório database](https://github.com/XepaMais/database) (01 a 04). Na pasta `Xepa` da equipe, o `configurar-banco-local.ps1` faz isso.

| Variável | Obrigatória | Exemplo / padrão |
| --- | --- | --- |
| `DB_URL` | sim | `jdbc:mysql://localhost:3306/db_xepa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo` |
| `DB_USER` / `DB_PASSWORD` | sim | usuário do MySQL |
| `AUTH_TOKEN_SECRET` | em produção | Chave do token. Vazia = sorteada ao iniciar (reiniciar desloga todos) |
| `APP_CORS_ALLOWED_ORIGINS` | não | domínios do site + `http://localhost:5173` |
| `GEMINI_API_KEY` | não | Sem chave, a Xepinha responde que está indisponível |
| `ADMIN_EMAIL` / `ADMIN_SENHA_HASH` | não | Sem eles, o painel `/admin` fica desligado |
| `LGPD_VERSAO_TERMOS` | não | `1.0` |
| `SERVER_PORT` / `SERVER_ADDRESS` | não | `8080` / todas as interfaces (em produção: `127.0.0.1`) |
| `SHOW_SQL` | não | `false` |

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/db_xepa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo"
$env:DB_USER = "root"
$env:DB_PASSWORD = "SUA_SENHA"
.\mvnw.cmd spring-boot:run
```

Swagger local: http://localhost:8080/api/docs

**Conta de teste** (com o seed de demonstração): `antonio.nakamura@demo.xepamais.com.br` / `XepaDemo@2026`.

## 10. Testes

```powershell
.\mvnw.cmd test
```

São **34 testes de integração** (JUnit 5 + MockMvc), rodando com H2 em memória no modo MySQL, sem precisar de banco instalado. Eles cobrem:
- cadastro com aceite obrigatório e limite de tentativas de login;
- exportação, consentimento e exclusão de conta (LGPD);
- formato padrão de erro e documentação OpenAPI;
- carrinho, checkout e estoque (sem vender mais que o disponível);
- edição e cancelamento de xepas e pedidos;
- financeiro e saques;
- feiras, planos e endereço;
- chat, fotos, avaliações e receitas;
- dispositivos conectados e Xepinha, com a IA simulada.

## 11. Publicação (AWS)

| Script | O que faz |
| --- | --- |
| [`deploy/setup-ec2.sh`](deploy/setup-ec2.sh) | Prepara uma EC2 nova: Java, MySQL, usuário de serviço, `/etc/xepamais/api.env`, serviço systemd |
| [`deploy/setup-https.sh`](deploy/setup-https.sh) | Nginx + certificado Let's Encrypt em `api.xepamais.com.br` |
| [`deploy/deploy.ps1`](deploy/deploy.ps1) | Gera o `.jar`, envia para o servidor e reinicia a API |

```powershell
.\deploy\deploy.ps1 -Servidor <IP-da-EC2> -Chave <caminho-da-chave.pem>
```

Logs no servidor: `sudo journalctl -u xepamais-api -f`.
