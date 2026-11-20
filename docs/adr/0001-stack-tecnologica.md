# ADR-001: Stack tecnológica — Java/Spring Boot, React e PostgreSQL

**Status:** Accepted
**Date:** 2026-11-20
**Deciders:** Dono do projeto / tech lead

## Context

O produto definido em [`docs/product/assistente-builds-poe2.md`](../product/assistente-builds-poe2.md) precisa de:

- Um formulário interativo, com seletores dependentes (Classe → Ascendência), e uma tela de progresso em tempo real durante a geração.
- Um backend que orquestre a chamada ao LLM (Google Gemini, camada gratuita), valide a resposta contra um catálogo curado e aplique limites de uso por IP e por usuário.
- Login opcional com Google, histórico "Minhas builds" e uma área de administração do catálogo, versionada por *patch*.
- Dados relacionais e com integridade: catálogo versionado, builds, avaliações e contadores de cota.

Restrições:

- **Prazo:** MVP completo até 2026-11-22. Escolher uma stack desconhecida custaria dias que não existem.
- **Custo zero:** sem serviços pagos de infraestrutura além do mínimo necessário para hospedar o app.
- **Esqueleto existente:** o repositório já tem Spring Boot 4.1 (Java 25) com Spring AI 2.0, Spring Security + OAuth2 Client, JPA, Flyway e o driver PostgreSQL.

O dono do projeto definiu a stack: **Java + Spring Boot** no backend, **React** no frontend e **PostgreSQL** para persistência. Este ADR registra a decisão, as alternativas e como as peças se encaixam.

## Decision

| Camada | Tecnologia | Observação |
| :--- | :--- | :--- |
| Backend | Java 25 + Spring Boot 4.1 (Spring MVC) | Já está no esqueleto |
| Integração com LLM | Spring AI 2.0 com o starter do **Google GenAI (Gemini)** | Substitui o `spring-ai-starter-model-openai` atual |
| Autenticação | Spring Security + OAuth2 Client (login Google), sessão no servidor | Modelo BFF: o React não manipula tokens |
| Persistência | PostgreSQL + Spring Data JPA + Flyway | Todo o schema versionado por migrations |
| Frontend | React + TypeScript, build com Vite | SPA na pasta `frontend/` deste repositório |
| Entrega | Um único artefato: o build do React é empacotado como recurso estático do Spring Boot | Mesma origem: sem CORS, cookie de sessão simples |
| Progresso da geração | Server-Sent Events (`SseEmitter` do Spring MVC) | Alimenta a tela "Gerando Build" com etapas reais |
| Limite de uso | Contadores no próprio PostgreSQL | Evita adicionar Redis no MVP |

## Options Considered

### Backend

#### Option A: Java + Spring Boot (escolhida)

| Dimension | Assessment |
| :--- | :--- |
| Complexity | Média: a configuração é verbosa, mas o esqueleto já existe |
| Cost | Zero de licenças. O consumo de memória do servidor é maior que o de Node |
| Scalability | Mais que suficiente para o volume do MVP, que é limitado pela cota do Gemini |
| Team familiarity | Alta: é a stack declarada pelo time |

**Pros:** Spring AI dá saída estruturada (JSON → *record* Java) e troca de provedor por configuração. Spring Security resolve o login Google com poucas linhas. O esqueleto já está pronto, e a tipagem forte combina com a validação do catálogo.
**Cons:** O ciclo de build e boot é mais lento que em Node. São duas linguagens no projeto (Java + TypeScript).

#### Option B: Node.js (NestJS/Express) full-stack TypeScript

| Dimension | Assessment |
| :--- | :--- |
| Complexity | Média |
| Cost | Zero |
| Scalability | Suficiente |
| Team familiarity | Menor que Java, segundo a escolha do time |

**Pros:** Uma linguagem só e tipos compartilhados entre front e back.
**Cons:** Joga fora o esqueleto existente e perde o Spring AI e o Spring Security já configurados. Aprender a stack consome o prazo de 2 dias.

### Frontend

#### Option A: React SPA (Vite) servida pelo Spring Boot (escolhida)

| Dimension | Assessment |
| :--- | :--- |
| Complexity | Média: o build do front precisa entrar no build Maven |
| Cost | Zero, um único deploy |
| Scalability | Suficiente para o MVP |
| Team familiarity | Alta: é a escolha declarada |

**Pros:** Formulário dinâmico, tela de progresso e cartões do resultado ficam naturais em React. Mesma origem que a API, então o login Google por sessão e cookie funciona sem CORS e sem tokens no navegador.
**Cons:** Exige integrar o build do Vite ao `mvn package`. Em desenvolvimento, o Vite roda separado com proxy para o Spring.

#### Option B: React em deploy separado (CDN) consumindo a API

**Pros:** Deploy independente do front e cache em CDN.
**Cons:** CORS, cookies *cross-site* (`SameSite=None`) e dois pipelines de deploy. Custo extra de configuração sem ganho no MVP.

#### Option C: Thymeleaf (renderização no servidor)

**Pros:** Nenhum build de front.
**Cons:** Formulário dependente e progresso em tempo real exigiriam JavaScript manual. Descartada pela decisão do time por React.

### Persistência

#### Option A: PostgreSQL (escolhida)

**Pros:** Relacional (catálogo versionado com integridade referencial), `jsonb` para guardar o conteúdo da build gerada, Flyway já configurado e camadas gratuitas disponíveis em vários provedores.
**Cons:** Precisa de uma instância rodando: Docker local e serviço gerenciado em produção.

#### Option B: MongoDB

**Pros:** A build gerada é naturalmente um documento.
**Cons:** O catálogo, o versionamento por *patch* e a validação são relacionais. O `jsonb` do PostgreSQL cobre a parte documental.

### Progresso da geração

| Opção | Prós | Contras |
| :--- | :--- | :--- |
| **SSE (escolhida)** | Etapas reais (validação → LLM → validação da saída → persistência), sem *polling*, suportado nativamente pelo Spring MVC e pelo `EventSource`/`fetch` no navegador | A conexão fica aberta por até ~30 s |
| *Polling* de um job | Simples e resistente a quedas de conexão | Exige uma tabela de jobs e um intervalo de *polling*; o progresso fica "aos saltos" |
| Requisição síncrona + barra simulada | O mais simples | O progresso é falso, e o produto pede etapas reais |

## Trade-off Analysis

- **Velocidade de entrega vs. pureza:** o prazo de 2 dias pesa mais que tudo. Spring Boot ganha por já estar configurado. Um único artefato (React dentro do Spring) elimina CORS, um segundo deploy e o tratamento de tokens no browser.
- **Sessão vs. JWT:** com mesma origem e login só pelo Google, uma sessão no servidor com cookie `HttpOnly` é mais simples e mais segura que gerenciar JWT no React. O custo é o estado no servidor, aceitável com uma única instância.
- **Redis vs. PostgreSQL para limites:** os contadores diários por IP e por usuário cabem numa tabela com *upsert*. Redis só se justifica com várias instâncias ou alto volume, o que a cota do Gemini impede de qualquer forma.

## Consequences

- **Fica mais fácil:** trocar de provedor de LLM (o Spring AI abstrai, e a ADR-002 isola o adaptador); o deploy, com um único jar; a segurança do login, sem tokens no front.
- **Fica mais difícil:** escalar o front separadamente do back; o build tem duas toolchains (Maven + Node).
- **A revisitar:**
  - Escalar para várias instâncias exigiria sessão compartilhada (Spring Session JDBC) e reavaliar os limites.
  - Se a camada gratuita do Gemini mudar, reavaliar o provedor; a troca fica restrita ao adaptador.

## Action Items

1. [ ] Trocar `spring-ai-starter-model-openai` pelo starter do Google GenAI no `pom.xml`, confirmando o *artifactId* na BOM do Spring AI 2.0.1. É uma mudança de dependência: confirmar antes de aplicar.
2. [ ] Criar `frontend/` com Vite + React + TypeScript e o proxy de dev para o Spring.
3. [ ] Integrar o build do front ao Maven (ex.: `frontend-maven-plugin`, que é uma nova dependência de build e precisa de confirmação), copiando `dist/` para `static/`.
4. [ ] Criar `compose.yaml` com PostgreSQL para desenvolvimento local.
5. [ ] Configurar `application.properties` por perfil (`dev`/`prod`), com segredos (chave do Gemini, client secret do Google) só por variável de ambiente.
6. [ ] Registrar o app OAuth no Google Cloud Console (redirect `/login/oauth2/code/google`).
