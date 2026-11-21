import { useRef, useState } from "react";
import { rateBuild, type Rating } from "../../api/builds";
import styles from "./BuildResultPage.module.css";

const OPTIONS: { value: Rating; label: string }[] = [
  { value: "UP", label: "👍 Ajudou" },
  { value: "DOWN", label: "👎 Não ajudou" },
];

/**
 * Avaliação da build, a métrica de sucesso do produto (≥ 70% de 👍). A seleção aparece na hora e
 * é desfeita se o servidor não registrar.
 */
export function RatingButtons({ buildId }: { buildId: string }) {
  const [selected, setSelected] = useState<Rating>();
  const [saved, setSaved] = useState(false);
  const [failed, setFailed] = useState(false);
  // Só a resposta do clique mais recente vale: uma falha atrasada do 👍 não pode apagar o 👎
  // que o servidor já registrou depois (chaos R7).
  const latest = useRef(0);

  function rate(value: Rating) {
    const previous = selected;
    const request = ++latest.current;
    setSelected(value);
    setSaved(false);
    setFailed(false);
    rateBuild(buildId, value).then(
      () => request === latest.current && setSaved(true),
      () => {
        if (request !== latest.current) return;
        setSelected(previous);
        setFailed(true);
      },
    );
  }

  return (
    <div className={styles.avaliacao} role="group" aria-label="Esta build ajudou?">
      <p>Esta build ajudou?</p>
      <div>
        {OPTIONS.map((o) => (
          <button key={o.value} type="button" aria-pressed={selected === o.value} onClick={() => rate(o.value)}>
            {o.label}
          </button>
        ))}
      </div>
      <p role="status" className={styles.feedback}>
        {saved ? "Obrigado pela avaliação." : ""}
      </p>
      {failed && <p role="alert">Não foi possível registrar sua avaliação. Tente de novo.</p>}
    </div>
  );
}
