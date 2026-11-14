///
package niko_ku.vtab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;

@Mod(VTab.MODID)
public class VTab {
    public static final String MODID = "vtab";
    private static final Logger LOGGER = LogUtils.getLogger();

    // ukryci gracze: UUID -> ostatni znany nick (zapisywane w folderze swiata)
    private static final Map<UUID, String> HIDDEN = new ConcurrentHashMap<>();

    public VTab() {
        NeoForge.EVENT_BUS.addListener(VTab::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(VTab::onServerStarting);
        NeoForge.EVENT_BUS.addListener(VTab::onPlayerLoggedIn);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("vtab")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> toggleSelf(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> toggleOther(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int toggleSelf(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean hide = toggle(source.getServer(), player);
        source.sendSuccess(() -> Lang.tr(source, hide ? "self_hidden" : "self_shown"), false);
        return 1;
    }

    private static int toggleOther(CommandSourceStack source, ServerPlayer target) {
        boolean hide = toggle(source.getServer(), target);
        String name = target.getName().getString();
        source.sendSuccess(() -> Lang.tr(source, hide ? "other_hidden" : "other_shown", name), false);
        return 1;
    }

    // przelacza stan, wysyla falszywy komunikat i pakiet tablisty; true = teraz ukryty
    private static boolean toggle(MinecraftServer server, ServerPlayer target) {
        UUID id = target.getUUID();
        boolean hide = !HIDDEN.containsKey(id);
        if (hide) {
            // falszywy komunikat MUSI isc przed dodaniem do HIDDEN, inaczej mixin sam by go wyciol
            announce(server, "multiplayer.player.left", target);
            HIDDEN.put(id, target.getName().getString());
        } else {
            HIDDEN.remove(id);
            announce(server, "multiplayer.player.joined", target);
        }
        save(server);
        server.getPlayerList().broadcastAll(listedPacket(server, id, !hide));
        return hide;
    }

    // zolty komunikat jak w vanilli; klient sam tlumaczy klucz na swoj jezyk
    private static void announce(MinecraftServer server, String key, ServerPlayer player) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(key, player.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
    }

    // wolane z mixina: true = to vanillowy komunikat wejscia/wyjscia ukrytego gracza
    public static boolean shouldSuppress(Component message) {
        if (HIDDEN.isEmpty()) {
            return false;
        }
        if (!(message.getContents() instanceof TranslatableContents tc)) {
            return false;
        }
        String key = tc.getKey();
        if (!key.equals("multiplayer.player.joined")
                && !key.equals("multiplayer.player.joined.renamed")
                && !key.equals("multiplayer.player.left")) {
            return false;
        }
        Object[] args = tc.getArgs();
        if (args.length == 0) {
            return false;
        }
        String shown = args[0] instanceof Component c ? c.getString() : String.valueOf(args[0]);
        return HIDDEN.containsValue(shown);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("vtab_hidden.txt");
    }

    private static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        HIDDEN.clear();
        Path file = file(server);
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.trim().split(" ", 2);
                if (parts.length == 2) {
                    try {
                        HIDDEN.put(UUID.fromString(parts[0]), parts[1]);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("VTab: pomijam zla linie w {}: {}", file, line);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("VTab: nie udalo sie wczytac {}", file, e);
        }
    }

    private static void save(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        HIDDEN.forEach((id, name) -> lines.add(id + " " + name));
        try {
            Files.write(file(server), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("VTab: nie udalo sie zapisac {}", file(server), e);
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined)) {
            return;
        }
        MinecraftServer server = joined.level().getServer();
        if (server == null) {
            return;
        }
        // nowy gracz dowiaduje sie, kto jest ukryty
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (HIDDEN.containsKey(other.getUUID())) {
                joined.connection.send(listedPacket(server, other.getUUID(), false));
            }
        }
        // ukryty gracz wrocil: ukryj go znowu i odswiez zapisany nick
        if (HIDDEN.containsKey(joined.getUUID())) {
            String name = joined.getName().getString();
            if (!name.equals(HIDDEN.get(joined.getUUID()))) {
                HIDDEN.put(joined.getUUID(), name);
                save(server);
            }
            server.getPlayerList().broadcastAll(listedPacket(server, joined.getUUID(), false));
        }
    }

    // pakiet zmieniajacy TYLKO flage "listed" (widocznosc na tablist)
    private static ClientboundPlayerInfoUpdatePacket listedPacket(MinecraftServer server, UUID id, boolean listed) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), server.registryAccess(), ConnectionType.OTHER);
        try {
            buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                    ClientboundPlayerInfoUpdatePacket.Action.class);
            buf.writeVarInt(1);
            buf.writeUUID(id);
            buf.writeBoolean(listed);
            return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }
}