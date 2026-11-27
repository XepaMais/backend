<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-texto-branca.png">
    <img src="assets/logo-texto-colorida.png" alt="Logo do Xepa+" width="280">
  </picture>
</p>

<div align="center">

# API

**API REST do Xepa+: liga o site dos feirantes e consumidores ao banco de dados.**

![Java 17](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)

**[Swagger](https://xepamais.com.br/api/docs)** · **[Site](https://xepamais.com.br)** · **[Banco de dados](https://github.com/XepaMais/database)** · **[Front-end](https://github.com/XepaMais/frontend)**

</div>

## Sumário

1. [Sobre](#sobre)
2. [Tecnologias](#tecnologias)
3. [Arquitetura](#arquitetura)
4. [Autenticação e segurança](#autenticação-e-segurança)
5. [Erros](#erros)
6. [Endpoints principais](#endpoints-principais)
7. [Banco de dados](#banco-de-dados)
8. [LGPD](#lgpd)
9. [Rodar localmente](#rodar-localmente)
10. [Testes](#testes)
11. [Publicação (AWS)](#publicação-aws)

## Sobre

O Xepa+ conecta feirantes que têm sobras no fim da feira (as *xepas*) a consumidores que querem comprar alimento fresco mais barato. A API concentra as regras de negócio:
- cadastro e login;
- catálogo e mapa de feiras;
- carrinho, checkout, pagamento e retirada;
- chat entre consumidor e feirante;
- financeiro do feirante (lucros, gastos, saques e planilha);
- planos e comissão;
- assistente com IA (Xepinha);
- privacidade (LGPD).

O site em React nunca acessa o banco direto: tudo passa pela API.

## Tecnologias

| Tecnologia | Uso |
| --- | --- |
| **Java 17 + Spring Boot 4** | Aplicação e endpoints REST |
| **Spring Data JPA / Hibernate** | Acesso ao banco |
| **MySQL 8** | Banco de dados |
| **springdoc-openapi** | Documentação Swagger |
| **PBKDF2 e HMAC-SHA256** | Hash das senhas e assinatura do token de login |
| **Google Gemini** | Respostas da Xepinha |
| **JUnit 5 + MockMvc + H2** | Testes de integração |
| **Nginx + Let's Encrypt** | HTTPS em produção |

## Arquitetura

```text
Navegador ──HTTPS──> Vercel (site React) ──HTTPS /api──> Nginx ──> Spring Boot :8080 ──> MySQL 8
```

Cada módulo tem **Controller** (recebe a requisição), **Service** (regras) e **Repository** (banco).

```text
src/main/java/br/com/xepamais/api/
├── auth/          cadastro, login, token e sessões
├── conta/         perfil, senha, dispositivos e LGPD
├── consumidor/    carrinho, checkout e pedidos
├── xepa/          catálogo e ofertas do feirante
├── feira/         mapa e feiras do feirante
├── financeiro/    rendas, despesas, saques e planilha
├── chat/          conversas consumidor ↔ feirante
├── avaliacao/     avaliações e perfil público do feirante
├── plano/         planos e comissão
├── receita/       receitas
├── xepinha/       assistente com IA
├── imagem/        fotos
├── suporte/       formulário de suporte
├── admin/         painel administrativo
├── config/        CORS, Swagger, erros e atualização do banco
└── database/      entidades e repositórios
```

## Autenticação e segurança

1. O login (`POST /api/auth/login`) devolve um **token** válido por 30 dias.
2. O site manda o token em toda rota da conta: `Authorization: Bearer <token>`.
3. A API confere se o token é válido, se a sessão ainda está ativa e se o `{idConta}` da rota é o mesmo do token.

| Situação | Resposta |
| --- | --- |
| Sem token ou sessão encerrada | `401` "Sessão expirada. Entre novamente." |
| Token de outra conta | `403` |
| 5 senhas erradas para o mesmo e-mail | `429` por 15 minutos |

Também tem: HTTPS, CORS só para os domínios do site, segredos em variáveis de ambiente (nunca no Git), limite de perguntas na demo da Xepinha e login separado para o painel admin.

## Erros

Todos os erros saem no mesmo formato. O campo `message` é o texto mostrado ao usuário.

```json
{
  "status": 400,
  "erro": "Bad Request",
  "message": "Para criar a conta, aceite os Termos de Uso e a Política de Privacidade.",
  "caminho": "/api/auth/cadastro-consumidor",
  "momento": "2026-11-27T10:00:00"
}
```

| Código | Quando |
| --- | --- |
| `400` | Dados inválidos |
| `401` / `403` | Sem sessão / conta de outra pessoa |
| `404` | Não encontrado |
| `409` | Conflito (ex.: e-mail já cadastrado) |
| `429` | Muitas tentativas |
| `503` | IA indisponível |

## Endpoints principais

A lista completa (84 endpoints, com exemplos) está no **[Swagger](https://xepamais.com.br/api/docs)**. 🌐 = público · 🔒 = precisa do token da conta.

| Área | Caminho | | O que faz |
| --- | --- | --- | --- |
| Login | `POST /api/auth/cadastro-consumidor` · `/cadastro-feirante` · `/login` | 🌐 | Cadastro e login |
| Catálogo | `GET /api/xepas` · `/api/xepas/{id}` | 🌐 | Xepas à venda e detalhe |
| Feiras | `GET /api/feiras` · `/api/feiras/proximas?lat=&lng=&raioKm=` | 🌐 | Mapa e feiras perto |
| Conta | `/api/conta/{idConta}/perfil` · `/senha` · `/dispositivos` | 🔒 | Perfil, senha e aparelhos conectados |
| Carrinho | `/api/consumidor/{idConta}/carrinho` | 🔒 | Ver, alterar e esvaziar |
| Pedidos | `POST /api/consumidor/{idConta}/pedidos` | 🔒 | Checkout (um pedido por feirante) |
| Xepas do feirante | `/api/feirante/{idConta}/xepas` | 🔒 | Publicar, editar e cancelar |
| Vendas | `POST /api/feirante/{idConta}/pedidos/{id}/retirada` | 🔒 | Confirma a retirada com o código |
| Financeiro | `/api/financeiro/{idConta}/rendas` · `/despesas` · `/saques` · `/resumo` | 🔒 | Lucros, gastos, saques e resumo |
| Conversas | `/api/{papel}/{idConta}/conversas` | 🔒 | Chat consumidor ↔ feirante |
| Xepinha | `POST /api/feirante/{idConta}/xepinha/mensagens` | 🔒 | Pergunta à IA (planos Broto e Colheita) |

## Banco de dados

- MySQL 8 (`db_xepa`). Os scripts, os dados de exemplo e o dicionário de dados estão no [repositório database](https://github.com/XepaMais/database).
- A API usa `ddl-auto: none`: quem cria as tabelas são os scripts. Ao iniciar, o `AtualizacaoBanco` cria as colunas e tabelas novas que ainda faltam.
- Perfil, carrinho, pedidos, xepas, financeiro e conversas ficam no banco, então aparecem em qualquer aparelho.

## LGPD

- O cadastro só é feito com o aceite dos Termos e da Política de Privacidade. A data e a versão ficam gravadas.
- Receber novidades por e-mail é opcional e pode ser desligado: `PUT /api/conta/{id}/consentimento`.
- A pessoa pode baixar todos os seus dados: `GET /api/conta/{id}/dados`.
- A exclusão (`DELETE /api/conta/{id}`) apaga os dados pessoais e desconecta todos os aparelhos. Os pedidos ficam guardados sem identificar a pessoa.


## Testes

```powershell
.\mvnw.cmd test
```

São 34 testes de integração, rodando com H2 em memória (não precisa de MySQL). Eles cobrem cadastro e login, carrinho e checkout, xepas e pedidos, financeiro e saques, feiras e planos, chat, fotos, avaliações, LGPD e a Xepinha (com a IA simulada).

## Publicação (AWS)

| Script | O que faz |
| --- | --- |
| [`deploy/setup-ec2.sh`](deploy/setup-ec2.sh) | Prepara a EC2: Java, MySQL e o serviço da API |
| [`deploy/setup-https.sh`](deploy/setup-https.sh) | Nginx + certificado HTTPS |
| [`deploy/deploy.ps1`](deploy/deploy.ps1) | Gera o `.jar`, envia para o servidor e reinicia a API |

```powershell
.\deploy\deploy.ps1 -Servidor <IP-da-EC2> -Chave <caminho-da-chave.pem>
```

Logs no servidor: `sudo journalctl -u xepamais-api -f`.
