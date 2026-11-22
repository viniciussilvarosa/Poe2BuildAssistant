import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router";
import type { BuildRequest } from "../../api/builds";
import { getCatalogOptions, type CatalogOptions } from "../../api/catalog";
import moldura from "../../ui/Moldura.module.css";
import styles from "./CreateBuildPage.module.css";

const AI_CHOOSES = "Deixar a IA escolher";
const MAX_CONCEPT = 500;
/** O catálogo não distingue a mão: escudo e broquel vão num grupo próprio (QA-10). */
const OFF_HAND = new Set(["Shield", "Buckler"]);

/**
 * Sem acento e em minúsculas ("SHÓT" acha "Shot"), com o apóstrofo curvo que o teclado do celular
 * põe sozinho igual ao reto ("alchemist’s" acha "Alchemist's Boon"; chaos do PR #95).
 */
const normalize = (text: string) =>
  text
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[\u2018\u2019\u02BC\u0060\u00B4]/g, "'")
    .toLowerCase();

type Group = { label?: string; names: string[] };

/** Erro trazido da tela de progresso (objeto simples: o histórico do navegador não clona Error). */
export interface GenerationError {
  status: number;
  detail: string;
  retryAt?: string;
}

export interface CreateBuildState {
  request?: BuildRequest;
  error?: GenerationError;
}

type Selector = "characterClass" | "ascendancy" | "weaponType" | "mainSkill" | "playstyle";
type Choices = Record<Selector, string>;

const EMPTY: Choices = { characterClass: "", ascendancy: "", weaponType: "", mainSkill: "", playstyle: "" };
const SELECTORS = Object.keys(EMPTY) as Selector[];

/** Só as chaves de seletor do pedido anterior: o conceito tem campo próprio (chaos K1). */
function choicesFrom(request?: BuildRequest): Choices {
  return Object.fromEntries(SELECTORS.map((k) => [k, request?.[k] ?? ""])) as Choices;
}

/**
 * Descarta escolhas que não estão no catálogo carregado (patch novo, histórico antigo): o seletor
 * mostraria "Deixar a IA escolher" e o valor iria escondido no pedido (chaos K2).
 */
function keepValid(choices: Choices, options?: CatalogOptions): Choices {
  const has = (names: string[], value: string) => (names.includes(value) ? value : "");
  const classes = options?.classes ?? [];
  const characterClass = has(classes.map((c) => c.name), choices.characterClass);
  const ascendancies = classes
    .filter((c) => !characterClass || c.name === characterClass)
    .flatMap((c) => c.ascendancies.map((a) => a.name));
  const weaponType = has((options?.weaponTypes ?? []).map((w) => w.name), choices.weaponType);
  const skills = (options?.skillGems ?? []).filter(
    (g) => !weaponType || g.weaponTypes.length === 0 || g.weaponTypes.includes(weaponType),
  );
  return {
    characterClass,
    ascendancy: has(ascendancies, choices.ascendancy),
    weaponType,
    mainSkill: has(skills.map((g) => g.name), choices.mainSkill),
    playstyle: has((options?.playstyles ?? []).map((p) => p.name), choices.playstyle),
  };
}

export function CreateBuildPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { request, error } = (useLocation().state ?? {}) as CreateBuildState;
  const conceptRef = useRef<HTMLTextAreaElement>(null);

  // A URL é editável: corta no limite do backend, como a digitação já faz (chaos K4).
  const [concept, setConcept] = useState((request?.concept ?? params.get("conceito") ?? "").slice(0, MAX_CONCEPT));
  const [choices, setChoices] = useState<Choices>(() => choicesFrom(request));
  const [conceptMissing, setConceptMissing] = useState(false);
  const [options, setOptions] = useState<CatalogOptions>();
  const [optionsError, setOptionsError] = useState<string>();
  const [skillQuery, setSkillQuery] = useState("");

  useEffect(() => {
    let active = true;
    getCatalogOptions()
      .then((o) => {
        if (!active) return;
        setOptions(o);
        setChoices((c) => keepValid(c, o));
      })
      .catch((e: { detail?: string }) => {
        if (!active) return;
        setOptionsError(e.detail ?? "Não foi possível carregar as opções.");
        setChoices(EMPTY);
      });
    return () => {
      active = false;
    };
  }, []);

  const classes = options?.classes ?? [];
  const ascendancies = classes
    .filter((c) => !choices.characterClass || c.name === choices.characterClass)
    .flatMap((c) => c.ascendancies.map((a) => ({ name: a.name, className: c.name })));
  const skills = (options?.skillGems ?? []).filter(
    (g) => !choices.weaponType || g.weaponTypes.length === 0 || g.weaponTypes.includes(choices.weaponType),
  );

  const weaponNames = (options?.weaponTypes ?? []).map((w) => w.name);
  const weaponGroups: Group[] = [
    { label: "Armas", names: weaponNames.filter((n) => !OFF_HAND.has(n)) },
    { label: "Mão secundária", names: weaponNames.filter((n) => OFF_HAND.has(n)) },
  ].filter((g) => g.names.length > 0);
  // A busca só esconde opções: a escolha atual continua na lista, senão o select mentiria.
  const matchesFor = (text: string) => {
    const words = normalize(text).split(/\s+/).filter(Boolean);
    return skills.map((g) => g.name).filter((n) => words.every((w) => normalize(n).includes(w)));
  };
  const searching = skillQuery.trim() !== "";
  const matches = matchesFor(skillQuery);
  const skillNames = skills
    .map((g) => g.name)
    .filter((n) => !searching || matches.includes(n) || n === choices.mainSkill);
  // Sem catálogo (carregando ou com erro) não há o que dizer: "Nenhuma habilidade" seria falso.
  const searchStatus = !searching || !options
    ? ""
    : matches.length === 0
      ? `Nenhuma habilidade com “${skillQuery.trim()}”`
      : matches.length === 1
        ? "1 habilidade"
        : `${matches.length} habilidades`;

  // A busca escolhe de verdade (#94): sozinha no resultado, a habilidade vai para o select se nada foi
  // escolhido ainda; Enter escolhe o primeiro resultado em vez de enviar o formulário.
  function search(text: string) {
    setSkillQuery(text);
    const found = matchesFor(text);
    if (text.trim() && found.length === 1 && !choices.mainSkill) choose("mainSkill", found[0]);
  }

  function searchKey(event: KeyboardEvent<HTMLInputElement>) {
    // Busca vazia não tem resultado: o Enter volta a ser o do formulário. O Enter que fecha uma
    // composição de texto (IME) também não é escolha (chaos do PR #95).
    if (event.key !== "Enter" || !searching || event.nativeEvent.isComposing || event.keyCode === 229) return;
    event.preventDefault();
    if (matches.length > 0) choose("mainSkill", matches[0]);
  }

  function choose(selector: Selector, value: string) {
    setChoices((current) => {
      const next = { ...current, [selector]: value };
      if (selector === "characterClass") {
        const owner = classes.find((c) => c.ascendancies.some((a) => a.name === next.ascendancy));
        if (owner && owner.name !== value) next.ascendancy = "";
      }
      if (selector === "ascendancy" && value) {
        next.characterClass = ascendancies.find((a) => a.name === value)?.className ?? next.characterClass;
      }
      if (selector === "weaponType" && value) {
        const gem = options?.skillGems.find((g) => g.name === next.mainSkill);
        if (gem && gem.weaponTypes.length > 0 && !gem.weaponTypes.includes(value)) next.mainSkill = "";
      }
      return next;
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = concept.trim();
    if (!trimmed) {
      setConceptMissing(true);
      conceptRef.current?.focus();
      return;
    }
    const filled = Object.fromEntries(Object.entries(choices).filter(([, v]) => v));
    navigate("/gerando", { state: { request: { ...filled, concept: trimmed } } });
  }

  const optionList = (names: string[]) =>
    names.map((name) => (
      <option key={name} value={name}>
        {name}
      </option>
    ));

  const selector = (id: Selector, label: string, groups: Group[], before?: ReactNode) => (
    <div className={styles.seletor}>
      <label htmlFor={id}>{label}</label>
      {before}
      <select id={id} value={choices[id]} onChange={(e) => choose(id, e.target.value)}>
        <option value="">{AI_CHOOSES}</option>
        {groups.map((g) =>
          g.label ? (
            <optgroup key={g.label} label={g.label}>
              {optionList(g.names)}
            </optgroup>
          ) : (
            optionList(g.names)
          ),
        )}
      </select>
    </div>
  );

  const skillSearch = (
    <>
      <input
        type="search"
        className={styles.busca}
        aria-label="Buscar habilidade"
        aria-controls="mainSkill"
        aria-describedby="busca-habilidade-status"
        placeholder="Buscar habilidade"
        value={skillQuery}
        onChange={(e) => search(e.target.value)}
        onKeyDown={searchKey}
      />
      <p id="busca-habilidade-status" className={styles.buscaStatus} aria-live="polite">
        {searchStatus}
      </p>
    </>
  );

  return (
    <section className={styles.pagina}>
      <h1>Configurar personagem</h1>
      <p className={styles.apoio}>
        Só o conceito é obrigatório. O que você deixar em aberto, o mentor escolhe e explica o porquê.
      </p>

      {error && (
        <div role="alert" className={styles.aviso}>
          <p>{error.detail}</p>
          {formatRetry(error.retryAt) && <p>Você pode gerar de novo a partir de {formatRetry(error.retryAt)}.</p>}
          {error.status === 429 && (
            <p>
              Quem entra com a conta Google tem um limite diário maior.{" "}
              <a href="/oauth2/authorization/google">Entrar com Google</a>
            </p>
          )}
        </div>
      )}
      {optionsError && (
        <p role="alert" className={styles.aviso}>
          {optionsError}
        </p>
      )}

      <form className={moldura.moldura} onSubmit={submit} noValidate>
        <div className={styles.conceito}>
          <label htmlFor="conceito">Conceito do personagem</label>
          <textarea
            id="conceito"
            ref={conceptRef}
            rows={3}
            maxLength={MAX_CONCEPT}
            value={concept}
            required
            aria-invalid={conceptMissing}
            aria-describedby={conceptMissing ? "conceito-erro" : undefined}
            onChange={(e) => {
              setConcept(e.target.value);
              setConceptMissing(false);
            }}
            placeholder="Ex.: um arqueiro que congela tudo e nunca para de se mover"
          />
          {conceptMissing && (
            <p id="conceito-erro" className={styles.erroCampo}>
              Conceito do personagem é obrigatório. Descreva a ideia em uma frase.
            </p>
          )}
        </div>

        <div className={moldura.separador} aria-hidden="true" />

        <fieldset className={styles.escolhas}>
          <legend>Escolhas opcionais</legend>
          {selector("characterClass", "Classe", [{ names: classes.map((c) => c.name) }])}
          {selector("ascendancy", "Ascendência", [{ names: ascendancies.map((a) => a.name) }])}
          {selector("weaponType", "Arma", weaponGroups)}
          {selector("mainSkill", "Habilidade principal", [{ names: skillNames }], skillSearch)}
          {selector("playstyle", "Estilo de jogo", [{ names: (options?.playstyles ?? []).map((p) => p.name) }])}
        </fieldset>

        <div className={moldura.separador} aria-hidden="true" />

        <div className={styles.rodape}>
          <p className={styles.patch}>{options ? `Opções do patch ${options.patch}` : " "}</p>
          <button type="submit" className={moldura.acaoPrincipal}>
            Gerar Build com IA
          </button>
        </div>
      </form>
    </section>
  );
}

/** retryAt vem em UTC; mostra no fuso do navegador. Data inválida não derruba a tela (chaos K3). */
function formatRetry(retryAt?: string) {
  const date = retryAt ? new Date(retryAt) : undefined;
  if (!date || Number.isNaN(date.getTime())) return undefined;
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(date);
}
