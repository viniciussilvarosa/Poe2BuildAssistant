import { Link, NavLink, Outlet } from "react-router";
import styles from "./Layout.module.css";

export function Layout() {
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
          <NavLink to="/minhas-builds">Minhas builds</NavLink>
          <a href="/#como-funciona">Como funciona</a>
          <a href="/oauth2/authorization/google" className={styles.entrar}>
            Entrar
          </a>
        </nav>
      </header>
      <main id="conteudo" className={styles.conteudo}>
        <Outlet />
      </main>
      <footer className={styles.rodape}>
        <p>Projeto independente não afiliado à Grinding Gear Games.</p>
      </footer>
    </div>
  );
}
