package tech.hakuri.graven.modules.impl.render;

import tech.hakuri.graven.events.bus.EventHandler;
import tech.hakuri.graven.events.impl.ChatReceivedEvent;
import tech.hakuri.graven.modules.Category;
import tech.hakuri.graven.modules.Module;
import tech.hakuri.graven.settings.impl.BoolSetting;
import tech.hakuri.graven.settings.impl.StringSetting;
import tech.hakuri.graven.utils.player.ChatUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public final class StreamerMode extends Module {

    public static final StreamerMode INSTANCE = new StreamerMode();

    private static final Pattern SERVER_BRAND_PATTERN = Pattern.compile("Hypixel BungeeCord \\(.+\\) <- .+");
    private static final String[] HYPIXEL_DOMAINS = {"hypixel.net", "hypixel.io", "technoblade.club"};
    private static final String[] HYPIXEL_PROXY_DOMAINS = {"liquidproxy.net", "nyap.buzz"};

    private final BoolSetting hideServerId = boolSetting("Hide server ID", true);
    private final BoolSetting hideUsername = boolSetting("Hide username", true);
    private final StringSetting customUsername = stringSetting("Custom username", "You", hideUsername::getValue);

    private StreamerMode() {
        super("Streamer Mode", Category.RENDER);
    }

    @EventHandler
    private void onChatReceived(ChatReceivedEvent event) {
        if (!hideServerId.getValue() || !isOnHypixel()) return;

        String message = event.getText().getString();
        if (message.startsWith("Sending you to ")) {
            event.cancel();
            String serverId = message.replace("Sending you to ", "").replace("!", "");
            ChatUtils.addChatMessage(false, Component.literal("§aSending you to §k" + serverId + "§r§a!"));
        }
    }

    public String filter(String text) {
        if (isHidingUsername()) {
            String replacement = getCustomUsername();
            if (!replacement.isEmpty()) {
                text = StringUtils.replaceIgnoreCase(text, mc.getUser().getName(), replacement);
            }
        }
        return text;
    }

    public Component filterScoreboardEntry(Component name, int index) {
        if (!isEnabled() || !isHidingServerId() || !isOnHypixel() || index != 0) return name;

        String nameString = name.getString();
        if (nameString.contains("/") && nameString.contains("  ")) {
            String[] parts = nameString.split(" {2}");
            if (parts.length > 1) {
                return Component.literal("§7" + parts[0] + "  §8§k" + parts[1]);
            }
        }
        return name;
    }

    public boolean isHidingServerId() {
        return hideServerId.getValue();
    }

    public String getCustomUsername() {
        return customUsername.getValue().trim();
    }

    public boolean isHidingUsername() {
        return hideUsername.getValue();
    }

    public boolean isOnHypixel() {
        ServerData server = mc.getCurrentServer();
        if (server == null) return false;

        ServerAddress address = ServerAddress.parseString(server.ip);
        if (address.getPort() != 25565) return false;

        boolean direct = matchesAnyDomain(address, HYPIXEL_DOMAINS);
        boolean proxy = matchesAnyDomain(address, HYPIXEL_PROXY_DOMAINS);
        if (!direct && !proxy) return false;

        ClientPacketListener connection = mc.getConnection();
        String serverBrand = connection == null ? null : connection.serverBrand();
        boolean validBrand = serverBrand != null && SERVER_BRAND_PATTERN.matcher(serverBrand).matches();
        return direct ? serverBrand == null || validBrand : validBrand;
    }

    private static boolean matchesAnyDomain(ServerAddress address, String[] domains) {
        for (String domain : domains) {
            if (isAddressOfDomain(address, domain)) return true;
        }
        return false;
    }

    private static boolean isAddressOfDomain(ServerAddress address, String domain) {
        String addressString = address.getHost().toLowerCase();
        String regex = "^(?:[a-zA-Z0-9-]+\\.)*" + Pattern.quote(domain) + "(\\.*)$";
        return Pattern.compile(regex).matcher(addressString).matches();
    }
}
