# Observability

- platform: none (sem dashboards/alertas)
- logger: SLF4J + Logback nativos do Spring Boot, logging estruturado ECS no perfil `prod` (`logging.structured.format.console=ecs`) — sem dependência nova
- frontend: `console.error` como piso
- event naming: `<dominio>.<acao>[.<resultado>]` em minúsculas, ex.: `build.generation.completed`, `quota.blocked`, `catalog.published`
- PII: nunca logar IP puro, e-mail ou texto completo do conceito; usar `userId`, hash ou tamanho
- métricas de sucesso do produto (p90, taxa de sucesso, taxa 👍) são extraídas dos logs estruturados
