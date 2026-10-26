package dev.wandering;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

/**
 * Wandering settings, loaded once at startup from {@code config/wandering.yml}.
 */
public final class WanderingConfig {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Batch outgoing packets per connection: one network task and one flush per batch instead of per packet. */
    public static boolean batchPacketFlush = true;
    /** Write playerdata, stats and advancements files on an IO thread instead of the main thread. */
    public static boolean asyncPlayerSave = true;

    private WanderingConfig() {
    }

    public static void init(final File configDir) {
        final File file = new File(configDir, "wandering.yml");
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.options().setHeader(java.util.List.of(
            "Wandering configuration. Every option keeps vanilla gameplay identical.",
            "Changes need a restart."
        ));

        batchPacketFlush = get(config, "network.batch-packet-flush", true);
        asyncPlayerSave = get(config, "player-data.async-save", true);

        try {
            config.save(file);
        } catch (final IOException e) {
            LOGGER.error("Could not save {}", file, e);
        }
    }

    private static boolean get(final YamlConfiguration config, final String path, final boolean def) {
        config.addDefault(path, def);
        config.set(path, config.getBoolean(path, def));
        return config.getBoolean(path);
    }
}
