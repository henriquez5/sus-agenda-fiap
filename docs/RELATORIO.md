# SUS Agenda — Relatório do projeto

**Curso:** Pós-graduação em Arquitetura e Desenvolvimento em Java — FIAP  
**Atividade:** Hackathon da Fase 5  
**Autor:** Henrique Jorge Alves Craveiro  
**LINK REPO:** https://github.com/henriquez5/sus-agenda-fiap

## 1. Resumo executivo

O SUS Agenda é um MVP de backend para apoiar atendentes no agendamento eletivo e na oferta de vagas a pacientes de uma lista de espera. Ao cancelar um agendamento, o sistema reserva a vaga temporariamente para o próximo paciente da mesma agenda. A confirmação cria um novo agendamento; recusa ou expiração permite atender o próximo candidato.

O impacto esperado é aumentar o aproveitamento dos horários disponibilizados e reduzir o trabalho manual de acompanhar a fila. São hipóteses de benefício, ainda sem validação com unidade de saúde ou mensuração em campo.

## 2. Problema identificado

O enunciado do hackathon destaca agendamento e redução do tempo de espera como oportunidades de melhoria no atendimento do SUS. A solução escolhe um recorte: a coordenação entre cancelamentos e pacientes que aguardam uma vaga.

Quando existe uma vaga liberada e uma lista de interessados, é necessário identificar quem deve recebê-la, reservar o horário durante a resposta e evitar que mais de uma pessoa o ocupe. O MVP modela esse processo com regras explícitas e histórico consultável.

O projeto não apresenta estatísticas de absenteísmo ou economia, nem afirma representar o funcionamento de todas as unidades do SUS. A magnitude do problema deve ser validada com dados locais antes de uma implantação real.

## 3. Descrição da solução

O atendente cadastra uma agenda por unidade e especialidade, seus horários e pacientes fictícios. Pode efetuar agendamento direto quando a vaga está livre e não há fila com preferência. Pacientes sem vaga entram em uma fila FIFO.

Ao cancelar uma consulta, uma oferta com prazo é criada automaticamente. O atendente consulta a oferta na API e registra a resposta do paciente. Confirmar ocupa a vaga; recusar ou deixar expirar encerra aquela entrada e aciona o próximo paciente. Novas vagas também podem ser destinadas à fila existente.

O diferencial proposto para o protótipo é explicitar todo o ciclo da oferta, com exclusividade temporária, expiração persistida, seleção determinística e rastreabilidade. Não se afirma ineditismo de mercado nem superioridade sobre sistemas existentes sem comparação empírica.

A fila modelada é administrativa para atendimentos eletivos já elegíveis; não faz triagem ou classificação clínica.

## 4. Processo de desenvolvimento

O trabalho partiu da leitura do enunciado e do material de apoio fornecido. Foram definidos o fluxo demonstrável, estados e regras de negócio; em seguida, a estrutura modular, persistência, endpoints e testes de integração.

Etapas realizadas no desenvolvimento assistido:

1. Delimitação de escopo e entregas obrigatórias.
2. Modelagem de comandos, eventos e estados de vaga, fila, oferta e agendamento.
3. Implementação de API, transações, segurança e expiração.
4. Preparação de testes para fluxo principal, erros e concorrência.
5. Preparação da execução local, coleção Postman e roteiros.

Não foram realizadas entrevistas, pesquisa de campo, oficina com equipe clínica ou validação com pacientes. O código e a documentação foram preparados com assistência de IA e devem ser revisados, executados e compreendidos pelo autor antes da apresentação. O registro dos testes efetivamente executados está em `VALIDACAO.md`.

## 5. Detalhes técnicos

Java 17, Spring Boot, Spring Web, Spring Security, Spring JDBC, Bean Validation, PostgreSQL, Flyway, Actuator, Micrometer, OpenAPI/Swagger, Maven e Docker Compose. JUnit, Spring Boot Test e MockMvc compõem a suíte de testes; H2 atende ao perfil de demonstração e testes rápidos.

A aplicação separa catálogo, comandos de agendamento, consultas, auditoria e infraestrutura. A separação entre comandos e consultas é lógica, no mesmo banco. A escolha mantém uma unidade de implantação e facilita consistência transacional no escopo do MVP.

O bloqueio de linha da agenda serializa decisões concorrentes. A expiração usa prazo persistido e rotina periódica. Estado e eventos são escritos na mesma transação. O histórico não é Event Sourcing. Não há mensageria externa ou implantação distribuída neste MVP.

O arquivo `ARQUITETURA.md` contém o diagrama, estados, decisões, limitações e proposta de evolução.

Segurança do protótipo: dois papéis, credenciais por configuração e API restrita ao localhost no Compose. As operações representam atendentes autorizados. A demonstração usa dados fictícios e coleta apenas nome do paciente. A evolução para implantação real exige desenho próprio de identidade, acesso por unidade, proteção e governança dos dados.


## 6. Aprendizados e próximos passos

Pontos técnicos demonstrados pelo projeto: a importância de distinguir a identidade da vaga da identidade do agendamento; o uso de transações para preservar a reserva sob concorrência; a diferença entre auditoria de eventos e Event Sourcing; e a necessidade de justificar o custo de cada padrão arquitetural.

O autor deve complementar esta seção com suas observações após executar o projeto, revisar o código e gravar a demonstração. Não se presumem experiências pessoais que ainda não ocorreram.

Próximos passos: validar o fluxo com uma unidade, incluir usuários e escopos reais, melhorar paginação, ampliar as regras de disponibilidade, integrar notificações, realizar testes de carga e desenhar uma implantação com alta disponibilidade.

