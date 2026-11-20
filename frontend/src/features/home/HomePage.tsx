import { useState } from "react";
import { Link } from "react-router";
import styles from "./HomePage.module.css";
import { PassiveConstellation } from "./PassiveConstellation";

const EXAMPLES = ["arqueiro de gelo", "monge de raios", "bruxa que invoca mortos"];

export function HomePage() {
  const [concept, setConcept] = useState("");
  const trimmed = concept.trim();
  const createLink = trimmed ? `/criar?${new URLSearchParams({ conceito: trimmed })}` : "/criar";

  return (
    <>
      <section className={styles.hero}>
        <div className={styles.pergunta}>
          <h1>
            <label htmlFor="conceito">Que personagem você quer jogar?</label>
          </h1>
          <p className={styles.apoio}>
            Descreva a ideia com suas palavras. Você recebe um plano inicial de gemas, passivas e equipamentos para
            seguir na campanha.
          </p>
          <textarea
            id="conceito"
            className={styles.campo}
            rows={3}
            maxLength={500}
            value={concept}
            onChange={(e) => setConcept(e.target.value)}
            placeholder="Ex.: um arqueiro que congela tudo e nunca para de se mover"
          />
          <div className={styles.acoes}>
            <Link to={createLink} className={styles.criar}>
              Criar Build
            </Link>
            <p className={styles.exemplos}>
              <span>Experimente:</span>
              {EXAMPLES.map((example) => (
                <button key={example} type="button" onClick={() => setConcept(example)}>
                  {example}
                </button>
              ))}
            </p>
          </div>
        </div>
        <PassiveConstellation />
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
