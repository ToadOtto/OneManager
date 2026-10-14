package com.onemanager.profession;

import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory registry of available professions with numeric IDs.
 * Supports adding, updating, or modifying professions at runtime or in future updates.
 */
public class ProfessionRegistry {
    private static final Map<Integer, Profession> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Profession> BY_TITLE = new LinkedHashMap<>();
    private static final Map<String, Profession> ALIASES = new LinkedHashMap<>();

    static {
        // --- 3 Основные профессии (2 места на профессию) ---
        register(new Profession(
                1,
                "Фермер-скотовод",
                ProfessionCategory.MAIN,
                Items.HAY_BLOCK,
                "Разведение домашних животных, обработка пашни, селекция зерновых культур и заготовка провианта для общины. Обеспечивает поселение ценными ресурсами и сырьем."
        ), "farmer_breeder");

        register(new Profession(
                2,
                "Шеф-повар",
                ProfessionCategory.MAIN,
                Items.COOKED_BEEF,
                "Мастерство кулинарного искусства, приготовление питательных блюд, организация пиров и снабжение жителей изысканной едой, дающей силу и выносливость."
        ), "chef");

        register(new Profession(
                3,
                "Охотник-авантюрист",
                ProfessionCategory.MAIN,
                Items.BOW,
                "Исследование неизведанных земель, выслеживание диких зверей, защита общины от монстров и добыча редких трофеев из глубоких подземелий."
        ), "hunter_adventurer");

        // --- Государственные должности (1 место на должность) ---
        register(new Profession(
                4,
                "Казначей",
                ProfessionCategory.STATE,
                com.oneshop.item.ModItems.SPROTA,
                "Ведение учёта государственной казны, сбор пошлин, регулирование торговых соглашений и финансовый контроль за оборотом Государственных Марок."
        ), "treasurer");

        // --- Дополнительные профессии (1 место на профессию) ---

        register(new Profession(
                5,
                "Флорист",
                ProfessionCategory.SECONDARY,
                Items.POPPY,
                "Выращивание редких декоративных растений, благоустройство садов и аллей, ландшафтный дизайн поселения и производство ярких природных красителей."
        ), "florist");

        register(new Profession(
                6,
                "Зельевар",
                ProfessionCategory.SECONDARY,
                Items.BREWING_STAND,
                "Алхимические эксперименты, дистилляция зелий, экстракция целебных настоев и создание могущественных эликсиров для жителей поселения."
        ), "alchemist");
    }

    public static synchronized void register(Profession profession, String... aliases) {
        if (profession != null) {
            BY_ID.put(profession.getId(), profession);
            BY_TITLE.put(profession.getTitle().toLowerCase(), profession);
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias != null && !alias.trim().isEmpty()) {
                        ALIASES.put(alias.trim().toLowerCase(), profession);
                    }
                }
            }
        }
    }

    public static Collection<Profession> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static Profession getById(int id) {
        return BY_ID.get(id);
    }

    public static Profession getById(String idStr) {
        if (idStr == null) return null;
        try {
            String trimmed = idStr.trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("№")) {
                trimmed = trimmed.substring(1).trim();
            }
            int id = Integer.parseInt(trimmed);
            return BY_ID.get(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Profession getByTitle(String title) {
        if (title == null) return null;
        return BY_TITLE.get(title.toLowerCase());
    }

    public static Profession getByIdOrTitle(String key) {
        if (key == null) return null;
        String trimmed = key.trim();
        String numKey = (trimmed.startsWith("#") || trimmed.startsWith("№")) ? trimmed.substring(1).trim() : trimmed;
        try {
            int id = Integer.parseInt(numKey);
            Profession p = BY_ID.get(id);
            if (p != null) return p;
        } catch (NumberFormatException ignored) {
        }

        Profession p = BY_TITLE.get(trimmed.toLowerCase());
        if (p != null) return p;
        return ALIASES.get(trimmed.toLowerCase());
    }
}
