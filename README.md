<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-texto-branca.png">
    <img src="assets/logo-texto-colorida.png" alt="Logo do Xepa+" width="280">
  </picture>
</p>

<div align="center">

# Xepa+ API

**API do Xepa+: integração entre a aplicação web e o banco de dados.**

![Status](https://img.shields.io/badge/status-em_desenvolvimento-FF5200?style=for-the-badge)

</div>

## Sobre este repositório

Este repositório contém o **back-end** do Xepa+. A API foi desenvolvida em **Java** com **Spring Boot** e usa **MySQL** para armazenar os dados. O front-end se comunica com a API por requisições HTTP; ele não acessa o banco diretamente.

### Tecnologias

**Back-end:**<br><br>
<img align="center" src="https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17" title="Java 17">
<img align="center" src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot" title="Spring Boot">
<img align="center" src="https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Data JPA" title="Spring Data JPA">
<img align="center" src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL" title="MySQL">
<img align="center" src="https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven" title="Maven">

| Tecnologia | Para que é usada no Xepa+ |
| --- | --- |
| Java 17 | Linguagem da aplicação do servidor |
| Spring Boot | Inicialização e configuração da API |
| Spring Web MVC | Criação dos endpoints HTTP/REST |
| Spring Data JPA | Persistência e consultas às entidades do banco |
| Hibernate | Implementação JPA usada pelo Spring Data JPA |
| MySQL Connector/J | Comunicação JDBC com o MySQL |
| Lombok | Geração de getters e setters nas entidades |
| Maven Wrapper | Execução e gerenciamento do projeto Maven |
| PBKDF2 com HMAC-SHA-256 | Hash de senhas antes de armazená-las |

**Ferramentas utilizadas:**<br><br>
<img align="center" src="https://img.shields.io/badge/IntelliJ_IDEA-000000?style=for-the-badge&logo=intellijidea&logoColor=white" alt="IntelliJ IDEA" title="IntelliJ IDEA">
<img align="center" src="https://img.shields.io/badge/Git-E44C30?style=for-the-badge&logo=git&logoColor=white" alt="Git" title="Git">
<img align="center" src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub" title="GitHub">

---

## Funcionalidades disponíveis

- Cadastro de consumidor, com gravação em `tb_conta` e `tb_consumidor`
- Cadastro de feirante, com gravação em `tb_conta` e `tb_feirante`
- Login por email e senha
- Hash de senha antes de persistir no banco
- CORS configurado para permitir chamadas do front-end local em `http://localhost:5173`
- Financeiro do feirante: lucros (rendas), despesas, saques via Pix (simulados), resumo e planilha CSV, gravados em `tb_renda`, `tb_despesa` e `tb_saque`
- Entidades JPA que representam contas, consumidores, feirantes, produtos, feiras, xepas, pedidos e outras tabelas do domínio

> A existência de uma entidade JPA não significa que já exista um endpoint para ela. Por enquanto, os endpoints REST implementados são os de cadastro, login e financeiro listados abaixo.

## Endpoints de autenticação

| Método | Caminho | Descrição |
| --- | --- | --- |
| `POST` | `/api/auth/cadastro-consumidor` | Cria uma conta de consumidor |
| `POST` | `/api/auth/cadastro-feirante` | Cria uma conta de feirante |
| `POST` | `/api/auth/login` | Confere email e senha e informa o tipo da conta |

## Endpoints do financeiro

`{idConta}` é o `id` devolvido pelo login de um feirante. Datas no formato `AAAA-MM-DD`.

| Método | Caminho | Descrição |
| --- | --- | --- |
| `GET` | `/api/financeiro/categorias` | Origens de renda e categorias de despesa aceitas |
| `GET` | `/api/financeiro/{idConta}/rendas?inicio=&fim=` | Lista os lucros (filtro de datas opcional) |
| `POST` | `/api/financeiro/{idConta}/rendas` | Cria um lucro: `{ "origem", "descricao", "valor", "data" }` |
| `PUT` | `/api/financeiro/{idConta}/rendas/{id}` | Edita um lucro |
| `DELETE` | `/api/financeiro/{idConta}/rendas/{id}` | Exclui um lucro |
| `GET` | `/api/financeiro/{idConta}/despesas?inicio=&fim=` | Lista as despesas |
| `POST` | `/api/financeiro/{idConta}/despesas` | Cria uma despesa: `{ "categoria", "descricao", "quantidade", "valorUnitario", "data" }` |
| `PUT` | `/api/financeiro/{idConta}/despesas/{id}` | Edita uma despesa |
| `DELETE` | `/api/financeiro/{idConta}/despesas/{id}` | Exclui uma despesa |
| `GET` | `/api/financeiro/{idConta}/saldo` | Saldo para saque: vendas "Venda de xepa pelo Xepa+" menos saques não recusados |
| `GET` | `/api/financeiro/{idConta}/saques` | Histórico de saques |
| `POST` | `/api/financeiro/{idConta}/saques` | Solicita saque: `{ "valor", "tipoChave", "chave" }` (`cpf`, `cnpj`, `email`, `celular`, `aleatoria`) |
| `GET` | `/api/financeiro/{idConta}/resumo` | Rendas, despesas e lucro de hoje, da semana (seg. a dom.) e do mês |
| `GET` | `/api/financeiro/{idConta}/historico?inicio=&fim=&tipo=&categoria=&busca=` | Lucros e despesas juntos, com totais (`tipo`: `todos`, `lucro` ou `gasto`) |
| `GET` | `/api/financeiro/{idConta}/planilha?...` | Mesmo filtro do histórico, em CSV para Excel (`;`, vírgula decimal) |

O saque confere o saldo no servidor com a linha do feirante travada, então dois saques ao mesmo tempo não passam do disponível. Nenhum dinheiro é transferido: o saque fica com status `SOLICITADO`. Rendas e despesas ligadas a um pedido (`id_pedido`) não podem ser editadas nem excluídas.

> Ainda não há token de sessão: qualquer pessoa que saiba o `idConta` consegue chamar esses endpoints. Antes de usar com dinheiro real, é preciso autenticação (ex.: JWT) e conferir que a conta do token é a do caminho.

O cadastro recebe JSON. Exemplo de consumidor:

```json
{
  "nome": "Nome completo",
  "cpf": "12345678901",
  "email": "pessoa@exemplo.com",
  "senha": "uma-senha-com-8-caracteres",
  "telefone": "11999999999"
}
```

O cadastro de feirante também recebe `nomeFantasia`, `cnpj`, `ccm`, `matricula` e `grupoComercio`. O login recebe `email` e `senha`. As respostas retornam `id`, `nome`, `email` e `tipo`, sem incluir a senha.

## Configuração local

### Pré-requisitos

- Java 17
- MySQL 8 em execução (porta padrão `3306`)
- Banco `db_xepa` criado com o script do repositório `database` (`xepaDB(1.0) (1).sql`). Na pasta `Xepa`, o `configurar-banco-local.ps1` faz isso e gera o `local.env` com as credenciais

O Hibernate está configurado com `ddl-auto: none`. As tabelas e colunas novas que faltarem no banco são criadas ao iniciar pela classe `config/AtualizacaoBanco.java`, que confere antes o que já existe.

### Variáveis de ambiente

Configure estas variáveis na configuração de execução do IntelliJ ou no terminal:

| Variável | Exemplo local |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/db_xepa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | senha configurada no seu MySQL |

Não coloque a senha real no README, no Git ou em arquivos versionados.

### Executar pelo IntelliJ

Abra a configuração `ApiApplication`, preencha as variáveis de ambiente e execute a aplicação. O servidor usa a porta `8080` por padrão.

### Executar pelo PowerShell

Na pasta deste projeto, configure as variáveis para a sessão atual e inicie o Spring Boot:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/db_xepa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo"
$env:DB_USER = "root"
$env:DB_PASSWORD = "SUA_SENHA"
.\mvnw.cmd spring-boot:run
```

## Estrutura do projeto

```text
src/main/java/br/com/xepamais/api/
├── auth/                 # cadastro, login, DTOs e hash de senha
├── config/               # configurações da aplicação, incluindo CORS
├── financeiro/           # lucros, despesas, saques, resumo e planilha do feirante
└── database/
    ├── model/            # entidades JPA mapeadas para as tabelas
    └── repository/       # repositórios Spring Data JPA
```

## Estado da integração

O front-end já envia cadastro e login de consumidor e feirante para a API. O financeiro do feirante (lucros, despesas, saques e planilhas) já usa a API quando há um feirante logado; sem login, as telas continuam no modo demonstração. A persistência de produtos, feiras, xepas, carrinho, pedidos e conversas ainda precisa ser implementada por endpoints e conectada às telas correspondentes.
