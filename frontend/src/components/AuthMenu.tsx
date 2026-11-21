import { useState } from "react";
import { logout } from "../api/me";
import { useSession } from "../app/session";

export const LOGIN_URL = "/oauth2/authorization/google";

/** Fim do menu: "Entrar com Google" para visitante; nome e "Sair" para quem entrou. */
export function AuthMenu({ className }: { className?: string }) {
  const { me, setMe } = useSession();
  const [failed, setFailed] = useState(false);
  if (me === undefined) return null;
  if (me === null) {
    return (
      <a href={LOGIN_URL} className={className}>
        Entrar com Google
      </a>
    );
  }

  // Só vira visitante depois que o servidor confirma: com a sessão ainda viva, mostrar "Entrar"
  // faria alguém sair de um computador compartilhado ainda logado (chaos M1).
  const sair = () => {
    setFailed(false);
    logout().then(() => setMe(null), () => setFailed(true));
  };

  return (
    <span className={className}>
      <span>{me.name}</span>
      <button type="button" onClick={sair}>
        Sair
      </button>
      {failed && <span role="alert">Não foi possível sair. Tente de novo.</span>}
    </span>
  );
}
