import { Link, NavLink, Outlet, ScrollRestoration } from "react-router";
import { AuthMenu } from "../components/AuthMenu";
import styles from "./Layout.module.css";
import { LoginFailedNotice } from "./LoginFailedNotice";
import { SessionProvider, useSession } from "./session";

export function Layout() {
  return (
    <SessionProvider>
      <Page />
    </SessionProvider>
  );
}

function Page() {
  const { me } = useSession();
  return (
    <div className={styles.pagina}>
      <a className="pular-para-conteudo" href="#conteudo">
        Pular para o conteúdo
      </a>
      <header className={styles.topo}>
        <Link to="/" className={styles.marca}>
          <svg className={styles.simbolo} viewBox="0 0 32 32" aria-hidden="true">
            <path d="M16 4 28 16 16 28 4 16Z" />
            <circle cx="16" cy="16" r="3.5" />
          </svg>
          Mentor de Builds
        </Link>
        <nav aria-label="Principal" className={styles.menu}>
          <NavLink to="/" end>
            Início
          </NavLink>
          <NavLink to="/guia">Guia rápido</NavLink>
          <NavLink to="/minhas-builds">Minhas builds</NavLink>
          {me?.role === "ADMIN" && <NavLink to="/admin">Admin</NavLink>}
          <a href="/#como-funciona">Como funciona</a>
          <AuthMenu className={styles.entrar} />
        </nav>
      </header>
      <main id="conteudo" className={styles.conteudo}>
        <LoginFailedNotice />
        <Outlet />
      </main>
      <footer className={styles.rodape}>
        <p>Projeto independente não afiliado à Grinding Gear Games.</p>
        <p>Arte: Path of Exile 2 © Grinding Gear Games.</p>
      </footer>
      {/* Página nova abre no topo e "voltar" devolve a posição anterior (#92). Sem isto, a tela de
          progresso abria rolada até o rodapé, porque o botão de gerar fica no fim do formulário. */}
      <ScrollRestoration />
    </div>
  );
}
