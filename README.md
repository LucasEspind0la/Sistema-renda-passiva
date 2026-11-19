# Finanças & Renda Passiva

Gestão financeira pessoal com foco em renda passiva e independência financeira.

## Tecnologias

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security, Spring Data JPA, Bean Validation
- **Autenticação:** JWT (jjwt 0.12.6) e senhas com BCrypt
- **Banco:** PostgreSQL 16 com migrations Flyway
- **Frontend:** Angular (standalone + signals)
- **Documentação:** OpenAPI / Swagger UI
- **Testes:** JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers
- **Infra:** Docker Compose

## Como rodar

Pré-requisitos: Java 21, Maven, Node.js e Docker.

```bash
# 1) Configuração (o .env não vai para o Git)
cp -n .env.example .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env
nano .env                       # defina DB_PASSWORD

# 2) Banco (porta 5433, para não conflitar com um PostgreSQL local)
docker compose up -d

# 3) Backend: http://localhost:8080 (Swagger em /swagger-ui.html)
cd backend
set -a; source ../.env; set +a
mvn spring-boot:run

# 4) Frontend: http://localhost:4200
cd ../frontend && npm install && npm start
```

O backend não inicia sem `DB_USER`, `DB_PASSWORD` e `JWT_SECRET`.

## Testes

```bash
cd backend
mvn test      # unitários, sem Docker
mvn verify    # unitários + integração (precisa do Docker)
```

## Regras de cálculo

| Métrica | Fórmula |
|---|---|
| Saldo atual | Receitas do mês − Gastos do mês |
| Patrimônio líquido | Σ valor atual dos ativos − Σ valor remanescente das dívidas |
| Renda passiva mensal | Σ proventos mensais dos ativos |
| Cobertura de independência | Renda passiva ÷ Gastos fixos do mês × 100 (nula se não há gastos fixos) |

Receitas **únicas** contam só no mês da data, **mensais** a partir da data e **anuais** todo ano no mesmo mês. Gastos do mês são as despesas com vencimento dentro do mês consultado.

## Arquitetura

- Camadas: web → service → repository → domain, com DTOs `record`.
- CRUD genérico (`CrudService` / `CrudController`), cada entidade só define o mapeamento.
- Dados isolados por usuário: toda consulta filtra por `usuario_id`, vindo do JWT.
- `BigDecimal` / `NUMERIC(15,2)` nos valores, chaves `UUID` e schema versionado pelo Flyway.
- Erros padronizados em `ProblemDetail` (RFC 7807).

## Segurança

- Rotas protegidas por JWT; só `/api/auth/**` e a documentação são públicas.
- Login retorna a mesma mensagem para e-mail inexistente e senha errada.
- Nenhum segredo no código: tudo vem de variáveis de ambiente.