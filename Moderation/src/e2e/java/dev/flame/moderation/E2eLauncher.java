package dev.flame.moderation;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

import java.nio.file.Path;

public final class E2eLauncher {
    public static void main(String[] args) throws Exception {
        Knot knot = new Knot(EnvType.SERVER);
        ClassLoader loader = knot.init(new String[]{"--gameDir", Path.of(".").toAbsolutePath().toString()});
        knot.addToClassPath(Path.of(E2eLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        Class.forName("dev.flame.moderation.E2e", true, loader).getMethod("main", String[].class)
                .invoke(null, (Object) args);
    }
}
