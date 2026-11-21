import { useEffect, useRef, useState, type FormEvent } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router";
import type { BuildRequest } from "../../api/builds";
import { getCatalogOptions, type CatalogOptions } from "../../api/catalog";
import moldura from "../../ui/Moldura.module.css";
import styles from "./CreateBuildPage.module.css";

const AI_CHOOSES = "Deixar a IA escolher";
const MAX_CONCEPT = 500;

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

export function CreateBuildPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { request, error } = (useLocation().state ?? {}) as CreateBuildState;
  const conceptRef = useRef<HTMLTextAreaElement>(null);

  const [concept, setConcept] = useState(request?.concept ?? params.get("conceito") ?? "");
  const [choices, setChoices] = useState<Choices>(() => ({ ...EMPTY, ...request }));
  const [conceptMissing, setConceptMissing] = useState(false);
  const [options, setOptions] = useState<CatalogOptions>();
  const [optionsError, setOptionsError] = useState<string>();

  useEffect(() => {
    let active = true;
    getCatalogOptions()
      .then((o) => active && setOptions(o))
      .catch((e: { detail?: string }) => active && setOptionsError(e.detail ?? "Não foi possível carregar as opções."));
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
    navigate("/gerando", { state: { request: { concept: trimmed, ...filled } } });
  }

  const selector = (id: Selector, label: string, names: string[]) => (
    <div className={styles.seletor}>
      <label htmlFor={id}>{label}</label>
      <select id={id} value={choices[id]} onChange={(e) => choose(id, e.target.value)}>
        <option value="">{AI_CHOOSES}</option>
        {names.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>
    </div>
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
          {error.retryAt && <p>Você pode gerar de novo a partir de {formatRetry(error.retryAt)}.</p>}
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
          {selector("characterClass", "Classe", classes.map((c) => c.name))}
          {selector("ascendancy", "Ascendência", ascendancies.map((a) => a.name))}
          {selector("weaponType", "Arma", (options?.weaponTypes ?? []).map((w) => w.name))}
          {selector("mainSkill", "Habilidade principal", skills.map((g) => g.name))}
          {selector("playstyle", "Estilo de jogo", (options?.playstyles ?? []).map((p) => p.name))}
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

/** retryAt vem em UTC; mostra no fuso do navegador. */
function formatRetry(retryAt: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(retryAt));
}
