# Imprime AI - API

Esta é a API backend do projeto **Imprime AI**, desenvolvida em Java utilizando o framework **Spring Boot**.

## 🛠️ Tecnologias Utilizadas

- **Java 25**
- **Spring Boot** (WebMVC, Data JPA, Security)
- **Banco de Dados Oracle** (via Docker)
- **Flyway** (Migrations de Banco de Dados)
- **Autenticação:** JWT (JSON Web Tokens) e BCrypt
- **Gerenciamento de Dependências:** Maven Wrapper

## 🚀 Como Rodar o Projeto Localmente

### Pré-requisitos
- Ter o **Docker** e **Docker Compose** instalados na sua máquina.
- Ter o **JDK 25** instalado para poder compilar e rodar a aplicação.

### Passo 1: Subir o Banco de Dados (Oracle)
O projeto depende de um banco de dados Oracle livre (Free) que já está pré-configurado no arquivo `docker-compose.yaml`.

Abra o terminal na pasta raiz do projeto (`imprime-ai-api`) e execute:

```bash
docker compose up -d
```

> **Nota:** A flag `-d` roda o contêiner em segundo plano. A primeira vez pode demorar alguns minutos para fazer o download da imagem do Oracle e inicializar o banco completamente.

### Passo 2: Executar a Aplicação Spring Boot

Após o banco de dados estar rodando e pronto para receber conexões, inicie a aplicação Spring Boot utilizando o Maven Wrapper (que já vem embutido no projeto).

**No Linux / WSL / macOS:**
```bash
./mvnw spring-boot:run
```

**No Windows (PowerShell / CMD):**
```powershell
.\mvnw.cmd spring-boot:run
```

### O que acontece em seguida?
1. O **Flyway** será acionado e criará ou atualizará todas as tabelas e esquemas necessários no banco de dados Oracle automaticamente de acordo com as *migrations* do projeto.
2. O servidor web vai iniciar e a API estará pronta para receber requisições (normalmente na porta `8081`).

## 🛑 Parando os Serviços

Para parar a execução da API, basta pressionar `Ctrl + C` no terminal onde o Spring Boot está rodando.

Para desligar e remover o contêiner do banco de dados Oracle, execute o seguinte comando na mesma pasta:
```bash
docker compose down
```
