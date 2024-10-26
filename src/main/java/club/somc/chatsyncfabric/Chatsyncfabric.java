package club.somc.chatsyncfabric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.InvalidProtocolBufferException;
import club.somc.protos.minecraft.MessageSent;
import club.somc.protos.minecraft.PlayerDied;
import club.somc.protos.minecraft.PlayerJoined;
import club.somc.protos.minecraft.PlayerQuit;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Chatsyncfabric implements ModInitializer {

    private static MinecraftServer SERVER;
    private Connection natsConnection;
    private String serverName;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chatsync.json");

    @Override
    public void onInitialize() {
        loadConfig();

        try {
            this.natsConnection = Nats.connect(getConfigValue("natsUrl", "nats://localhost:4222"));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to connect to NATS", e);
        }

        // Register event handlers
        MessageListener.register(natsConnection, serverName);
        PlayerEventListener.register(natsConnection, serverName);

        // Set up NATS message dispatcher
        setupNatsDispatcher();


        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SERVER = server;
        });

        // Handle server shutdown
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            try {
                if (natsConnection != null) {
                    natsConnection.drain(Duration.ofSeconds(5));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupNatsDispatcher() {
        Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                switch (msg.getSubject()) {
                    case "minecraft.chat.message_sent" -> {
                        MessageSent event = MessageSent.parseFrom(msg.getData());
                        if (!event.getServerName().equals(serverName)) {
                            broadcastMessage(Text.literal("<" + event.getPlayerName() + "> " + event.getMessage()));
                        }
                    }
                    case "minecraft.player.joined" -> {
                        PlayerJoined event = PlayerJoined.parseFrom(msg.getData());
                        if (!event.getServerName().equals(serverName)) {
                            broadcastMessage(Text.literal(event.getPlayerName() + " joined server " + event.getServerName())
                                    .formatted(Formatting.YELLOW));
                        }
                    }
                    case "minecraft.player.quit" -> {
                        PlayerQuit event = PlayerQuit.parseFrom(msg.getData());
                        if (!event.getServerName().equals(serverName)) {
                            broadcastMessage(Text.literal(event.getPlayerName() + " left server " + event.getServerName())
                                    .formatted(Formatting.YELLOW));
                        }
                    }
                    case "minecraft.player.died" -> {
                        PlayerDied event = PlayerDied.parseFrom(msg.getData());
                        if (!event.getServerName().equals(serverName)) {
                            broadcastMessage(Text.literal(event.getDeathMessage()).formatted(Formatting.RED));
                        }
                    }
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        });

        dispatcher.subscribe("minecraft.chat.*");
        dispatcher.subscribe("minecraft.player.joined");
        dispatcher.subscribe("minecraft.player.quit");
        dispatcher.subscribe("minecraft.player.died");
    }

    private void broadcastMessage(Text message) {
        if (SERVER != null) {
            SERVER.execute(() ->
                    SERVER.getPlayerManager().broadcast(message, false));
        }
    }

    private void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Map<String, String> defaultConfig = new HashMap<>();
                defaultConfig.put("serverName", "fabric-server");
                defaultConfig.put("natsUrl", "nats://localhost:4222");
                Files.writeString(CONFIG_PATH, new Gson().toJson(defaultConfig));
            }

            String configContent = Files.readString(CONFIG_PATH);
            JsonObject config = new Gson().fromJson(configContent, JsonObject.class);
            this.serverName = config.get("serverName").getAsString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    private String getConfigValue(String key, String defaultValue) {
        try {
            String configContent = Files.readString(CONFIG_PATH);
            JsonObject config = new Gson().fromJson(configContent, JsonObject.class);
            return config.get(key).getAsString();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
