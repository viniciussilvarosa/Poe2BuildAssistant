import { useEffect, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { generateBuild, type GenerationStep, type ProgressEvent } from "../../api/builds";
import { ApiError, GENERIC_ERROR } from "../../api/client";
import moldura from "../../ui/Moldura.module.css";
import type { CreateBuildState } from "../create-build/CreateBuildPage";
import styles from "./GeneratingPage.module.css";

const STEPS: { step: GenerationStep; label: string }[] = [
  { step: "VALIDATING_INPUT", label: "Validando suas escolhas" },
  { step: "GENERATING", label: "Gerando build com IA" },
  { step: "VALIDATING_OUTPUT", label: "Conferindo gemas e passivas no catálogo" },
  { step: "SAVING", label: "Salvando" },
];

export function GeneratingPage() {
  const navigate = useNavigate();
  const { request } = (useLocation().state ?? {}) as CreateBuildState;
  const [progress, setProgress] = useState<ProgressEvent>({ step: "VALIDATING_INPUT", percent: 0 });
  // Uma geração por visita: o StrictMode monta o efeito duas vezes e cada POST gasta cota.
  const started = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    if (request && !started.current) {
      started.current = true;
      generateBuild(request, (event) => mounted.current && setProgress(event))
        .then((build) => mounted.current && navigate(`/builds/${build.id}`, { replace: true, state: { build } }))
        .catch((e: unknown) => {
          if (!mounted.current) return;
          if (!(e instanceof ApiError)) console.error("build.generation.unexpected", e);
          const error = e instanceof ApiError ? e : new ApiError(0, GENERIC_ERROR);
          navigate("/criar", {
            replace: true,
            state: { request, error: { status: error.status, detail: error.detail, retryAt: error.retryAt } },
          });
        });
    }
    return () => {
      mounted.current = false;
    };
  }, [request, navigate]);

  if (!request) {
    return <Navigate to="/criar" replace />;
  }

  const current = progress.step === "DONE" ? STEPS.length : STEPS.findIndex((s) => s.step === progress.step);

  return (
    <section className={styles.pagina}>
      <h1>Gerando sua build</h1>
      <p className={styles.conceito}>“{request.concept}”</p>

      <div className={moldura.moldura}>
        <div role="status" aria-live="polite" className={styles.estado}>
          <span className={styles.percentual}>{progress.percent}%</span>
          <span className={styles.etapaAtual}>{STEPS[current]?.label ?? "Pronto"}</span>
        </div>
        <progress className={styles.barra} max={100} value={progress.percent} aria-label="Progresso da geração" />

        <div className={moldura.separador} aria-hidden="true" />

        <ol className={styles.etapas}>
          {STEPS.map(({ step, label }, i) => (
            <li
              key={step}
              data-estado={i < current ? "feita" : i === current ? "em-andamento" : "pendente"}
              aria-current={i === current ? "step" : undefined}
            >
              {label}
            </li>
          ))}
        </ol>
      </div>

      <p className={styles.nota}>Costuma levar menos de um minuto. Mantenha esta página aberta.</p>
    </section>
  );
}
