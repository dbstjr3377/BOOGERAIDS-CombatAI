package com.boogeraids.combatai.pack;

import com.boogeraids.combatai.BoogerAIDS;
import com.boogeraids.combatai.config.GameConfig;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * jar 에 내장된 리소스팩(BOOGERAIDS-pack.zip)을 내장 HTTP 서버로 배포한다.
 * plugins/BOOGERAIDS-CombatAI/custom-pack.zip 이 있으면 그것을 대신 사용.
 */
public final class ResourcePackService {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("boogeraids-combatai-pack".getBytes(StandardCharsets.UTF_8));

    private final BoogerAIDS plugin;
    private HttpServer server;
    private byte[] data;
    private String sha1;
    private String url;

    public ResourcePackService(BoogerAIDS plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        GameConfig cfg = plugin.gameConfig();
        if (!cfg.packEnabled) return;

        if (cfg.packExternalUrl != null && !cfg.packExternalUrl.isBlank()) {
            url = cfg.packExternalUrl;
            sha1 = cfg.packExternalSha1 == null || cfg.packExternalSha1.isBlank() ? null : cfg.packExternalSha1.trim();
            plugin.getLogger().info("외부 리소스팩 URL 사용: " + url);
            return;
        }

        try {
            File custom = new File(plugin.getDataFolder(), "custom-pack.zip");
            if (custom.isFile()) {
                data = Files.readAllBytes(custom.toPath());
            } else {
                try (InputStream in = plugin.getResource("BOOGERAIDS-pack.zip")) {
                    if (in == null) {
                        plugin.getLogger().warning("jar 안에 리소스팩이 없습니다. (gradle build 로 빌드했는지 확인)");
                        return;
                    }
                    data = in.readAllBytes();
                }
            }
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
            server = HttpServer.create(new InetSocketAddress(cfg.packPort), 0);
            server.createContext("/pack.zip", ex -> {
                try (ex) {
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    ex.sendResponseHeaders(200, data.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(data);
                    }
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "BOOGERAIDS-pack-http");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            url = "http://" + cfg.packHost + ":" + cfg.packPort + "/pack.zip?v=" + sha1.substring(0, 8);
            plugin.getLogger().info("리소스팩 서버 시작: " + url + " (sha1 " + sha1 + ")");
            if ("localhost".equalsIgnoreCase(cfg.packHost)) {
                plugin.getLogger().warning("resource-pack.public-host 가 localhost 입니다. 외부 플레이어는 받을 수 없으니 서버 주소로 바꾸세요.");
            }
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            plugin.getLogger().severe("리소스팩 서버 시작 실패: " + e.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void send(Player p) {
        GameConfig cfg = plugin.gameConfig();
        if (!cfg.packEnabled || url == null) return;
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), sha1 == null ? "" : sha1);
            p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .replace(true)
                    .required(cfg.packRequired)
                    .prompt(Component.text("BOOGERAIDS 연구소 HUD 리소스팩이 필요합니다.", NamedTextColor.AQUA))
                    .build());
        } catch (Exception ex) {
            plugin.getLogger().warning("리소스팩 전송 실패 (" + p.getName() + "): " + ex.getMessage());
        }
    }
}
