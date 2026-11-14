///
package niko_ku.vtab;

import java.util.Locale;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Lang {
    private static final String DEFAULT = "en";

    private static final Map<String, Map<String, String>> TEXTS = Map.of(
            "self_hidden", Map.of(
                    "en", "You are now hidden from the tab list.",
                    "pl", "Ukryto cię na liście graczy (tab).",
                    "de", "Du bist jetzt in der Tabliste versteckt."),
            "self_shown", Map.of(
                    "en", "You are visible on the tab list again.",
                    "pl", "Znowu jesteś widoczny na liście graczy (tab).",
                    "de", "Du bist wieder in der Tabliste sichtbar."),
            "other_hidden", Map.of(
                    "en", "%s is now hidden from the tab list.",
                    "pl", "Ukryto gracza %s na liście graczy (tab).",
                    "de", "%s ist jetzt in der Tabliste versteckt."),
            "other_shown", Map.of(
                    "en", "%s is visible on the tab list again.",
                    "pl", "Gracz %s jest znowu widoczny na liście graczy (tab).",
                    "de", "%s ist wieder in der Tabliste sichtbar."));

    private Lang() {
    }

    public static Component tr(CommandSourceStack source, String key, Object... args) {
        String lang = DEFAULT;
        if (source.getEntity() instanceof ServerPlayer player) {
            lang = player.clientInformation().language();
        }
        return Component.literal(String.format(Locale.ROOT, pick(key, lang), args));
    }

    static String pick(String key, String langCode) {
        Map<String, String> byLang = TEXTS.get(key);
        String prefix = langCode.toLowerCase(Locale.ROOT).split("_")[0];
        String text = byLang.get(prefix);
        return text != null ? text : byLang.get(DEFAULT);
    }
}
