# 🐟 HookFishing

API REST desenvolvida em **Java + Spring Boot** para gerenciamento de peixes e recursos relacionados a um aquário.

O projeto foi desenvolvido com foco em demonstrar os principais recursos de uma API REST utilizando:

* Spring Boot
* Java 25
* Maven
* Spring Data JPA
* H2 Database
* Bean Validation
* Spring HATEOAS
* Springdoc OpenAPI / Swagger
* Paginação com `Pageable`
* Relacionamentos entre entidades
* Enum
* Operações CRUD

---

## 📋 Sumário

* [Sobre o projeto](#-sobre-o-projeto)
* [Tecnologias utilizadas](#-tecnologias-utilizadas)
* [Estrutura do projeto](#-estrutura-do-projeto)
* [Entidades](#-entidades)
* [Relacionamentos](#-relacionamentos)
* [Validações](#-validações)
* [Banco de dados](#-banco-de-dados)
* [Como executar](#-como-executar)
* [Swagger / OpenAPI](#-swagger--openapi)
* [Endpoints](#-endpoints)
* [Paginação](#-paginação)
* [HATEOAS](#-hateoas)
* [Testando a API](#-testando-a-api)
* [Exemplos de requisições](#-exemplos-de-requisições)
* [Códigos HTTP](#-códigos-http)
* [Requisitos do projeto](#-requisitos-atendidos)

---

# 🐠 Sobre o projeto

O **HookFishing** é uma API REST para gerenciamento de um ambiente de aquário.

A aplicação permite cadastrar e administrar:

* 🐟 Peixes
* 🧬 Espécies
* 🏠 Aquários
* 👨‍🔬 Cuidadores
* 🍽️ Alimentações

Cada entidade possui operações de criação, consulta, atualização e exclusão.

Além do CRUD, cada entidade possui uma consulta personalizada através de um endpoint `/buscar`.

---

# 🛠️ Tecnologias utilizadas

| Tecnologia        | Utilização                  |
| ----------------- | --------------------------- |
| Java 25           | Linguagem de programação    |
| Spring Boot       | Framework principal         |
| Spring Web        | Criação da API REST         |
| Spring Data JPA   | Persistência de dados       |
| Hibernate         | ORM                         |
| H2 Database       | Banco de dados em memória   |
| Bean Validation   | Validação dos dados         |
| Spring HATEOAS    | HATEOAS e links REST        |
| Springdoc OpenAPI | Documentação da API         |
| Swagger UI        | Interface para testar a API |
| Maven             | Gerenciamento do projeto    |

---

# 📁 Estrutura do projeto

```text
fishapi/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── fishapi/
│   │   │           │
│   │   │           ├── FishapiApplication.java
│   │   │           │
│   │   │           ├── controller/
│   │   │           │   ├── PeixeController.java
│   │   │           │   ├── EspecieController.java
│   │   │           │   ├── AquarioController.java
│   │   │           │   ├── CuidadorController.java
│   │   │           │   └── AlimentacaoController.java
│   │   │           │
│   │   │           ├── model/
│   │   │           │   ├── Peixe.java
│   │   │           │   ├── Especie.java
│   │   │           │   ├── Aquario.java
│   │   │           │   ├── Cuidador.java
│   │   │           │   ├── Alimentacao.java
│   │   │           │   └── TipoAlimentacao.java
│   │   │           │
│   │   │           └── repository/
│   │   │               ├── PeixeRepository.java
│   │   │               ├── EspecieRepository.java
│   │   │               ├── AquarioRepository.java
│   │   │               ├── CuidadorRepository.java
│   │   │               └── AlimentacaoRepository.java
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│       └── java/
│
├── pom.xml
└── README.md
```

---

# 🧩 Entidades

O projeto possui cinco entidades principais.

## 🐟 Peixe

Representa um peixe cadastrado no sistema.

Principais atributos:

```text
id
nome
idade
tamanho
especie
aquario
alimentacoes
```

---

## 🧬 Especie

Representa a espécie à qual um peixe pertence.

Atributos:

```text
id
nome
familia
peixes
```

---

## 🏠 Aquario

Representa um aquário existente no sistema.

Atributos:

```text
id
nome
capacidadeLitros
cuidador
peixes
```

---

## 👨‍🔬 Cuidador

Representa o responsável por um aquário.

Atributos:

```text
id
nome
email
```

---

## 🍽️ Alimentacao

Representa uma alimentação disponível para os peixes.

Atributos:

```text
id
nome
tipo
peixes
```

---

# 🔗 Relacionamentos

O projeto utiliza três tipos diferentes de relacionamentos JPA.

## One-to-Many

Uma espécie pode possuir vários peixes:

```text
Especie 1 ───────── N Peixe
```

Um aquário também pode possuir vários peixes:

```text
Aquario 1 ───────── N Peixe
```

---

## Many-to-Many

Um peixe pode receber diferentes tipos de alimentação.

Uma alimentação também pode ser utilizada por vários peixes.

```text
Peixe N ───────── N Alimentacao
```

Esse relacionamento é armazenado através da tabela:

```text
peixe_alimentacao
```

---

## One-to-One

Cada aquário possui um cuidador:

```text
Aquario 1 ───────── 1 Cuidador
```

---

# 🔤 Enum

O projeto possui o enum `TipoAlimentacao`.

Valores disponíveis:

```java
RACAO
VIVA
VEGETAL
CONGELADA
```

Exemplo:

```json
{
  "nome": "Ração Tropical",
  "tipo": "RACAO"
}
```

---

# ✅ Validações

As entidades utilizam **Bean Validation** através das anotações do pacote `jakarta.validation`.

Exemplos utilizados:

```java
@NotBlank
@Size
@NotNull
@Min
@Positive
@Email
```

### Peixe

* Nome obrigatório
* Nome entre 2 e 100 caracteres
* Idade obrigatória
* Idade não pode ser negativa
* Tamanho obrigatório
* Tamanho deve ser maior que zero

### Espécie

* Nome obrigatório
* Nome entre 3 e 100 caracteres
* Família obrigatória
* Família entre 3 e 100 caracteres

### Aquário

* Nome obrigatório
* Nome entre 3 e 100 caracteres
* Capacidade obrigatória
* Capacidade mínima de 1 litro

### Cuidador

* Nome obrigatório
* Nome entre 3 e 100 caracteres
* E-mail obrigatório
* E-mail deve possuir formato válido

### Alimentação

* Nome obrigatório
* Nome entre 3 e 100 caracteres
* Tipo obrigatório

---

# 🗄️ Banco de dados

O projeto utiliza o **H2 Database** em memória.

Configuração:

```properties
spring.datasource.url=jdbc:h2:mem:fishdb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=create-drop
```

Como o banco é em memória, os dados são apagados quando a aplicação é encerrada.

Isso é adequado para desenvolvimento e testes acadêmicos.

---

# ▶️ Como executar

## 1. Pré-requisitos

É necessário possuir instalado:

* Java 25
* Maven
* IDE de sua preferência

Verifique o Java:

```bash
java -version
```

Verifique o Maven:

```bash
mvn -version
```

---

## 2. Clonar o projeto

```bash
git clone <https://github.com/LGdds/HookFishing.git>
```

Entrar na pasta:

```bash
cd fishapi
```

---

## 3. Compilar

```bash
mvn clean install
```

---

## 4. Executar

```bash
mvn spring-boot:run
```

A aplicação será iniciada na porta:

```text
8080
```

---

# 📚 Swagger / OpenAPI

A API possui documentação automática através do Springdoc OpenAPI.

Com a aplicação executando, acesse:

**Swagger UI:**

http://localhost:8080/swagger-ui.html

Também é possível acessar:

http://localhost:8080/swagger-ui/index.html

A especificação OpenAPI está disponível em:

http://localhost:8080/v3/api-docs

O Swagger permite:

* visualizar todos os endpoints;
* visualizar parâmetros;
* visualizar códigos HTTP;
* visualizar os modelos;
* executar requisições;
* testar POST, GET, PUT e DELETE diretamente pelo navegador.

---

# 🗃️ H2 Console

O console do banco H2 está habilitado.

Acesse:

http://localhost:8080/h2-console

Utilize:

```text
JDBC URL: jdbc:h2:mem:fishdb
User Name: sa
Password:
```

A senha deve ficar em branco.

---

# 🌐 Endpoints

Todas as entidades possuem seis endpoints.

---

## 🐟 Peixes

### Listar peixes

```http
GET /peixes
```

Lista os peixes de forma paginada.

---

### Buscar peixe por ID

```http
GET /peixes/{id}
```

Exemplo:

```http
GET /peixes/1
```

---

### Cadastrar peixe

```http
POST /peixes
```

Exemplo:

```json
{
  "nome": "Nemo",
  "idade": 2,
  "tamanho": 8.5
}
```

---

### Atualizar peixe

```http
PUT /peixes/{id}
```

Exemplo:

```http
PUT /peixes/1
```

---

### Excluir peixe

```http
DELETE /peixes/{id}
```

Exemplo:

```http
DELETE /peixes/1
```

---

### Buscar peixe por nome

```http
GET /peixes/buscar?nome=Nemo
```

---

# 🧬 Espécies

### Listar espécies

```http
GET /especies
```

### Buscar por ID

```http
GET /especies/{id}
```

### Cadastrar

```http
POST /especies
```

Exemplo:

```json
{
  "nome": "Peixe-palhaço",
  "familia": "Pomacentridae"
}
```

### Atualizar

```http
PUT /especies/{id}
```

### Excluir

```http
DELETE /especies/{id}
```

### Buscar por nome

```http
GET /especies/buscar?nome=Peixe-palhaço
```

---

# 🏠 Aquários

### Listar aquários

```http
GET /aquarios
```

### Buscar por ID

```http
GET /aquarios/{id}
```

### Cadastrar

```http
POST /aquarios
```

Exemplo:

```json
{
  "nome": "Aquário Principal",
  "capacidadeLitros": 200
}
```

### Atualizar

```http
PUT /aquarios/{id}
```

### Excluir

```http
DELETE /aquarios/{id}
```

### Buscar por nome

```http
GET /aquarios/buscar?nome=Aquário Principal
```

---

# 👨‍🔬 Cuidadores

### Listar cuidadores

```http
GET /cuidadores
```

### Buscar por ID

```http
GET /cuidadores/{id}
```

### Cadastrar

```http
POST /cuidadores
```

Exemplo:

```json
{
  "nome": "João Silva",
  "email": "joao@email.com"
}
```

### Atualizar

```http
PUT /cuidadores/{id}
```

### Excluir

```http
DELETE /cuidadores/{id}
```

### Buscar por e-mail

```http
GET /cuidadores/buscar?email=joao@email.com
```

---

# 🍽️ Alimentações

### Listar alimentações

```http
GET /alimentacoes
```

### Buscar por ID

```http
GET /alimentacoes/{id}
```

### Cadastrar

```http
POST /alimentacoes
```

Exemplo:

```json
{
  "nome": "Ração Tropical",
  "tipo": "RACAO"
}
```

### Atualizar

```http
PUT /alimentacoes/{id}
```

### Excluir

```http
DELETE /alimentacoes/{id}
```

### Buscar por nome

```http
GET /alimentacoes/buscar?nome=Ração Tropical
```

---

# 📄 Paginação

Todos os endpoints de listagem utilizam `Pageable`.

Os parâmetros disponíveis são:

```text
page
size
sort
```

Exemplo:

```http
GET /peixes?page=0&size=10
```

Retorna a primeira página contendo até 10 registros.

Também é possível ordenar:

```http
GET /peixes?page=0&size=10&sort=nome,asc
```

Ou:

```http
GET /peixes?page=0&size=10&sort=idade,desc
```

Os parâmetros de paginação são opcionais.

Portanto, também é possível simplesmente utilizar:

```http
GET /peixes
```

---

# 🔗 HATEOAS

A API utiliza **Spring HATEOAS** para adicionar links relacionados aos recursos.

Exemplo de resposta:

```json
{
  "id": 1,
  "nome": "Nemo",
  "idade": 2,
  "tamanho": 8.5,
  "_links": {
    "self": {
      "href": "http://localhost:8080/peixes/1"
    },
    "update": {
      "href": "http://localhost:8080/peixes/1"
    },
    "delete": {
      "href": "http://localhost:8080/peixes/1"
    }
  }
}
```

O link `self` representa o próprio recurso.

O link `update` representa o endereço utilizado para atualização.

O link `delete` representa o endereço utilizado para exclusão.

---

# 🧪 Testando a API

Os endpoints podem ser testados através de:

* Swagger UI
* Postman
* Insomnia
* `curl`
* navegador, para requisições GET

A forma recomendada para apresentação do projeto é utilizar o **Swagger UI**, pois ele permite demonstrar os endpoints diretamente.

---

## Teste de criação

Primeiro, crie uma espécie:

```http
POST /especies
```

```json
{
  "nome": "Peixe-palhaço",
  "familia": "Pomacentridae"
}
```

Depois crie um aquário:

```http
POST /aquarios
```

```json
{
  "nome": "Aquário Principal",
  "capacidadeLitros": 200
}
```

Depois crie uma alimentação:

```http
POST /alimentacoes
```

```json
{
  "nome": "Ração Tropical",
  "tipo": "RACAO"
}
```

Depois pode cadastrar um peixe:

```http
POST /peixes
```

```json
{
  "nome": "Nemo",
  "idade": 2,
  "tamanho": 8.5
}
```

---

# ❌ Testando validações

Também é possível testar o Bean Validation.

Por exemplo, enviar:

```json
{
  "nome": "",
  "idade": -5,
  "tamanho": -10
}
```

A API deverá rejeitar os dados devido às regras de validação.

Outro exemplo para cuidador:

```json
{
  "nome": "João",
  "email": "email-invalido"
}
```

O campo `email` será rejeitado pela validação `@Email`.

---

# 📊 Códigos HTTP utilizados

A API utiliza códigos HTTP apropriados para cada operação.

| Código            | Significado                      | Situação                          |
| ----------------- | -------------------------------- | --------------------------------- |
| `200 OK`          | Requisição realizada com sucesso | GET e PUT                         |
| `201 CREATED`     | Recurso criado                   | POST                              |
| `204 NO CONTENT`  | Recurso excluído                 | DELETE                            |
| `400 BAD REQUEST` | Dados inválidos                  | Validação ou parâmetros inválidos |
| `404 NOT FOUND`   | Recurso não encontrado           | ID ou busca inexistente           |

---

# 🔍 Consultas personalizadas

Cada entidade possui pelo menos uma consulta personalizada implementada no Repository.

### Peixe

```java
findByNomeIgnoreCase(String nome)
```

Endpoint:

```http
GET /peixes/buscar?nome=Nemo
```

### Espécie

```java
findByNomeIgnoreCase(String nome)
```

Endpoint:

```http
GET /especies/buscar?nome=Peixe-palhaço
```

### Aquário

```java
findByNomeIgnoreCase(String nome)
```

Endpoint:

```http
GET /aquarios/buscar?nome=Aquário Principal
```

### Cuidador

```java
findByEmailIgnoreCase(String email)
```

Endpoint:

```http
GET /cuidadores/buscar?email=joao@email.com
```

### Alimentação

```java
findByNomeIgnoreCase(String nome)
```

Endpoint:

```http
GET /alimentacoes/buscar?nome=Ração Tropical
```

---

# 📌 Resumo dos endpoints

| Entidade    | GET lista       | GET por ID           | POST            | PUT                  | DELETE               | Busca                  |
| ----------- | --------------- | -------------------- | --------------- | -------------------- | -------------------- | ---------------------- |
| Peixe       | `/peixes`       | `/peixes/{id}`       | `/peixes`       | `/peixes/{id}`       | `/peixes/{id}`       | `/peixes/buscar`       |
| Espécie     | `/especies`     | `/especies/{id}`     | `/especies`     | `/especies/{id}`     | `/especies/{id}`     | `/especies/buscar`     |
| Aquário     | `/aquarios`     | `/aquarios/{id}`     | `/aquarios`     | `/aquarios/{id}`     | `/aquarios/{id}`     | `/aquarios/buscar`     |
| Cuidador    | `/cuidadores`   | `/cuidadores/{id}`   | `/cuidadores`   | `/cuidadores/{id}`   | `/cuidadores/{id}`   | `/cuidadores/buscar`   |
| Alimentação | `/alimentacoes` | `/alimentacoes/{id}` | `/alimentacoes` | `/alimentacoes/{id}` | `/alimentacoes/{id}` | `/alimentacoes/buscar` |

Ao todo, são **30 endpoints**:

```text
5 entidades × 6 endpoints = 30 endpoints
```

---

# 🎓 Requisitos atendidos

O projeto foi estruturado para atender aos requisitos da atividade.

### Spring Boot

A aplicação utiliza Spring Boot para construção da API REST.

### Java 17+

O projeto utiliza **Java 25**.

### Maven

O gerenciamento das dependências é feito pelo Maven através do `pom.xml`.

### H2

Banco de dados H2 em memória.

### Spring Data JPA

Os repositories utilizam `JpaRepository`.

### Cinco entidades

```text
Peixe
Especie
Aquario
Cuidador
Alimentacao
```

### Relacionamentos

**One-to-One:**

```text
Aquario → Cuidador
```

**One-to-Many:**

```text
Especie → Peixe
Aquario → Peixe
```

**Many-to-Many:**

```text
Peixe ↔ Alimentacao
```

### Bean Validation

Utilização de:

```text
@NotBlank
@NotNull
@Size
@Min
@Positive
@Email
```

### Enum

```text
TipoAlimentacao
```

### CRUD

Todas as cinco entidades possuem:

```text
CREATE
READ
UPDATE
DELETE
```

### Paginação

Os endpoints de listagem utilizam:

```java
Pageable
```

### Consultas personalizadas

Cada entidade possui pelo menos uma consulta personalizada no Repository.

### HTTP Status

São utilizados códigos apropriados como:

```text
200
201
204
400
404
```

### Swagger / OpenAPI

A API possui documentação através do Springdoc OpenAPI e Swagger UI.

### HATEOAS

Os recursos retornam links através de:

```text
EntityModel
PagedModel
```

Incluindo links como:

```text
self
update
delete
```

---

# 👨‍💻 Autor

Projeto acadêmico desenvolvido para demonstrar a construção de uma API REST utilizando Java e Spring Boot.

---

# 📄 Licença

Projeto desenvolvido para fins acadêmicos e de estudo.
