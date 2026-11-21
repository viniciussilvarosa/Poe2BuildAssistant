import { logout } from "../api/me";
import { useSession } from "../app/session";

export const LOGIN_URL = "/oauth2/authorization/google";

/** Fim do menu: "Entrar com Google" para visitante; nome e "Sair" para quem entrou. */
export function AuthMenu({ className }: { className?: string }) {
  const { me, setMe } = useSession();
  if (me === undefined) return null;
  if (me === null) {
    return (
      <a href={LOGIN_URL} className={className}>
        Entrar com Google
      </a>
    );
  }
  return (
    <span className={className}>
      <span>{me.name}</span>
      <button type="button" onClick={() => logout().finally(() => setMe(null))}>
        Sair
      </button>
    </span>
  );
}
