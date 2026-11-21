# Mentor de Builds · Path of Exile 2

Web app que transforma uma ideia livre de personagem ("arqueiro que congela tudo", "bruxa que invoca mortos") num plano inicial de build para *Path of Exile 2*, explicado em português para quem está começando.

**No ar:** https://poe2buildassistant-877547383313.us-central1.run.app

## O que ele faz

- **Gera a build a partir do conceito.** Só a descrição é obrigatória. Classe, ascendência, arma, habilidade principal e estilo de jogo são opcionais: o que ficar em aberto, a IA escolhe e explica por quê.
- **Mostra o progresso em tempo real**, etapa por etapa, enquanto o Google Gemini monta a build.
- **Nunca inventa itens.** Toda gema, suporte, ascendência e notable citada é conferida contra o catálogo do *patch* vigente. Nomes errados são corrigidos quando possível; senão a IA tenta de novo com o erro apontado.
- **Entrega um plano de campanha:** habilidade principal e suportes, direção na árvore de passivas com notables-chave, prioridades de equipamento e dicas para os atos.
- **Login opcional com Google** para guardar as builds em "Minhas builds" e ter um limite diário maior. Visitantes também podem gerar, com limite por dia.
- **Avaliação 👍/👎** de cada build.
- **Área de administração** para manter o catálogo do jogo, versionado por *patch*.

A interface é em português; os nomes do jogo ficam em inglês, exatamente como aparecem no jogo.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 25, Spring Boot 4, Spring AI (Google Gemini), Spring Security (OAuth2 com Google), Flyway |
| Frontend | React 19, TypeScript, Vite (servido pelo próprio Spring, num único jar) |
| Banco | PostgreSQL |
| Testes | JUnit, Testcontainers, Vitest, Testing Library |
| Produção | Google Cloud Run + Neon (PostgreSQL), imagem Docker, CI no GitHub Actions |

O backend segue arquitetura hexagonal: o domínio e os casos de uso não conhecem Spring, banco nem IA. Os adaptadores (`adapter/in/web`, `adapter/out/ai`, `adapter/out/persistence`…) ligam o núcleo ao mundo externo. Detalhes e motivos nas ADRs abaixo.

## Rodando localmente

**Pré-requisitos:** Java 25 e Docker. O Node é baixado pelo Maven, não precisa instalar.

1. Copie `.env.example` para `.env` e preencha:
   - `DB_PASSWORD` (e, se quiser, `DB_USERNAME`/`DB_NAME`) para o PostgreSQL local;
   - `GEMINI_API_KEY`, criada no [Google AI Studio](https://aistudio.google.com);
   - opcionais: `GOOGLE_CLIENT_ID` e `GOOGLE_CLIENT_SECRET` (login com Google, redirect `http://localhost:8080/login/oauth2/code/google`), `ADMIN_EMAILS` (e-mails com acesso à administração, separados por vírgula) e `QUOTA_IP_SALT`.
2. Suba o banco:
   ```bash
   docker compose up -d
   ```
3. Rode o app (as migrations do Flyway criam as tabelas e carregam o catálogo):
   ```bash
   set -a; source .env; set +a; ./mvnw spring-boot:run
   ```
4. Abra http://localhost:8080.

Para mexer no frontend com recarga instantânea, deixe o backend rodando e, em outro terminal, `cd frontend && npm install && npm run dev`: o Vite abre em http://localhost:5173 e repassa a API para a porta 8080.

## Testes

```bash
./mvnw verify
```

Roda tudo: typecheck, build e testes do frontend (Vitest) e os testes do backend, incluindo os de integração com PostgreSQL real via Testcontainers (precisa do Docker).

## CI/CD e deploy

Todo pull request e todo merge na `main` passam pelo `./mvnw verify` no GitHub Actions. A cada merge, a imagem Docker é publicada no GitHub Container Registry. Como a pipeline funciona, como rodar a imagem, a produção no Cloud Run + Neon e o checklist de deploy estão em [`docs/deploy.md`](docs/deploy.md).

## Documentação

- [Produto](docs/product/assistente-builds-poe2.md): problema, público, escopo e critérios de aceite.
- [ADR-001: stack tecnológica](docs/adr/0001-stack-tecnologica.md)
- [ADR-002: arquitetura hexagonal](docs/adr/0002-arquitetura-hexagonal.md)
- [Deploy](docs/deploy.md)

## Aviso

Projeto independente e acadêmico, não afiliado nem endossado pela Grinding Gear Games. *Path of Exile*, a arte e o logo são marcas e propriedade da Grinding Gear Games.

## Licença

[MIT](LICENSE)
