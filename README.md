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
- Entidades JPA que representam contas, consumidores, feirantes, produtos, feiras, xepas, pedidos e outras tabelas do domínio

> A existência de uma entidade JPA não significa que já exista um endpoint para ela. Por enquanto, os endpoints REST implementados são os de cadastro e login listados abaixo.

## Endpoints de autenticação

| Método | Caminho | Descrição |
| --- | --- | --- |
| `POST` | `/api/auth/cadastro-consumidor` | Cria uma conta de consumidor |
| `POST` | `/api/auth/cadastro-feirante` | Cria uma conta de feirante |
| `POST` | `/api/auth/login` | Confere email e senha e informa o tipo da conta |

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
- MySQL em execução e banco `db_xepa` já criado
- Tabelas do banco criadas previamente

O Hibernate está configurado com `ddl-auto: none`, então a aplicação não cria nem altera as tabelas automaticamente.

### Variáveis de ambiente

Configure estas variáveis na configuração de execução do IntelliJ ou no terminal:

| Variável | Exemplo local |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3307/db_xepa` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | senha configurada no seu MySQL |

Não coloque a senha real no README, no Git ou em arquivos versionados.

### Executar pelo IntelliJ

Abra a configuração `ApiApplication`, preencha as variáveis de ambiente e execute a aplicação. O servidor usa a porta `8080` por padrão.

### Executar pelo PowerShell

Na pasta deste projeto, configure as variáveis para a sessão atual e inicie o Spring Boot:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3307/db_xepa"
$env:DB_USER = "root"
$env:DB_PASSWORD = "SUA_SENHA"
.\mvnw.cmd spring-boot:run
```

## Estrutura do projeto

```text
src/main/java/br/com/xepamais/api/
├── auth/                 # cadastro, login, DTOs e hash de senha
├── config/               # configurações da aplicação, incluindo CORS
└── database/
    ├── model/            # entidades JPA mapeadas para as tabelas
    └── repository/       # repositórios Spring Data JPA
```

## Estado da integração

O front-end já envia cadastro e login de consumidor e feirante para a API. A persistência de produtos, feiras, xepas, carrinho, pedidos, conversas e dados financeiros ainda precisa ser implementada por endpoints e conectada às telas correspondentes.
