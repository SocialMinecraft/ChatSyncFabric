package club.somc.chatsyncfabric;

import club.somc.protos.MinecraftPlayerDied;
import club.somc.protos.MinecraftPlayerJoined;
import club.somc.protos.MinecraftPlayerQuit;
import io.nats.client.Connection;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;


public class PlayerEventListener {
    public static void register(Connection natsConnection, String serverName) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            MinecraftPlayerJoined msg = MinecraftPlayerJoined.newBuilder()
                    .setServerName(serverName)
                    .setPlayerUuid(player.getUuid().toString())
                    .setPlayerName(player.getName().getString())
                    .build();

            natsConnection.publish("minecraft.player.joined", msg.toByteArray());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            MinecraftPlayerQuit msg = MinecraftPlayerQuit.newBuilder()
                    .setServerName(serverName)
                    .setPlayerUuid(player.getUuid().toString())
                    .setPlayerName(player.getName().getString())
                    .build();

            natsConnection.publish("minecraft.player.quit", msg.toByteArray());
        });

        ServerEntityCombatEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                MinecraftPlayerDied msg = MinecraftPlayerDied.newBuilder()
                        .setServerName(serverName)
                        .setPlayerUuid(player.getUuid().toString())
                        .setPlayerName(player.getName().getString())
                        .setDeathMessage(damageSource.getDeathMessage(player).getString())
                        .setWorld(player.getWorld().getRegistryKey().getValue().toString())
                        .setX((long) player.getX())
                        .setY((long) player.getY())
                        .setZ((long) player.getZ())
                        .build();

                natsConnection.publish("minecraft.player.died", msg.toByteArray());
            }
        });
    }
}
