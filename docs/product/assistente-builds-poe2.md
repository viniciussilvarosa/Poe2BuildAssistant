# Assistente de Builds para Path of Exile 2

## Problem

Jogadores iniciantes de *Path of Exile 2* travam na complexidade dos sistemas do jogo (gemas de habilidade, gemas de suporte, árvore de passivas extensa, ascendências). Hoje a alternativa é pesquisar fora do jogo:

- **poe.ninja** mostra dados brutos do *ladder* competitivo, sem didática e sem partir de uma ideia do jogador.
- **Maxroll.gg** oferece guias rígidos, escritos para o *meta*, em inglês e cheios de jargão.

Nenhum dos dois transforma um conceito livre ("arqueiro mágico", "monge de raios") num plano inicial acessível. O resultado é tentativa e erro, combinações sem sinergia, travamento na campanha e abandono do personagem (fluxo AS-IS do pré-projeto).

Evidência: análise de concorrentes e fluxo AS-IS do pré-projeto. Não há pesquisa com usuários até o momento.

## Who it's for

- **Jogador iniciante/casual (público principal):** conhece pouco o PoE 2 e precisa de um ponto de partida claro para a campanha, explicado em português.
- **Theorycrafter casual:** já conhece o básico e quer validar rápido uma ideia temática antes de investir horas num personagem.
- **Administrador/curador (equipe do projeto):** mantém o catálogo do jogo atualizado a cada *patch* e acompanha o consumo da cota da IA.

## Success criteria

Medidos pelo próprio sistema durante o MVP:

| Métrica | Meta |
| :--- | :--- |
| Builds exibidas que citam apenas itens existentes no catálogo do *patch* vigente (gemas, suportes, ascendências, notables) | 100% |
| Gerações concluídas sem erro (excluindo bloqueio por limite de uso) | ≥ 90% |
| Tempo entre "Gerar Build" e o resultado na tela (p90) | ≤ 30 s |
| Avaliações positivas (👍) sobre o total de avaliações (👍/👎) | ≥ 70% |

## Scope

### In

**Formulário de criação**
- Campo de texto livre **Conceito do personagem**, o único campo obrigatório.
- Seletores **opcionais**: Classe, Ascendência, Arma, Habilidade principal e Estilo de jogo (ex.: corpo a corpo, à distância, invocação, magia). Cada um tem a opção "Deixar a IA escolher".
- Os seletores só oferecem valores do catálogo vigente. A Ascendência fica restrita à Classe escolhida.
- Um seletor preenchido vira restrição obrigatória para a IA.

**Geração da build**
- Geração por LLM do **Google Gemini API, na camada gratuita** (custo de IA zero).
- O prompt usa só itens do catálogo curado. A resposta é validada contra o catálogo, e nenhum nome fora dele é exibido ao usuário.
- Tela de progresso com percentual e etapas enquanto a build é gerada.

**Resultado ("Sua Build Sugerida")**
- Resumo do estilo de jogo e análise de sinergia, com justificativa de cada escolha que a IA fez no lugar do usuário.
- Gemas: habilidade principal e suportes.
- Árvore de passivas: direção (região/caminho) e **3 a 8 notables-chave reais**, validados contra o export oficial da árvore da GGG, com explicação didática. Sem visualização gráfica.
- Prioridades de equipamento: arma, armadura e acessórios.
- Dicas de campanha.
- Botão **"Copiar Resumo"**.
- Avaliação 👍/👎 da build.

**Idioma**
- Interface e explicações em **português (pt-BR)**. Nomes de jogo (gemas, notables, itens, classes) em **inglês**, como na comunidade.

**Contas e limites**
- Login **opcional**, somente com **Google**.
- Visitante anônimo gera builds com limite diário por IP. A build aparece na tela, mas não é salva em histórico.
- Usuário logado tem limite diário maior, e as builds vão para **"Minhas builds"** (histórico consultável).
- Quando o limite do usuário ou a cota do provedor se esgota: mensagem clara em pt-BR com o horário para tentar de novo, e o formulário preenchido é preservado.

**Catálogo e administração**
- Árvore de passivas importada do export oficial da GGG (`grindinggear/poe2-skilltree-export`).
- Classes, ascendências, tipos de arma, gemas de habilidade e gemas de suporte cadastrados e revisados pelo administrador numa **tela de administração**, versionados por *patch*.
- Acesso à administração restrito ao papel de administrador.

**Prioridade de corte** (se o prazo apertar, sai primeiro → por último):
1. Avaliação 👍/👎.
2. Login com Google e "Minhas builds".
3. Tela de administração (o catálogo passa a ser carregado por dados iniciais).

O núcleo protegido é: formulário → geração → validação contra o catálogo → resultado.

### Out

- **Integração com a conta Path of Exile (OAuth GGG):** removida do produto. Não há login PoE nem leitura de personagens, ligas ou perfil.
- **Exportação de Loot Filter:** fora do MVP, nem por API nem por download. O download de arquivo `.filter` gerado por templates fica para a fase 2.
- Builds otimizadas para *meta*/*endgame* ou garantia de eficácia competitiva.
- Seletor de **item único-chave** e de **camada defensiva** (armadura/evasão/escudo de energia).
- Visualização gráfica da árvore de passivas.
- Login por e-mail/senha ou outros provedores.
- Nomes bilíngues ou nomes do cliente em português.
- Galeria pública de builds de outros usuários.
- Provedor de IA pago, incluindo a OpenAI citada no pré-projeto.

## Acceptance criteria

- **Dado** um visitante que preencheu só o conceito "arqueiro mágico", **quando** clica em "Gerar Build com IA", **então** recebe uma build completa (resumo, gemas, passivas, equipamentos, dicas) com classe, ascendência, arma e skill escolhidas pela IA e justificadas em pt-BR.
- **Dado** que o usuário escolheu Classe = Ranger e Arma = Bow, **quando** a build é gerada, **então** a build usa Ranger e Bow.
- **Dado** que o usuário escolheu uma Classe, **quando** abre o seletor de Ascendência, **então** só aparecem as ascendências dessa classe.
- **Dado** o campo de conceito vazio, **quando** o usuário tenta gerar, **então** a geração é bloqueada com uma mensagem indicando o campo obrigatório.
- **Dado** que a IA devolveu um nome de gema, suporte, ascendência ou notable fora do catálogo vigente, **quando** a resposta é processada, **então** esse conteúdo nunca é exibido ao usuário: o sistema corrige/regenera ou mostra um erro amigável.
- **Dado** uma geração em andamento, **quando** o usuário está na tela de progresso, **então** vê o percentual e as etapas avançando até o resultado.
- **Dado** um visitante que atingiu o limite diário, **quando** tenta gerar outra build, **então** vê uma mensagem em pt-BR com o horário de liberação, o formulário continua preenchido e é sugerido o login para aumentar o limite.
- **Dado** que a cota do provedor de IA se esgotou, **quando** qualquer usuário tenta gerar, **então** vê uma mensagem em pt-BR com a previsão de retorno e o formulário é preservado.
- **Dado** um usuário logado com Google, **quando** gera uma build, **então** ela aparece em "Minhas builds" e pode ser reaberta depois.
- **Dado** um visitante, **quando** gera uma build, **então** ela não aparece em nenhum histórico.
- **Dado** uma build exibida, **quando** o usuário clica em "Copiar Resumo", **então** o texto do resumo vai para a área de transferência.
- **Dado** uma build exibida, **quando** o usuário avalia com 👍 ou 👎, **então** a avaliação é registrada e conta para a métrica de satisfação.
- **Dado** um administrador logado, **quando** cadastra ou edita gemas, suportes, armas, classes ou ascendências e publica a versão do *patch*, **então** os seletores e a validação passam a usar essa versão.
- **Dado** um usuário sem papel de administrador, **quando** tenta acessar a administração, **então** o acesso é negado.
- **Dado** qualquer página do app, **então** o aviso "Projeto independente não afiliado à Grinding Gear Games." está visível.

## Constraints & dependencies

- **Prazo:** MVP completo em **22/11/2026**, dois dias depois da definição deste escopo. O risco de não caber é alto e foi aceito, com a ordem de corte definida em *Scope › In*.
- **Custo de IA zero:** depende da camada gratuita do Google Gemini API. Os limites de requisição (por minuto e por dia) restringem o volume de uso e de testes, e os termos podem mudar sem aviso.
- **Dados do jogo:** a API oficial do PoE não fornece gemas, suportes, armas nem ascendências. Só a árvore de passivas é publicada oficialmente (GitHub da GGG). Todo o resto depende da curadoria manual do administrador, que é a principal fonte de esforço e de defasagem a cada *patch*.
- **Precisão:** o conhecimento do LLM sobre PoE 2 é desatualizado e incompleto. A qualidade depende do catálogo e da validação, não do modelo.
- **Login Google:** depende de credenciais OAuth do Google para o app.
- **Pré-projeto desatualizado:** o documento original cita OpenAI, OAuth do PoE e exportação de Loot Filter. Precisa ser revisado para refletir este escopo.
- **Aviso legal:** "Projeto independente não afiliado à Grinding Gear Games."

## Open questions

| Question | Owner | Status |
| :--- | :--- | :--- |
| Qual o limite diário de gerações para visitante (por IP) e para usuário logado? Depende da cota real da camada gratuita do Gemini. | Tech lead — após medir a cota | Aberta |
| Qual modelo Gemini específico será usado (equilíbrio entre qualidade, latência ≤ 30 s e cota gratuita)? | Tech lead | Aberta |
| A camada gratuita do Gemini pode usar os prompts para treinar modelos. É preciso avisar o usuário ou ter um termo de privacidade? | Product owner | Aberta |
| Quais gemas e itens entram no catálogo do MVP: o catálogo completo do *patch* ou um subconjunto viável até 22/11? | Product owner + curador | Aberta |
| Qual a lista fechada de opções de "Estilo de jogo"? | Product owner | Aberta |
| Como um usuário recebe o papel de administrador (lista fixa de contas Google, atribuição manual)? | Tech lead | Aberta |
| O que o sistema faz quando a validação falha repetidamente: quantas regenerações antes de mostrar erro? | Tech lead | Aberta |
| O usuário pode regenerar ou ajustar uma build já gerada, e isso consome o limite? | Product owner | Aberta |
| O que acontece com builds em "Minhas builds" quando o catálogo muda de *patch* (marcar como desatualizada, manter como está)? | Product owner | Aberta |
| Como coletar a métrica de tempo p90 e a taxa de sucesso (painel, logs)? | Tech lead | Aberta |
