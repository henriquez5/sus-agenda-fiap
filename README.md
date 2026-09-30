# SUS Agenda

MVP acadêmico para o Hackathon FIAP — Arquitetura e Desenvolvimento em Java, Fase 5.
Agendamento assistido por atendentes e oferta automática de vagas para uma lista de espera.
**Fluxo demonstrável:** Ana cancela → Bruno recebe oferta → Bruno confirma → a vaga fica ocupada novamente.

## Comece aqui no Windows

1. Extraia o ZIP por completo.
2. Tenha **Java 17 ou superior** instalado (`java -version`). O código é compilado para Java 17.
3. Execute `executar-demo.bat`.
4. Abra **http://localhost:8080/swagger-ui.html**.
5. Clique em **Authorize**: usuário `operador`, senha `operador-demo`.
6. Importe `postman/SUS-Agenda.postman_collection.json` no Postman e execute as requisições em ordem.

O ZIP de entrega inclui o JAR em `bin/`. O modo `demo` usa H2 em memória: **os dados são apagados ao encerrar a aplicação**. As ofertas duram 45 segundos. Para ter mais tempo no Postman, antes de abrir o script, use no terminal:

```bat
set OFFER_TTL=PT15M
executar-demo.bat
```

Alternativa sem Postman, com Python 3:

```powershell
python scripts/demo.py
```

## Docker com PostgreSQL e persistência

Instale e inicie o Docker Desktop. Na pasta do projeto:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose logs -f api
```

No Linux/macOS, use `cp .env.example .env`. Aguarde a mensagem `Started SusAgendaApplication`.
Swagger: http://localhost:8080/swagger-ui.html. Saúde: http://localhost:8080/actuator/health.
A API fica acessível apenas pelo localhost. O banco usa volume persistente e não expõe porta ao host.

```powershell
docker compose down
```

Esse comando mantém os dados. `docker compose down -v` **apaga o banco**; use só para reiniciar uma demonstração conscientemente.
As senhas de `.env.example` são exclusivas para o ambiente local fictício. O `.env` não deve entrar no Git.

## Desenvolver e testar

Pré-requisitos: JDK 17+ e Maven 3.6.3+.

```sh
mvn verify
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

O build gera `target/sus-agenda-1.0.0.jar`. O `bin/sus-agenda.jar` do ZIP representa o código no momento da entrega; depois de alterar o projeto, use o JAR novo de `target/`.

```sh
java -jar target/sus-agenda-1.0.0.jar --spring.profiles.active=demo
```

## Perfis e configuração

| Item | Padrão / comportamento |
|---|---|
| Sem perfil | PostgreSQL; exige `DB_PASSWORD`, `OPERATOR_PASSWORD`, `READER_PASSWORD` |
| `demo` | H2 em memória e credenciais fictícias locais |
| `DB_URL` | `jdbc:postgresql://localhost:5432/susagenda` |
| `DB_USER` | `susagenda` |
| `OFFER_TTL` | `PT15M` sem perfil; `PT45S` no demo e no exemplo Docker |
| Job | Verifica ofertas vencidas a cada 5 segundos |
| Datas | Envie ISO-8601 com fuso, por exemplo `2030-01-01T15:00:00-03:00`; são normalizadas para UTC |

## Funcionalidades implementadas

- Cadastro mínimo de pacientes fictícios, unidades/especialidades e horários.
- Agendamento e cancelamento por ID de agendamento.
- Fila FIFO por agenda, com prevenção de duplicidade ativa.
- Oferta exclusiva temporária, confirmação, recusa e expiração automática.
- Novas vagas também atendem à fila existente.
- Confirmação e cancelamento com repetição segura.
- Bloqueio transacional por agenda para controlar concorrência no banco.
- Autenticação HTTP Basic e papéis `OPERATOR` / `READER`.
- Histórico transacional de eventos, indicadores, logs de requisição e métricas Prometheus.
- Migrações Flyway, Swagger e testes de integração.

## API

Todas as rotas abaixo têm prefixo `/api` e exigem autenticação. Operador escreve e consulta. Leitor só consulta, com usuário `leitor` e senha `leitor-demo` no perfil demo.

| Método | Rota | Função |
|---|---|---|
| POST / GET | `/patients` | Cadastrar/listar pacientes |
| POST / GET | `/agendas` | Criar/listar agendas |
| POST / GET | `/agendas/{id}/slots` | Criar/listar horários |
| POST | `/slots/{id}/book` | Agendar com `patientId` |
| POST | `/appointments/{id}/cancel` | Cancelar agendamento específico |
| GET | `/patients/{id}/appointments` | Consultar agendamentos do paciente |
| POST / GET | `/agendas/{id}/waitlist` | Entrar/consultar fila |
| POST | `/waitlist/{id}/withdraw` | Sair de uma entrada WAITING |
| GET | `/agendas/{id}/offers` | Consultar ofertas e destinatários |
| POST | `/offers/{id}/accept` | Confirmar oferta |
| POST | `/offers/{id}/decline` | Recusar oferta |
| GET | `/dashboard` | Indicadores agregados |
| GET | `/events` | Últimos 200 eventos, mais recentes primeiro |

Erros de negócio usam Problem Details: `400` dados inválidos; `401` sem credenciais; `403` papel sem permissão; `404` registro não encontrado; `409` conflito de estado ou duplicidade.
Consultas de listas retornam até 200 registros; paginação completa é evolução futura.

## Organização

```text
src/main/java/br/com/susagenda/
  api/          contratos HTTP e validação
  catalog/      pacientes e agendas
  scheduling/   agendamento, fila, ofertas e job
  reporting/    consultas e indicadores
  audit/        histórico transacional de eventos
  config/       segurança, Swagger e logs
  shared/       acesso JDBC e erros de negócio
src/main/resources/db/migration/  esquema versionado
src/test/                        testes de integração
postman/                         demonstração guiada
scripts/                         demonstração automatizada
 docs/                           arquitetura, relatório e roteiros
```

## Limites do MVP

O fluxo é operado por atendentes autorizados; não há contas individuais de pacientes, SMS, WhatsApp ou e-mail. A oferta é consultável via API, e o atendente registra a resposta do paciente. A fila é administrativa, FIFO e restrita à mesma unidade/especialidade, para pacientes já elegíveis a atendimento eletivo. Não realiza triagem nem decide prioridade clínica.

Uma agenda representa uma fila de atendimento com uma vaga por horário. Não há gestão de múltiplos profissionais no mesmo horário, duração de consulta, check-in ou conclusão do atendimento. Expirar ou recusar encerra aquela entrada; é possível entrar novamente ao fim da fila. Um paciente não pode ter dois agendamentos futuros na mesma agenda. Não há prevenção de sobreposição entre agendas distintas.

O histórico de eventos é auditoria; o estado atual está nas tabelas relacionais. Não há Event Sourcing, broker, microsserviços distribuídos ou alta disponibilidade implantada. Veja as decisões e evolução em `docs/ARQUITETURA.md`.
