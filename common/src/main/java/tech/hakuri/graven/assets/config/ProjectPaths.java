package tech.hakuri.graven.assets.config;

import java.nio.file.Path;

/**
 * 统一解析 Graven 的用户数据目录，并保留 Epsilon 目录作为迁移源。
 */
public final class ProjectPaths {

    public static final String CONFIG_DIRECTORY_NAME = ".graven";
    public static final String LEGACY_CONFIG_DIRECTORY_NAME = ".epsilon";

    private ProjectPaths() {
    }

    public static Path configDirectory() {
        return Path.of(System.getProperty("user.home"), CONFIG_DIRECTORY_NAME);
    }

    public static Path legacyConfigDirectory() {
        return Path.of(System.getProperty("user.home"), LEGACY_CONFIG_DIRECTORY_NAME);
    }

    public static Path scriptsDirectory() {
        return configDirectory().resolve("scripts");
    }
}
