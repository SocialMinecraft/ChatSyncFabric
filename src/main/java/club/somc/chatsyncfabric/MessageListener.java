package club.somc.chatsyncfabric;

import club.somc.protos.MinecraftMessageSent;
import io.nats.client.Connection;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class MessageListener {
    public static void register(Connection natsConnection, String serverName) {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            MinecraftMessageSent msg = MinecraftMessageSent.newBuilder()
                    .setServerName(serverName)
                    .setPlayerUuid(sender.getUuid().toString())
                    .setPlayerName(sender.getName().getString())
                    .setMessage(message.getContent().getString())
                    .build();

            natsConnection.publish("minecraft.chat.message_sent", msg.toByteArray());
        });
    }
}
