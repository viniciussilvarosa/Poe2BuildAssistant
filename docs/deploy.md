# CI/CD e deploy

## Pipeline (`.github/workflows/ci.yml`)

| Evento | Job `build` | Job `image` |
| --- | --- | --- |
| Pull request | `./mvnw verify` | Constrói a imagem para validar o `Dockerfile`, sem publicar |
| Push na `main` (merge) | `./mvnw verify` | Publica `ghcr.io/viniciussilvarosa/poe2buildassistant` |
| Manual (`workflow_dispatch`) | `./mvnw verify` | Só constrói, a não ser que rode na `main` |

- O `verify` cobre o frontend (npm ci, typecheck, build e Vitest) e o backend (testes unitários e de integração com Testcontainers, que usam o Docker do runner). Quando algum teste falha, os relatórios ficam como artefato `relatorios-de-teste` por 7 dias.
- A imagem usa o **mesmo jar** que passou nos testes (artefato `app-jar`); nada é recompilado.
- Tags publicadas: `sha-<commit completo>` (imutável, use esta no deploy) e `latest`.
- Um push novo num PR cancela o run anterior do mesmo PR.

Para exigir o CI verde antes do merge: *Settings → Branches → Branch protection rule* na `main` → *Require status checks to pass* → marcar **Build e testes** e **Imagem Docker**.

## Rodar a imagem

```bash
docker run -d --name poe2 -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/<banco> \
  -e DB_USERNAME=<usuário> -e DB_PASSWORD=<senha> \
  -e GEMINI_API_KEY=<chave> \
  -e GOOGLE_CLIENT_ID=<id> -e GOOGLE_CLIENT_SECRET=<segredo> \
  -e ADMIN_EMAILS=<email1,email2> \
  -e QUOTA_IP_SALT=<texto aleatório longo e fixo> \
  ghcr.io/viniciussilvarosa/poe2buildassistant:sha-<commit>
```

- Perfil `prod` por padrão: logs em JSON (ECS) e IP real do cliente lido do `X-Forwarded-For`, então o app deve ficar atrás de um proxy confiável.
- Roda como usuário sem privilégios, na porta 8080 (ou em `SERVER_PORT`).
- `HEALTHCHECK` faz `GET /api/health`; o mesmo endpoint serve para o health check do host.
- O Flyway aplica as migrations na subida. O banco precisa existir, mas as tabelas não.
- Sem `GEMINI_API_KEY`, a aplicação falha na subida com a mensagem de qual variável definir.

## Checklist de deploy

**Antes**
- [ ] CI verde no commit que vai subir (jobs **Build e testes** e **Imagem Docker**).
- [ ] PRs do release revisados e mergeados pelo dono do repositório.
- [ ] Migrations novas (`src/main/resources/db/migration`) lidas: nenhuma apaga ou reescreve dados sem plano de volta. Backup do banco feito se houver migration.
- [ ] Variáveis de ambiente do host conferidas contra a lista acima. `QUOTA_IP_SALT` é o mesmo do deploy anterior (trocar zera a cota dos visitantes do dia).
- [ ] URI de redirecionamento do Google OAuth cadastrada para o domínio de produção (`https://<domínio>/login/oauth2/code/google`).
- [ ] Cota do Gemini disponível para o volume esperado (camada gratuita × faturamento ativo).
- [ ] Proxy reverso com timeout de leitura ≥ 150 s em `/api/builds/generate` (stream SSE da geração).
- [ ] Tag da imagem anterior anotada, para o rollback.

**Durante**
- [ ] Deploy pela tag `sha-<commit>`, não pela `latest`.
- [ ] Log `app.started` com o `version` e o `aiModel` esperados.
- [ ] `GET /api/health` responde `{"status":"ok"}`.
- [ ] Fluxo principal: home → Configurar → gerar uma build → página da build abre e aceita o voto.
- [ ] Login com Google e "Minhas builds" funcionando.
- [ ] Admin: o catálogo publicado aparece.

**Depois (primeiros 15 min)**
- [ ] Sem `http.unexpected_error` nem `build.generation.failed reason="unexpected"` nos logs.
- [ ] `ai.provider.misconfigured` ausente (chave, modelo ou faturamento errados).
- [ ] Duração de `ai.generation.call` dentro do normal (14–42 s no `gemini-3.6-flash`).

**Gatilhos de rollback** (voltar para a tag anterior)
- `GET /api/health` fora do ar por mais de 2 min depois da subida.
- Qualquer `ai.provider.misconfigured` ou falha de migration do Flyway na subida.
- Mais da metade das gerações terminando em erro que não seja `429` do Gemini.
- Login com Google quebrado.

Rollback: subir a tag `sha-` anterior. Migrations do Flyway não voltam sozinhas: se o deploy trouxe migration, restaurar o backup ou aplicar a migration inversa antes.
