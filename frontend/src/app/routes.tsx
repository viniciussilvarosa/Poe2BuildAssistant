import type { RouteObject } from "react-router";
import { AdminCatalogPage } from "../features/admin-catalog/AdminCatalogPage";
import { BuildResultPage } from "../features/build-result/BuildResultPage";
import { CreateBuildPage } from "../features/create-build/CreateBuildPage";
import { GeneratingPage } from "../features/generating/GeneratingPage";
import { HomePage } from "../features/home/HomePage";
import { Layout } from "./Layout";
import { NotFoundPage } from "./NotFoundPage";

/**
 * Rotas do SPA. Reservadas para os próximos cards (o backend já as encaminha ao index.html):
 * /minhas-builds (#15).
 */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "criar", element: <CreateBuildPage /> },
      { path: "gerando", element: <GeneratingPage /> },
      { path: "builds/:id", element: <BuildResultPage /> },
      { path: "admin", element: <AdminCatalogPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];
