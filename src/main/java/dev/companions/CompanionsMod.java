package dev.companions;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import dev.companions.command.GeneralCommands;
import dev.companions.player.FakePlayerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.IdentityHashMap;
import java.util.Map;

public final class CompanionsMod implements ModInitializer {
    private static final Map<MinecraftServer,FakePlayerManager> MANAGERS=new IdentityHashMap<>();
    public static FakePlayerManager manager(MinecraftServer server) { return MANAGERS.computeIfAbsent(server,FakePlayerManager::new); }
    @Override public void onInitialize() {
        dev.companions.selection.GuardWandEvents.register();
        dev.companions.selection.FlyingTargetReceiver.register();
        ServerTickEvents.START_SERVER_TICK.register(s -> manager(s).tick());
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount) -> {
            if(source.getEntity() instanceof dev.companions.player.CompanionPlayer p) {
                for(var c:manager(p.level().getServer()).all())if(c.player==p)return c.combat.allowed(entity);
            }
            return true;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { var m=MANAGERS.remove(s); if(m!=null) m.close(); });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root=Commands.literal("bot").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("Deterministic Companions: Minecraft 26.2"), false);
                return 1;
            });
            GeneralCommands.register(root);
            dev.companions.command.MovementCommands.register(root);
            dev.companions.command.WorkCommands.register(root);
            dev.companions.command.InventoryCommands.register(root);
            dev.companions.command.CombatCommands.register(root);
            dev.companions.command.ConfigCommands.register(root);
            dev.companions.command.AreaGuardCommands.register(root);
            dev.companions.command.HealthCommands.register(root);
            dev.companions.command.SchematicCommands.register(root);
            dev.companions.command.AllCommands.register(root);
            dispatcher.register(root);
            dev.companions.ui.BotUiCommands.register(dispatcher);
        });
    }
}
