import { useEffect, useState } from "react";
import { useLocation, useSearchParams } from "react-router";
import styles from "./Layout.module.css";

/**
 * O backend redireciona para "?login=falhou" quando o login com Google falha (ex.: o usuário
 * cancelou na tela do Google). Mostra o aviso na página em que chegou e tira o parâmetro da URL,
 * para recarregar não repetir. Só o valor "falhou" é reconhecido; nada da URL vai para a tela.
 */
export function LoginFailedNotice() {
  const [params, setParams] = useSearchParams();
  const { pathname } = useLocation();
  const [shownOn, setShownOn] = useState<string>();
  const failed = params.get("login") === "falhou";

  useEffect(() => {
    if (!failed) return;
    const rest = new URLSearchParams(params);
    rest.delete("login");
    setParams(rest, { replace: true });
  }, [failed, params, setParams]);

  // Guardado durante o render (não num efeito): o aviso continua depois que o parâmetro sai da URL.
  if (failed && shownOn !== pathname) setShownOn(pathname);

  if (shownOn !== pathname) return null;
  return (
    <p role="alert" className={styles.aviso}>
      Não foi possível entrar com o Google. <a href="/oauth2/authorization/google">Tentar de novo</a>
    </p>
  );
}
