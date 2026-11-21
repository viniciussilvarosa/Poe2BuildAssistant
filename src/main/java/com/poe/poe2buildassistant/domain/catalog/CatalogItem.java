package com.poe.poe2buildassistant.domain.catalog;

import java.util.List;
import java.util.Objects;

/**
 * Item que o admin cadastra ou edita num rascunho do catálogo. Notables não entram aqui: vêm só do
 * import da árvore oficial.
 *
 * @param characterClass só para ascendência: a classe dona
 * @param weaponTypes só para gema de habilidade: armas exigidas (vazio = qualquer arma)
 */
public record CatalogItem(Type type, String name, String characterClass, List<String> weaponTypes) {

    public enum Type {
        CLASS,
        ASCENDANCY,
        WEAPON_TYPE,
        SKILL_GEM,
        SUPPORT_GEM,
        PLAYSTYLE
    }

    public CatalogItem {
        Objects.requireNonNull(type, "item sem tipo");
        name = CatalogNames.requireName(name, "item do catálogo");
        if (name.length() > CatalogNames.MAX_LENGTH) {
            throw new IllegalArgumentException("O nome " + name.substring(0, 40) + "… passa de " + CatalogNames.MAX_LENGTH + " caracteres");
        }
        if (type == Type.ASCENDANCY) {
            characterClass = CatalogNames.requireName(characterClass, "classe da ascendência " + name);
        } else {
            characterClass = null;
        }
        weaponTypes = type == Type.SKILL_GEM && weaponTypes != null ? List.copyOf(weaponTypes) : List.of();
    }

    public static CatalogItem characterClass(String name) {
        return new CatalogItem(Type.CLASS, name, null, null);
    }

    public static CatalogItem ascendancy(String name, String characterClass) {
        return new CatalogItem(Type.ASCENDANCY, name, characterClass, null);
    }

    public static CatalogItem weaponType(String name) {
        return new CatalogItem(Type.WEAPON_TYPE, name, null, null);
    }

    public static CatalogItem skillGem(String name, List<String> weaponTypes) {
        return new CatalogItem(Type.SKILL_GEM, name, null, weaponTypes);
    }

    public static CatalogItem supportGem(String name) {
        return new CatalogItem(Type.SUPPORT_GEM, name, null, null);
    }

    public static CatalogItem playstyle(String name) {
        return new CatalogItem(Type.PLAYSTYLE, name, null, null);
    }
}
