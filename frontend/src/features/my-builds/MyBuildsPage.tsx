import { useEffect, useState } from "react";
import { Link } from "react-router";
import { ApiError } from "../../api/client";
import { getMyBuilds, type MyBuild } from "../../api/me";
import { useSession } from "../../app/session";
import { LOGIN_URL } from "../../components/AuthMenu";
import moldura from "../../ui/Moldura.module.css";
import styles from "./MyBuildsPage.module.css";

type Load = { status: "loading" } | { status: "ready"; builds: MyBuild[] } | { status: "visitor" } | { status: "failed"; detail: string };

const DATE = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const MAX_CONCEPT = 60;

/** Data inválida não derruba a página: só some (chaos M3). */
function formatDate(iso: string) {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "" : DATE.format(date);
}

/** Corta por caracteres, não por unidades UTF-16: um emoji no limite não vira "�" (chaos M4). */
function summary(concept: string) {
  const chars = Array.from(concept);
  return chars.length > MAX_CONCEPT ? `${chars.slice(0, MAX_CONCEPT).join("").trimEnd()}…` : concept;
}

export function MyBuildsPage() {
  const { me } = useSession();
  const [state, setLoad] = useState<Load>({ status: "loading" });
  // Depois de sair, o histórico some na hora: é dado de quem acabou de usar o computador (chaos M2).
  const load: Load = me === null ? { status: "visitor" } : state;

  useEffect(() => {
    getMyBuilds().then(
      (builds) => setLoad({ status: "ready", builds }),
      (e: unknown) => setLoad(e instanceof ApiError && e.status === 401
        ? { status: "visitor" }
        : { status: "failed", detail: e instanceof ApiError ? e.detail : "Não foi possível abrir suas builds." }),
    );
  }, []);

  return (
    <section className={styles.pagina}>
      <h1>Minhas builds</h1>
      {load.status === "loading" && <p role="status">Carregando…</p>}
      {load.status === "failed" && <p role="alert">{load.detail}</p>}
      {load.status === "visitor" && (
        <div className={styles.convite}>
          <p>Entre com sua conta Google para guardar as builds que gerar e ter um limite diário maior.</p>
          <a href={LOGIN_URL} className={moldura.acaoPrincipal}>
            Entrar com Google
          </a>
        </div>
      )}
      {load.status === "ready" && load.builds.length === 0 && (
        <div className={styles.convite}>
          <p>Você ainda não gerou nenhuma build. As que gerar daqui para a frente aparecem aqui.</p>
          <Link to="/criar" className={moldura.acaoPrincipal}>
            Criar Build
          </Link>
        </div>
      )}
      {load.status === "ready" && load.builds.length > 0 && (
        <ol className={styles.lista}>
          {load.builds.map((b) => (
            <li key={b.id}>
              <Link to={`/builds/${b.id}`}>
                <strong>{b.mainSkill}</strong>
                <span>
                  {b.characterClass} ({b.ascendancy})
                </span>
                <span className={styles.conceito} title={b.concept}>
                  “{summary(b.concept)}”
                </span>
                <time dateTime={b.createdAt}>{formatDate(b.createdAt)}</time>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
