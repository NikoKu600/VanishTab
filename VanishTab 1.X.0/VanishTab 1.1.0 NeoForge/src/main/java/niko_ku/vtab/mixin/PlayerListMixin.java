package niko_ku.vtab.mixin;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import niko_ku.vtab.VTab;

@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void vtab$hideShort(Component message, boolean overlay, CallbackInfo ci) {
        if (VTab.shouldSuppress(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void vtab$hideLong(Component message, Function<ServerPlayer, Component> perPlayer,
                               boolean overlay, CallbackInfo ci) {
        if (VTab.shouldSuppress(message)) {
            ci.cancel();
        }
    }
}