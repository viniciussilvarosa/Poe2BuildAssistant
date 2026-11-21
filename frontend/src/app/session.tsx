import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { getMe, type Me } from "../api/me";

/**
 * Quem está logado, lido uma vez do servidor. undefined = ainda carregando; null = visitante.
 * Nada vai para localStorage: a sessão é do servidor (ADR-001).
 */
interface Session {
  me: Me | null | undefined;
  setMe: (me: Me | null) => void;
}

const SessionContext = createContext<Session>({ me: null, setMe: () => {} });

export function SessionProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>();
  useEffect(() => {
    getMe().then(setMe);
  }, []);
  return <SessionContext value={{ me, setMe }}>{children}</SessionContext>;
}

export const useSession = () => useContext(SessionContext);
