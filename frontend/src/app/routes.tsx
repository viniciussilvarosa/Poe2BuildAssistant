import type { RouteObject } from "react-router";
import { Layout } from "./Layout";
import { NotFoundPage } from "./NotFoundPage";

/**
 * Rotas do SPA. A tela inicial entra no PR seguinte da #2. Reservadas para os próximos cards (o
 * backend já as encaminha ao index.html): /criar (#12), /gerando (#12), /builds/:id (#13),
 * /minhas-builds (#15), /admin (#18).
 */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [{ path: "*", element: <NotFoundPage /> }],
  },
];
