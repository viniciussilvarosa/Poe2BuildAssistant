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

## Produção: Cloud Run + Neon

| Peça | Onde |
| --- | --- |
| App | Cloud Run, serviço `poe2buildassistant`, região `us-central1`, projeto `poe2buildassistant`: https://poe2buildassistant-877547383313.us-central1.run.app |
| Imagem | Artifact Registry `us-central1-docker.pkg.dev/poe2buildassistant/poe2/poe2buildassistant` (limpeza automática: guarda as 2 mais recentes) |
| Banco | Neon, projeto `proud-breeze-19471968`, branch `production`, AWS US East 2 (Ohio) |
| Segredos | Secret Manager: `GEMINI_API_KEY`, `GOOGLE_CLIENT_SECRET`, `QUOTA_IP_SALT`, `DB_PASSWORD` |
| Identidade do app | Conta de serviço `poe2-run@poe2buildassistant.iam.gserviceaccount.com` (só lê os segredos acima) |

Configuração do serviço: 1 GiB e 1 vCPU, timeout de 300 s (cobre o stream de 150 s da geração), de 0 a **1** instância: a sessão de login fica na memória do processo (sem sessão compartilhada), então uma segunda instância faria o usuário perder o login ou a volta do Google cair em outra instância. Para escalar além de 1, primeiro mover a sessão para o banco (Spring Session JDBC). Com 0 instâncias, a primeira requisição depois de um tempo parado espera a subida do Java (~15 s).

**Banco (Neon):** o app usa a conexão com pooling (`DB_URL`, host `…-pooler…`); o Flyway usa a conexão direta (`SPRING_FLYWAY_URL`, o mesmo host sem `-pooler`), como recomenda o Neon para migrations. Os dois usam o usuário de `DB_USERNAME`/`SPRING_FLYWAY_USER` e a senha do segredo `DB_PASSWORD`. O Neon hiberna o banco depois de alguns minutos sem uso; a primeira consulta seguinte leva cerca de 1 s a mais.

**Atualizar** (enquanto o deploy automático não está no CI):

```bash
./mvnw -DskipTests package   # o commit já passou no CI
IMG=us-central1-docker.pkg.dev/poe2buildassistant/poe2/poe2buildassistant:sha-$(git rev-parse HEAD)
docker build -t "$IMG" . && docker push "$IMG"
gcloud run deploy poe2buildassistant --image="$IMG" --region=us-central1
```

O `gcloud run deploy` só com `--image` mantém variáveis, segredos e limites do deploy anterior.

**Rollback:** `gcloud run revisions list --service=poe2buildassistant --region=us-central1` e depois `gcloud run services update-traffic poe2buildassistant --region=us-central1 --to-revisions=<revisão anterior>=100`. Migrations não voltam junto: para desfazer dados, use o *restore* do branch no Neon (point-in-time).

**Trocar um segredo:** `printf '%s' '<novo valor>' | gcloud secrets versions add <NOME> --data-file=-` e um novo deploy (`gcloud run deploy poe2buildassistant --image=<a mesma imagem> --region=us-central1`) para o serviço ler a versão nova.

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
