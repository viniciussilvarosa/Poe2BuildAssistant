import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router";
import * as admin from "../../api/adminCatalog";
import type { CatalogVersionInfo, DraftCatalog, ItemType } from "../../api/adminCatalog";
import { getCatalogOptions } from "../../api/catalog";
import { getMe, type Me } from "../../api/me";
import moldura from "../../ui/Moldura.module.css";
import styles from "./AdminCatalogPage.module.css";

const TABS: { type: ItemType; label: string }[] = [
  { type: "classes", label: "Classes" },
  { type: "ascendancies", label: "Ascendências" },
  { type: "weapon-types", label: "Armas" },
  { type: "skill-gems", label: "Gemas" },
  { type: "support-gems", label: "Suportes" },
  { type: "playstyles", label: "Estilos" },
];

interface Row {
  name: string;
  detail?: string;
  characterClass?: string;
  weaponTypes?: string[];
}

function rowsOf(draft: DraftCatalog, type: ItemType): Row[] {
  switch (type) {
    case "classes":
      return draft.classes.map((c) => ({ name: c.name, detail: c.ascendancies.join(", ") }));
    case "ascendancies":
      return draft.classes.flatMap((c) => c.ascendancies.map((a) => ({ name: a, detail: c.name, characterClass: c.name })));
    case "skill-gems":
      return draft.skillGems.map((g) => ({ name: g.name, detail: g.weaponTypes.join(", "), weaponTypes: g.weaponTypes }));
    case "weapon-types":
      return draft.weaponTypes.map((name) => ({ name }));
    case "support-gems":
      return draft.supportGems.map((name) => ({ name }));
    case "playstyles":
      return draft.playstyles.map((name) => ({ name }));
  }
}

const message = (e: unknown) => (e as { detail?: string }).detail ?? "Não foi possível concluir a ação.";

/** Esconder a tela é só conforto: quem protege de verdade é o ROLE_ADMIN na API. */
export function AdminCatalogPage() {
  const [me, setMe] = useState<Me | null>();
  useEffect(() => {
    getMe().then(setMe);
  }, []);

  if (me === undefined) return <p className={styles.pagina}>Carregando…</p>;
  if (me?.role !== "ADMIN") {
    return (
      <section className={styles.pagina}>
        <h1>Acesso restrito</h1>
        <p className={styles.apoio}>Esta área é só para quem cuida do catálogo do jogo.</p>
        <Link to="/">Voltar ao início</Link>
      </section>
    );
  }
  return <CatalogAdmin />;
}

function CatalogAdmin() {
  const [published, setPublished] = useState<string>();
  const [draftInfo, setDraftInfo] = useState<CatalogVersionInfo | null>();
  const [draft, setDraft] = useState<DraftCatalog>();
  const [error, setError] = useState<string>();
  const [notice, setNotice] = useState("");
  const [patch, setPatch] = useState("");
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    getCatalogOptions().then((o) => setPublished(o.patch), () => setPublished("nenhuma"));
    admin.getCurrentDraft().then(setDraftInfo, (e) => setError(message(e)));
  }, []);

  useEffect(() => {
    if (draftInfo) admin.getDraft(draftInfo.id).then(setDraft, (e) => setError(message(e)));
  }, [draftInfo]);

  async function act(action: () => Promise<void>) {
    setError(undefined);
    try {
      await action();
    } catch (e) {
      setError(message(e));
    }
  }

  const create = (e: FormEvent) => {
    e.preventDefault();
    void act(async () => setDraftInfo(await admin.createDraft(patch.trim())));
  };
  const reload = async () => draftInfo && setDraft(await admin.getDraft(draftInfo.id));

  return (
    <section className={styles.pagina}>
      <h1>Catálogo do jogo</h1>
      <p className={styles.apoio}>Versão publicada: {published ?? "…"}</p>
      {error && (
        <p role="alert" className={styles.aviso}>
          {error}
        </p>
      )}
      <p role="status" className={styles.aviso} hidden={!notice}>
        {notice}
      </p>

      {draftInfo === null && (
        <form className={styles.linha} onSubmit={create}>
          <label htmlFor="patch">Patch do rascunho</label>
          <input id="patch" value={patch} onChange={(e) => setPatch(e.target.value)} placeholder="Ex.: 1.0" />
          <button type="submit" className={moldura.acaoPrincipal}>
            Criar rascunho
          </button>
        </form>
      )}

      {draftInfo && draft && (
        <>
          <div className={styles.linha}>
            <p>
              Rascunho <strong>{draftInfo.patch}</strong>, com {draft.notables} notables.
            </p>
            <button type="button" onClick={() => act(async () => {
              const { notables } = await admin.importPassiveTree(draftInfo.id);
              setNotice(`${notables} notables importados da árvore oficial.`);
              await reload();
            })}>
              Importar árvore de passivas
            </button>
            <button type="button" className={moldura.acaoPrincipal} onClick={() => setConfirming(true)}>
              Publicar {draftInfo.patch}
            </button>
          </div>
          {confirming && (
            <div className={`${moldura.moldura} ${styles.confirmar}`}>
              <p>
                Publicar a versão {draftInfo.patch}? Ela passa a valer na hora para os seletores e o validador, e a{" "}
                {published} é arquivada. Não dá para desfazer.
              </p>
              <div className={styles.linha}>
                <button type="button" className={moldura.acaoPrincipal} onClick={() => act(async () => {
                  const version = await admin.publishDraft(draftInfo.id);
                  setConfirming(false);
                  setNotice(`Versão ${version.patch} publicada.`);
                  setPublished(version.patch);
                  setDraftInfo(null);
                  setDraft(undefined);
                })}>
                  Confirmar publicação
                </button>
                <button type="button" onClick={() => setConfirming(false)}>
                  Cancelar
                </button>
              </div>
            </div>
          )}
          <ItemTabs draft={draft} onChange={(action) => act(async () => {
            await action();
            await reload();
          })} />
        </>
      )}
    </section>
  );
}

function ItemTabs({ draft, onChange }: { draft: DraftCatalog; onChange: (action: () => Promise<void>) => void }) {
  const [tab, setTab] = useState<ItemType>("classes");
  const [search, setSearch] = useState("");
  const [form, setForm] = useState<Row>({ name: "" });
  const [fieldError, setFieldError] = useState<string>();
  const id = draft.version.id;
  const rows = rowsOf(draft, tab).filter((r) => r.name.toLowerCase().includes(search.trim().toLowerCase()));

  function select(type: ItemType) {
    setTab(type);
    setSearch("");
    setForm({ name: "" });
    setFieldError(undefined);
  }

  function save(e: FormEvent) {
    e.preventDefault();
    if (!form.name.trim()) return setFieldError("Dê um nome ao item.");
    if (tab === "ascendancies" && !form.characterClass) return setFieldError("Escolha a classe da ascendência.");
    setFieldError(undefined);
    const body =
      tab === "ascendancies"
        ? { name: form.name.trim(), characterClass: form.characterClass }
        : tab === "skill-gems"
          ? { name: form.name.trim(), weaponTypes: form.weaponTypes ?? [] }
          : { name: form.name.trim() };
    onChange(async () => {
      await admin.saveItem(id, tab, body);
      setForm({ name: "" });
    });
  }

  const toggleWeapon = (weapon: string) => {
    const current = form.weaponTypes ?? [];
    setForm({ ...form, weaponTypes: current.includes(weapon) ? current.filter((w) => w !== weapon) : [...current, weapon] });
  };

  return (
    <div className={styles.abas}>
      <div role="tablist" aria-label="Tipos de item">
        {TABS.map((t) => (
          <button key={t.type} type="button" role="tab" aria-selected={tab === t.type} onClick={() => select(t.type)}>
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" aria-label={TABS.find((t) => t.type === tab)?.label} className={moldura.moldura}>
        <form className={styles.formulario} onSubmit={save} noValidate>
          <label htmlFor="item-nome">Nome</label>
          <input id="item-nome" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
            aria-invalid={!!fieldError} aria-describedby={fieldError ? "item-erro" : undefined} />
          {tab === "ascendancies" && (
            <>
              <label htmlFor="item-classe">Classe</label>
              <select id="item-classe" value={form.characterClass ?? ""}
                onChange={(e) => setForm({ ...form, characterClass: e.target.value || undefined })}>
                <option value="">Escolha a classe</option>
                {draft.classes.map((c) => (
                  <option key={c.name}>{c.name}</option>
                ))}
              </select>
            </>
          )}
          {tab === "skill-gems" && (
            <fieldset className={styles.armas}>
              <legend>Armas exigidas (nenhuma = qualquer arma)</legend>
              {draft.weaponTypes.map((w) => (
                <label key={w}>
                  <input type="checkbox" checked={form.weaponTypes?.includes(w) ?? false} onChange={() => toggleWeapon(w)} />
                  {w}
                </label>
              ))}
            </fieldset>
          )}
          {fieldError && (
            <p id="item-erro" className={styles.erro}>
              {fieldError}
            </p>
          )}
          <button type="submit">Salvar</button>
        </form>

        <div className={moldura.separador} aria-hidden="true" />

        <label htmlFor="busca">Buscar</label>
        <input id="busca" type="search" value={search} onChange={(e) => setSearch(e.target.value)} />
        <ul className={styles.lista}>
          {rows.map((r) => (
            <li key={r.name}>
              <span>{r.name}</span>
              <span className={styles.detalhe}>{r.detail}</span>
              <button type="button" onClick={() => setForm(r)}>
                Editar
              </button>
              <button type="button" aria-label={`Remover ${r.name}`} onClick={() => onChange(() => admin.removeItem(id, tab, r.name))}>
                Remover
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
