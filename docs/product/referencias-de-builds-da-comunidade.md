# Referências de builds da comunidade (heatmap e escolhas dos top jogadores)

> **Status: adiada (2026-11-21).** A ideia fica registrada com o que foi pesquisado e decidido. Antes de retomar, resolver as perguntas abertas, principalmente a fonte dos dados.

## Problem

A build gerada pela IA é uma sugestão isolada: o jogador não tem como saber se ela se parece com o que funciona na prática. Jogadores mais experientes usam como referência o *heatmap* da árvore de passivas e as escolhas dos melhores jogadores com builds parecidas (equilíbrio entre dano e camadas defensivas), mas isso exige ir a outro site e saber ler esses dados.

## Who it's for

- **Jogador iniciante/casual:** quer confiança de que o plano sugerido é parecido com o que dá certo.
- **Theorycrafter casual:** quer comparar a sugestão da IA com o que a comunidade realmente joga.

## Success criteria

A definir quando a funcionalidade for retomada (ver Open questions).

## Scope

### In

- **Direção preferida (sugestão discutida, não decidida):** mostrar as referências **depois** que a build for gerada, como comparação ("o que a IA sugeriu" × "o que jogadores com builds parecidas usam"), em vez de usá-las no prompt antes da geração. Motivos levantados:
  - a build da IA continua validada contra o catálogo, sem interferência;
  - o usuário vê as duas coisas e decide;
  - o prompt e a latência da geração não aumentam;
  - quando não houver referência para um conceito raro, a comparação simplesmente não aparece.
- Usar as referências como insumo do prompt fica como evolução posterior, se houver como medir que melhora as builds.

### Out

- Integração com conta/personagem do jogador (OAuth do jogador), como já decidido no MVP.
- Qualquer coleta de dados de sites de terceiros sem autorização.

## Acceptance criteria

A definir quando a fonte de dados e o escopo forem decididos.

## Constraints & dependencies

- **A API oficial da GGG não oferece esses dados prontos.** O acesso é por OAuth 2.1, com cadastro do app feito manualmente pela GGG, por e-mail e a critério dela; há relatos de demora ou falta de resposta. A cobertura de PoE2 na API é descrita como limitada. A GGG só libera oficialmente, fora das APIs, os dados das árvores de passivas e do Atlas.
- **O heatmap e as escolhas dos top jogadores existem no poe.ninja** (site de terceiros), montados a partir de personagens públicos. Não foi encontrada API pública documentada nem termos de uso; usar exigiria autorização do mantenedor.
- Alternativas sem dependência externa que foram levantadas e não escolhidas por ora:
  - builds bem avaliadas (👍) do próprio app com conceito, classe ou skill parecidos;
  - builds de referência curadas pela equipe na área de administração.

## Open questions

| Pergunta | Dono | Status |
| --- | --- | --- |
| Qual será a fonte dos dados: poe.ninja com autorização, API oficial da GGG (cadastro do app), dados próprios do app ou referências curadas? | Product owner | Aberta |
| Se for o poe.ninja: o mantenedor autoriza o uso, com quais condições (cache, crédito, frequência)? | Product owner | Aberta |
| Se for a API oficial: a GGG aprova o cadastro, e o PoE2 tem endpoints de ladder e personagens suficientes? | Tech lead | Aberta |
| Como definir "build parecida" (mesma classe? ascendência? habilidade principal? conceito?) | Product owner | Aberta |
| Como definir "top jogadores com equilíbrio entre dano e defesa" a partir dos dados disponíveis? | Product owner | Aberta |
| Referências antes da geração (no prompt), depois (comparação) ou as duas? | Product owner | Aberta, com preferência por "depois" |

Fontes consultadas em 2026-11-21: [Developer Docs - Path of Exile](https://www.pathofexile.com/developer/docs), [fórum: cadastro de app OAuth](https://www.pathofexile.com/forum/view-thread/3821465), [fórum: acesso à API](https://www.pathofexile.com/forum/view-thread/3984124), [poe.ninja PoE2](https://poe.ninja/poe2), [Launching Path of Exile 2 Builds](https://poe.ninja/posts/launching-builds), [Developer Docs: dados](https://br.pathofexile.com/developer/docs/data).
