import type { RouteObject } from "react-router";
import { HomePage } from "../features/home/HomePage";
import { Layout } from "./Layout";
import { NotFoundPage } from "./NotFoundPage";

/**
 * Rotas do SPA. Reservadas para os próximos cards (o backend já as encaminha ao index.html):
 * /criar (#12), /gerando (#12), /builds/:id (#13), /minhas-builds (#15), /admin (#18).
 */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];
