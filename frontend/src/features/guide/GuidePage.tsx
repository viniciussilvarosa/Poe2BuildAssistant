import { Link } from "react-router";
import moldura from "../../ui/Moldura.module.css";
import styles from "./GuidePage.module.css";
import {
  CONCEPT_AFTER,
  CONCEPT_BEFORE,
  CONCEPT_TIPS,
  createLink,
  GUIDE_CLASSES,
  GUIDE_SECTIONS,
} from "./guideContent";

export function GuidePage() {
  return (
    <article className={styles.guia}>
      <header className={styles.abertura}>
        <div className={styles.intro}>
          <h1>Guia rápido do Path of Exile 2</h1>
          <p className={styles.apoio}>
            Você já sabe o que são classes, builds e chefes. Aqui está o que muda no Path of Exile 2, em cinco minutos,
            antes de montar sua build com o mentor.
          </p>
        </div>
        <nav aria-label="Seções do guia" className={`${moldura.moldura} ${styles.indice}`}>
          <p className={styles.indiceTitulo}>Neste guia</p>
          <ol>
            {GUIDE_SECTIONS.map((s) => (
              <li key={s.id}>
                <a href={`#${s.id}`}>{s.title}</a>
              </li>
            ))}
            <li>
              <a href="#conceito">Como descrever seu personagem</a>
            </li>
          </ol>
        </nav>
      </header>

      {GUIDE_SECTIONS.map((s) => (
        <section key={s.id} id={s.id} className={styles.secao} aria-labelledby={`${s.id}-titulo`}>
          <div className={styles.texto}>
            <h2 id={`${s.id}-titulo`}>{s.title}</h2>
            {s.paragraphs.map((p) => (
              <p key={p}>{p}</p>
            ))}
            {s.id === "classes" && (
              <dl className={styles.classes}>
                {GUIDE_CLASSES.map((c) => (
                  <div key={c.name}>
                    <dt>{c.name}</dt>
                    <dd>{c.style}</dd>
                  </div>
                ))}
              </dl>
            )}
            {s.ideas.length > 0 && (
              <p className={styles.ideias}>
                <span className={styles.rotulo}>Ideias: </span>
                {s.ideas.map((idea, i) => (
                  <span key={idea}>
                    {i > 0 && <span aria-hidden="true"> · </span>}
                    <Link to={createLink(idea)}>{idea}</Link>
                  </span>
                ))}
              </p>
            )}
          </div>
          {s.image && (
            <figure className={`${styles.figura} ${s.image.kind === "recorte" ? styles.recorte : styles.ilustracao}`}>
              {/* Decorativa: o texto ao lado já diz tudo; a legenda só nomeia o personagem. */}
              <img
                src={s.image.src}
                alt=""
                loading="lazy"
                style={s.image.focus ? { objectFit: "cover", objectPosition: `${s.image.focus} 50%` } : undefined}
              />
              {s.image.caption && <figcaption>{s.image.caption}</figcaption>}
            </figure>
          )}
        </section>
      ))}

      <section id="conceito" className={`${moldura.moldura} ${styles.conceito}`} aria-labelledby="conceito-titulo">
        <h2 id="conceito-titulo">Como descrever seu personagem</h2>
        <p>Uma frase basta, mas quanto mais destes pontos ela tiver, mais a build vai parecer com o que você imaginou:</p>
        <dl className={styles.dicas}>
          {CONCEPT_TIPS.map((t) => (
            <div key={t.label}>
              <dt>{t.label}</dt>
              <dd>{t.examples}</dd>
            </div>
          ))}
        </dl>
        <div className={moldura.separador} aria-hidden="true" />
        <p className={styles.comparacao}>
          Em vez de <q>{CONCEPT_BEFORE}</q>, tente <q>{CONCEPT_AFTER}</q>.
        </p>
        <div>
          <Link to={createLink(CONCEPT_AFTER)} className={moldura.acaoPrincipal}>
            Montar minha build
          </Link>
        </div>
      </section>
    </article>
  );
}
