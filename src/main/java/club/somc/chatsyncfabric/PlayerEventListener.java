package club.somc.chatsyncfabric;

import club.somc.protos.minecraft.PlayerDied;
import club.somc.protos.minecraft.PlayerJoined;
import club.somc.protos.minecraft.PlayerQuit;
import io.nats.client.Connection;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;


public class PlayerEventListener {
    public static void register(Connection natsConnection, String serverName) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerJoined msg = PlayerJoined.newBuilder()
                    .setServerName(serverName)
                    .setPlayerUuid(player.getUuid().toString())
                    .setPlayerName(player.getName().getString())
                    .build();

            natsConnection.publish("minecraft.player.joined", msg.toByteArray());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerQuit msg = PlayerQuit.newBuilder()
                    .setServerName(serverName)
                    .setPlayerUuid(player.getUuid().toString())
                    .setPlayerName(player.getName().getString())
                    .build();

            natsConnection.publish("minecraft.player.quit", msg.toByteArray());
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                PlayerDied msg = PlayerDied.newBuilder()
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
