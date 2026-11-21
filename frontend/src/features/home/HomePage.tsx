import { useState } from "react";
import { Link } from "react-router";
import styles from "./HomePage.module.css";

const EXAMPLES = ["arqueiro de gelo", "monge de raios", "bruxa que invoca mortos"];

export function HomePage() {
  const [concept, setConcept] = useState("");
  const trimmed = concept.trim();
  const createLink = trimmed ? `/criar?${new URLSearchParams({ conceito: trimmed })}` : "/criar";

  return (
    <>
      {/* Arte oficial do Path of Exile 2 (sem o logo) como palco da página. */}
      <div className={styles.arte} aria-hidden="true" />

      <section className={styles.hero}>
        <h1>
          <label htmlFor="conceito">Que personagem você quer jogar?</label>
        </h1>
        <p className={styles.apoio}>
          Descreva a ideia com suas palavras. Você recebe um plano inicial de gemas, passivas e equipamentos para seguir
          na campanha.
        </p>
        <p className={styles.guia}>
          Novo no Path of Exile 2? <Link to="/guia">Leia o guia rápido</Link> antes de começar.
        </p>

        {/* Moldura no idioma dos tooltips de item do jogo; o separador divide o campo das sugestões. */}
        <div className={styles.tooltip}>
          <textarea
            id="conceito"
            className={styles.campo}
            rows={3}
            maxLength={500}
            value={concept}
            onChange={(e) => setConcept(e.target.value)}
            placeholder="Ex.: um arqueiro que congela tudo e nunca para de se mover"
          />
          <div className={styles.separador} aria-hidden="true" />
          <div className={styles.acoes}>
            <p className={styles.exemplos}>
              <span>Experimente:</span>
              {EXAMPLES.map((example) => (
                <button key={example} type="button" onClick={() => setConcept(example)}>
                  {example}
                </button>
              ))}
            </p>
            <Link to={createLink} className={styles.criar}>
              Criar Build
            </Link>
          </div>
        </div>
      </section>

      <section id="como-funciona" className={styles.passos} aria-labelledby="como-funciona-titulo">
        <h2 id="como-funciona-titulo">Como funciona</h2>
        <ol>
          <li>
            <strong>Descreva o personagem.</strong> Uma frase basta, como “bruxa que invoca mortos”.
          </li>
          <li>
            <strong>Fixe o que já decidiu.</strong> Classe, arma ou habilidade são opcionais: o que ficar em aberto, o
            mentor escolhe e explica o porquê.
          </li>
          <li>
            <strong>Siga o plano.</strong> Gemas, direção na árvore de passivas e prioridades de equipamento, com nomes
            exatamente como aparecem no jogo.
          </li>
        </ol>
      </section>
    </>
  );
}
