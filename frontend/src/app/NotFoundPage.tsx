import { Link } from "react-router";
import styles from "./NotFoundPage.module.css";

export function NotFoundPage() {
  return (
    <section className={styles.secao}>
      <h1>Página não encontrada</h1>
      <p>Este endereço não existe ou ainda não está disponível.</p>
      <Link to="/">Voltar ao início</Link>
    </section>
  );
}
