package mc.iaiao.interactivedonations;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.socket.client.IO;
import io.socket.client.Socket;
// Сюда вставь эти названия (пакет net.minecraft.что-то),
// потому что я не имею на них прав
// и они могут измениться со временем.
import TextComponent;
import DedicatedServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class InteractiveDonations {
    public static Socket socket = null;
    private static Logger LOGGER = LogManager.getLogger();
    private static List<Item> items = new ArrayList<>();
    private static String message;
    private static DedicatedServer server;

    private static class Item {
        private String cmd;
        private double rub;
        private double usd;

        Item(String cmd, double rub, double usd) {
            this.cmd = cmd;
            this.rub = rub;
            this.usd = usd;
        }
    }

    public static void connect(DedicatedServer srv) {
        server = srv;
        try {
            if (!new File("config.json").exists()) {
                new File("config.json").createNewFile();
                FileWriter writer = new FileWriter("config.json");
                writer.write("" +
                        "{\n" +
                        "   \"token\": \"ОН ЗДЕСЬ --> https://www.donationalerts.com/dashboard/general\",\n" +
                        "   \"message\": \"Спасибо &6%name%&f за &6&l%amount% %currency%&f! &e%message%\",\n" +
                        "   \"items\": [\n" +
                        "       {\n" +
                        "           \"cmd\": \"execute at Streamer run summon Creeper\",\n" +
                        "           \"rub\": 30,\n" +
                        "           \"usd\": 0.5\n" +
                        "       },\n" +
                        "       {\n" +
                        "           \"cmd\": \"execute at Streamer run fill ~-1 ~ ~-1 ~1 ~-2 ~1 minecraft:lava\",\n" +
                        "           \"rub\": 100,\n" +
                        "           \"usd\": 1\n" +
                        "       },\n" +
                        "   ]\n" +
                        "}");
                writer.close();
            }
            BufferedReader reader = new BufferedReader(new FileReader("config.json"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonObject config = new JsonParser().parse(sb.toString()).getAsJsonObject();
            message = config.get("message").getAsString();
            for (JsonElement e : config.get("items").getAsJsonArray()) {
                JsonObject item = e.getAsJsonObject();
                items.add(new Item(
                        item.get("cmd").getAsString(),
                        item.has("rub") ? item.get("rub").getAsDouble() : -1,
                        item.has("usd") ? item.get("usd").getAsDouble() : -1
                ));
            }
            socket = IO.socket("https://socket.donationalerts.ru:443");
            socket.on(Socket.EVENT_CONNECT, args -> {
                LOGGER.info("[WS] Connected");
                try {
                    socket.emit("add-user", new JSONObject().put("token", config.get("token").getAsString()).put("type", "alert_widget"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }).on("donation", args -> {
                try {
                    JSONObject obj = new JSONObject((String) args[0]);
                    execute(obj.getDouble("amount_main"), obj.getString("currency"), obj.getString("username"), obj.getString("message"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }).on(Socket.EVENT_DISCONNECT, args -> LOGGER.info("[WS] Disconnected"))
                    .on(Socket.EVENT_ERROR, args -> {
                        System.out.println("ERROR:");
                        System.out.println(args);
                    })
                    .on(Socket.EVENT_CONNECT_ERROR, args -> {
                        System.out.println("ERROR:");
                        System.out.println(args[0]);
                    });
            LOGGER.info("[WS] Connecting");
            socket.connect();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private static void execute(double amount, String currency, String name, String msg) {
        TextComponent format = new TextComponent(message.replaceAll("&", "\u00A7").replaceAll("%amount%", String.valueOf(amount)).replaceAll("%currency%", currency).replaceAll("%name%", name).replaceAll("%message%", msg));
        server.getPlayerList().getPlayers().forEach(p -> p.sendMessage(format));
        switch (currency.toUpperCase()) {
            case "RUB":
                for (Item cmd : items) {
                    if (cmd.rub == amount) {
                        server.runCommand(cmd.cmd);
                    }
                }
            case "USD":
                for (Item cmd : items) {
                    if (cmd.usd == amount) {
                        server.runCommand(cmd.cmd);
                    }
                }
        }
    }

}
