import { Fragment, useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router";
import { getBuild, type SavedBuild } from "../../api/builds";
import { ApiError } from "../../api/client";
import moldura from "../../ui/Moldura.module.css";
import { GearSection, GemsSection, PassivesSection, SummarySection, TipsSection } from "./BuildSections";
import styles from "./BuildResultPage.module.css";
import { formatBuildSummary } from "./formatBuildSummary";
import { RatingButtons } from "./RatingButtons";

type Load = { status: "loading" } | { status: "ready"; build: SavedBuild } | { status: "missing" } | { status: "failed"; detail: string };

export function BuildResultPage() {
  const { id = "" } = useParams();
  // Uma montagem por build: ir de /builds/A para /builds/B sem sair da tela não pode manter A (chaos C2a).
  return <BuildResult key={id} id={id} />;
}

function BuildResult({ id }: { id: string }) {
  const fromGeneration = (useLocation().state as { build?: SavedBuild } | null)?.build;
  const initial: Load = fromGeneration?.id === id ? { status: "ready", build: fromGeneration } : { status: "loading" };
  const [load, setLoad] = useState<Load>(initial);

  useEffect(() => {
    if (initial.status === "ready") return;
    let active = true;
    setLoad({ status: "loading" });
    getBuild(id)
      .then((build) => active && setLoad({ status: "ready", build }))
      .catch((e: unknown) => {
        if (!active) return;
        // 400 = id que nem é um UUID: para quem abriu o link, é o mesmo que não existir.
        if (e instanceof ApiError && (e.status === 404 || e.status === 400)) {
          setLoad({ status: "missing" });
        } else {
          console.error("build.load.failed", e);
          setLoad({ status: "failed", detail: e instanceof ApiError ? e.detail : "Não foi possível abrir a build." });
        }
      });
    return () => {
      active = false;
    };
  }, [id]);

  if (load.status === "loading") {
    return (
      <section className={styles.pagina}>
        <p role="status">Abrindo a build…</p>
      </section>
    );
  }
  if (load.status !== "ready") {
    return (
      <section className={styles.pagina}>
        <h1>{load.status === "missing" ? "Build não encontrada" : "Não foi possível abrir a build"}</h1>
        <p className={styles.apoio}>
          {load.status === "missing" ? "O link pode estar incompleto ou a build não existe mais." : load.detail}
        </p>
        <Link to="/criar" className={moldura.acaoPrincipal}>
          Criar nova build
        </Link>
      </section>
    );
  }

  const { build, request } = load.build;
  const sections = [SummarySection, GemsSection, PassivesSection, GearSection, TipsSection];

  return (
    <section className={styles.pagina}>
      <header className={styles.cabecalho}>
        <p className={styles.apoio}>Sua build sugerida para “{request.concept}”</p>
        <h1>{build.mainSkill}</h1>
        <p className={styles.identidade}>
          {build.characterClass} <span aria-hidden="true">◆</span> {build.ascendancy}{" "}
          <span aria-hidden="true">◆</span> {build.weaponType}
        </p>
      </header>

      <div className={styles.layout}>
        <article className={`${moldura.moldura} ${styles.secoes}`}>
          {sections.map((Section, i) => (
            <Fragment key={i}>
              {i > 0 && <div className={moldura.separador} aria-hidden="true" />}
              <Section build={build} />
            </Fragment>
          ))}
        </article>

        <aside className={styles.lateral}>
          <CopySummary text={formatBuildSummary(load.build)} />
          <RatingButtons buildId={load.build.id} />
          <Link to="/criar">Criar nova build</Link>
        </aside>
      </div>
    </section>
  );
}

function CopySummary({ text }: { text: string }) {
  const [state, setState] = useState<"idle" | "copied" | "manual">("idle");

  async function copy() {
    try {
      // Exige contexto seguro (HTTPS ou localhost); sem ele, ou sem permissão, cai na cópia manual.
      await navigator.clipboard.writeText(text);
      setState("copied");
    } catch {
      setState("manual");
    }
  }

  return (
    <div className={styles.copiar}>
      <button type="button" className={moldura.acaoPrincipal} onClick={copy}>
        Copiar Resumo
      </button>
      <p role="status" className={styles.feedback}>
        {state === "copied" ? "Resumo copiado" : ""}
      </p>
      {state === "manual" && (
        <>
          <label htmlFor="resumo-manual">Copie o resumo abaixo (o navegador bloqueou a cópia automática):</label>
          <textarea id="resumo-manual" readOnly rows={10} value={text} onFocus={(e) => e.target.select()} />
        </>
      )}
    </div>
  );
}
