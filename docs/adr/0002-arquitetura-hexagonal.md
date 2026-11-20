# ADR-002: Arquitetura Hexagonal (Ports & Adapters) no backend

**Status:** Accepted
**Date:** 2026-11-20
**Deciders:** Dono do projeto / tech lead

## Context

O backend (ver [ADR-001](0001-stack-tecnologica.md)) concentra a regra de negócio mais sensível do produto: **nenhuma build exibida pode citar um item fora do catálogo vigente** (critério de sucesso de 100%). Em volta dessa regra existem várias dependências externas instáveis:

| Dependência | Por que é instável |
| :--- | :--- |
| LLM (Google Gemini, camada gratuita) | Já trocamos de provedor uma vez (OpenAI → Gemini); cota e termos podem mudar |
| Export da árvore de passivas da GGG (GitHub) | Muda a cada *patch*; formato fora do nosso controle |
| Login Google | Provedor de identidade externo; pode ganhar outros no futuro |
| PostgreSQL | Estável, mas o domínio não deve ser moldado pelas entidades JPA |

O dono do projeto definiu que o backend segue **arquitetura Hexagonal e suas melhores práticas**. Este ADR fixa como ela é aplicada: pacotes, portas, regras de dependência, como garantir as regras e as concessões conscientes, dado o prazo de 2026-11-22.

## Decision

### 1. Regra de dependência

```
adapter.in  ──►  application  ──►  domain
adapter.out ──►  application (implementa portas de saída)
config      ──►  todos (só para montar o grafo de beans)
```

- **`domain`:** Java puro. Zero dependência de Spring, JPA, Jackson, Lombok ou Spring AI. Modelos são `record`s ou classes imutáveis com invariantes no construtor.
- **`application`:** casos de uso. Depende só de `domain` e das próprias portas. **Concessão consciente:** pode usar `@Service` e `@Transactional`; nenhuma outra anotação ou classe de framework.
- **`adapter`:** toda a tecnologia (web, JPA, Spring AI, Spring Security, cliente HTTP do GitHub). Nenhum adaptador conhece outro adaptador.
- Um adaptador de entrada **nunca** chama um adaptador de saída nem um repositório diretamente. Ele só chama portas de entrada (casos de uso).

### 2. Estrutura de pacotes

Organizado por camada, com subpacotes por funcionalidade. Pacote base existente: `com.poe.Poe2BuildAssistant`.

```
com.poe.Poe2BuildAssistant
├── domain
│   ├── build        # BuildRequest, GeneratedBuild, GemSetup, PassiveDirection,
│   │                # GearPriorities, BuildValidator, BuildViolation, Playstyle
│   ├── catalog      # Catalog, CatalogVersion, CharacterClass, Ascendancy, WeaponType,
│   │                # SkillGem, SupportGem, PassiveNotable, CatalogSlice
│   ├── quota        # Requester (Anonymous(ip) | Authenticated(userId)), QuotaPolicy, QuotaDecision
│   ├── feedback     # BuildRating
│   └── shared       # exceções de domínio (QuotaExceeded, ProviderUnavailable, InvalidBuildRequest…)
├── application
│   ├── port
│   │   ├── in       # GenerateBuildUseCase, GetBuildUseCase, ListMyBuildsUseCase,
│   │   │            # RateBuildUseCase, GetCatalogOptionsUseCase,
│   │   │            # ManageCatalogUseCase, PublishCatalogVersionUseCase, ImportPassiveTreeUseCase
│   │   └── out      # BuildGeneratorPort, BuildRepositoryPort, CatalogRepositoryPort,
│   │                # QuotaCounterPort, PassiveTreeSourcePort, GenerationProgressPort,
│   │                # RatingRepositoryPort, ClockPort
│   └── service      # implementações dos casos de uso (ex.: GenerateBuildService)
├── adapter
│   ├── in
│   │   └── web      # @RestController, DTOs de request/response, @ControllerAdvice → ProblemDetail,
│   │                # SSE (SseEmitter), resolução do Requester a partir da sessão/IP
│   └── out
│       ├── ai          # GeminiBuildGeneratorAdapter (Spring AI, saída estruturada, prompts em resources)
│       ├── persistence # entidades JPA, Spring Data repositories, mappers entidade ↔ domínio
│       ├── quota       # contadores diários no PostgreSQL (upsert)
│       ├── passivetree # leitura/parse do export da GGG
│       └── progress    # implementação de GenerationProgressPort que publica no SseEmitter
└── config           # SecurityConfig (OAuth2 Google, papel ADMIN), beans, propriedades tipadas
```

### 3. Portas principais

| Porta | Tipo | Responsabilidade | Adaptador |
| :--- | :--- | :--- | :--- |
| `GenerateBuildUseCase` | in | Orquestra cota → catálogo → geração → validação → persistência | `adapter.in.web` |
| `GetCatalogOptionsUseCase` | in | Opções dos seletores (ascendências filtradas por classe) do catálogo publicado | `adapter.in.web` |
| `ManageCatalogUseCase` / `PublishCatalogVersionUseCase` | in | CRUD do rascunho e publicação da versão do *patch* | `adapter.in.web` (admin) |
| `BuildGeneratorPort` | out | `GeneratedBuildDraft generate(BuildRequest, CatalogSlice, List<BuildViolation> feedback)` | `adapter.out.ai` |
| `CatalogRepositoryPort` | out | Carrega a versão publicada; gerencia rascunhos | `adapter.out.persistence` |
| `BuildRepositoryPort` | out | Salva/consulta builds e "Minhas builds" | `adapter.out.persistence` |
| `QuotaCounterPort` | out | Incrementa e lê o contador diário por `Requester` de forma atômica | `adapter.out.quota` |
| `PassiveTreeSourcePort` | out | Obtém os notables do export oficial | `adapter.out.passivetree` |
| `GenerationProgressPort` | out | Publica etapas (`VALIDATING_INPUT`, `GENERATING`, `VALIDATING_OUTPUT`, `SAVING`, `DONE`) | `adapter.out.progress` |
| `ClockPort` | out | Hora atual, para reset de cota e testes determinísticos | `config` |

### 4. Fluxo do caso de uso central (`GenerateBuildService`)

1. O adaptador web converte o DTO em `GenerateBuildCommand` (conceito + restrições opcionais + `Requester`) e abre o SSE.
2. `QuotaPolicy` (domínio) decide com base no `QuotaCounterPort`. Se o limite estourou, lança `QuotaExceeded(retryAt)`.
3. `CatalogRepositoryPort` carrega o `Catalog` publicado. O `BuildRequest` valida as restrições contra ele (ex.: ascendência pertence à classe).
4. `CatalogSlice` (domínio) recorta o catálogo relevante para as restrições e o estilo, para caber no prompt.
5. `BuildGeneratorPort.generate(...)` devolve um `GeneratedBuildDraft`.
6. `BuildValidator` (domínio puro) confere cada nome contra o `Catalog` e as restrições do usuário. Se houver violações, o serviço regenera passando as violações como *feedback*, até o limite configurado. Se elas persistirem, lança uma exceção de domínio e nada inválido é exibido.
7. Salva via `BuildRepositoryPort`. Toda build é persistida; a de visitante fica **sem dono** (`owner = null`) e nunca aparece em histórico. Isso permite a avaliação 👍/👎 e as métricas para todos. Decisão de 2026-11-20 durante o `/create-tasks`.
8. Cada etapa é emitida via `GenerationProgressPort`.

**Por que essa divisão:** o critério de sucesso de 100% vive em `BuildValidator`, uma classe sem framework, testável com testes unitários puros e independente do LLM. O prompt e a sintaxe do Spring AI ficam no adaptador. Trocar o Gemini por outro provedor significa escrever um novo adaptador, sem tocar no domínio.

### 5. Boas práticas adotadas

- **Modelos separados por camada:** DTO web ≠ modelo de domínio ≠ entidade JPA. Mapeamento manual em cada adaptador, sem MapStruct, para não adicionar dependência sob prazo.
- **Erros:** exceções de domínio tipadas. O `@ControllerAdvice` traduz para `ProblemDetail` (RFC 9457) com mensagens em pt-BR. O adaptador de IA traduz HTTP 429/5xx do provedor em `ProviderUnavailable(retryAt)`.
- **Identidade:** o domínio só conhece `Requester`. O adaptador web o constrói a partir do `OAuth2User` da sessão ou do IP, e o Spring Security nunca vaza para a aplicação.
- **Catálogo versionado:** `CatalogVersion` com estado `DRAFT`/`PUBLISHED`. Há só uma publicada por vez, e a build guarda a versão usada.
- **Build gerada:** persistida como `jsonb` (conteúdo) mais colunas relacionais (dono, versão do catálogo, data, avaliação).
- **Configuração:** limites de cota, número de regenerações e modelo do LLM em `@ConfigurationProperties` (em `config`), entregues à aplicação como valores, sem `@Value` espalhado.
- **Segredos:** só por variável de ambiente.

### 6. Como as regras são garantidas

- **Testes de arquitetura com ArchUnit:** o domínio não importa `org.springframework..`, `jakarta.persistence..`, `lombok..` nem `com.fasterxml..`; a aplicação não importa `adapter..`; adaptadores não importam outros adaptadores. ArchUnit é uma **nova dependência de teste** e precisa de confirmação antes de entrar no `pom.xml`.
- **Pirâmide de testes por camada:**

| Camada | Tipo de teste | Infra |
| :--- | :--- | :--- |
| domain | Unitário puro | Nenhuma |
| application | Unitário com *fakes* em memória das portas de saída | Nenhuma |
| adapter.in.web | `@WebMvcTest` | MockMvc |
| adapter.out.persistence | `@DataJpaTest` + Flyway | PostgreSQL real (Testcontainers, nova dependência; ou o Postgres do `compose.yaml`) |
| adapter.out.ai | Teste com resposta gravada (*fixture*) do provedor | Sem chamada real ao Gemini nos testes automatizados |

### 7. Frontend (React)

A arquitetura Hexagonal se aplica ao backend. No React, adotamos a mesma ideia de isolamento, sem cerimônia:

```
frontend/src
├── features/        # create-build, generating, build-result, my-builds, admin-catalog
├── api/             # único ponto que conhece URLs/HTTP/SSE (client + tipos dos DTOs)
├── components/      # UI compartilhada
└── app/             # rotas, layout, sessão do usuário
```

Componentes nunca chamam `fetch` direto; usam `api/`.

## Options Considered

### Option A: Hexagonal por camada com subpacotes por funcionalidade (escolhida)

| Dimension | Assessment |
| :--- | :--- |
| Complexity | Média: mais interfaces e mapeamentos que um CRUD em camadas |
| Cost | ~10–20% a mais de código (portas, mappers) |
| Scalability | Alta para evolução: troca de provedor de LLM e novas entradas (ex.: fase 2 do Loot Filter) sem tocar no domínio |
| Team familiarity | Declarada como padrão pelo time |

**Pros:** A regra crítica (validação do catálogo) fica isolada e testável. A troca de LLM é barata, e já aconteceu uma vez. As regras de dependência são verificáveis por ArchUnit com poucas regras, porque as camadas estão no topo.
**Cons:** Boilerplate de mapeamento num prazo de 2 dias. Funcionalidades ficam espalhadas por três árvores de pacote.

### Option B: Hexagonal por módulo de funcionalidade (`build/`, `catalog/`, `quota/`, cada um com domain/application/adapter)

**Pros:** Alta coesão por funcionalidade; prepara para um monólito modular (ex.: Spring Modulith).
**Cons:** Mais pacotes e regras de ArchUnit por módulo, além de dependências entre módulos (build depende de catalog e quota) para resolver agora. Mais cerimônia do que o prazo comporta. Pode ser adotada depois por movimentação de pacotes, sem reescrita.

### Option C: Camadas tradicionais (controller → service → repository)

**Pros:** Menos código e mais rápido de entregar.
**Cons:** Entidades JPA e Spring AI vazam para a regra de negócio, e a troca de provedor de LLM toca nos serviços. Contraria a decisão do time.

## Trade-off Analysis

O custo da Hexagonal é boilerplate, e o prazo é curto. O benefício está exatamente onde o produto tem mais risco: o LLM é a peça mais instável, e a validação do catálogo é o critério de sucesso que não admite falha. A opção A concentra esse benefício com o menor custo estrutural.

Duas concessões reduzem a cerimônia sem quebrar a regra de dependência:

- `@Service`/`@Transactional` na camada de aplicação.
- Mapeamento manual em vez de uma biblioteca.

A opção B fica como evolução natural se o produto crescer além do MVP.

## Consequences

- **Fica mais fácil:**
  - Trocar ou adicionar provedores de LLM.
  - Testar a regra dos 100% sem rede.
  - Adicionar a fase 2 (gerador de Loot Filter como novo caso de uso + adaptador de download).
  - Dividir o trabalho em cards por porta/adaptador no `/create-tasks`.
- **Fica mais difícil:**
  - Entregar rápido coisas triviais (um CRUD do admin passa por DTO → caso de uso → porta → entidade).
  - Onboarding de quem não conhece Ports & Adapters.
- **A revisitar:**
  - Se a concessão do `@Transactional` incomodar, mover a demarcação de transação para um *decorator* em `config`.
  - Migrar para a Option B se surgirem mais de ~5 funcionalidades.
  - Renomear o pacote base para minúsculas (`com.poe.poe2buildassistant`), convenção Java, de preferência antes de existir código.

## Action Items

1. [ ] Criar a árvore de pacotes da seção 2 com `package-info.java` descrevendo cada camada.
2. [ ] Decidir sobre ArchUnit e Testcontainers como dependências de teste (confirmação de nova dependência) e escrever as regras da seção 6.
3. [ ] Implementar `domain.catalog` e `domain.build.BuildValidator` com testes unitários primeiro: é o núcleo do critério de 100%.
4. [ ] Definir as migrations Flyway iniciais: catálogo versionado, builds (`jsonb`), avaliações, contadores de cota, usuários.
5. [ ] Implementar `GenerateBuildService` com *fakes* das portas, depois os adaptadores reais (Gemini, JPA, SSE).
6. [ ] Mapear exceções de domínio → `ProblemDetail` pt-BR no `@ControllerAdvice`.
7. [ ] Decidir sobre a renomeação do pacote base antes do primeiro código de domínio.
