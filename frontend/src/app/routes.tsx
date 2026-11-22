import type { RouteObject } from "react-router";
import { AdminCatalogPage } from "../features/admin-catalog/AdminCatalogPage";
import { BuildResultPage } from "../features/build-result/BuildResultPage";
import { CreateBuildPage } from "../features/create-build/CreateBuildPage";
import { GeneratingPage } from "../features/generating/GeneratingPage";
import { GuidePage } from "../features/guide/GuidePage";
import { HomePage } from "../features/home/HomePage";
import { Layout } from "./Layout";
import { MyBuildsPage } from "../features/my-builds/MyBuildsPage";
import { NotFoundPage } from "./NotFoundPage";

/** Rotas do SPA. O backend encaminha cada uma delas ao index.html (SpaForwardController). */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "guia", element: <GuidePage /> },
      { path: "criar", element: <CreateBuildPage /> },
      { path: "gerando", element: <GeneratingPage /> },
      { path: "builds/:id", element: <BuildResultPage /> },
      { path: "admin", element: <AdminCatalogPage /> },
      { path: "minhas-builds", element: <MyBuildsPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];
