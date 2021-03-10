package io.github.speedbridgemc.entityevents.impl;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MappedNames {
    private MappedNames() { }

    public static final @NotNull String CLASS_ENTITY;
    public static final @NotNull String CLASS_DAMAGE_SOURCE;
    public static final @NotNull String METHOD_ENTITY_DAMAGE_NAME;
    public static final @NotNull String METHOD_ENTITY_DAMAGE_DESC;
    public static final @NotNull String METHOD_EVENT_DAMAGE_INVOKE_DESC;

    static {
        final String ns = "intermediary";
        MappingResolver mr = FabricLoader.getInstance().getMappingResolver();
        CLASS_ENTITY = b(mr.mapClassName(ns, "net.minecraft.class_1297"));
        CLASS_DAMAGE_SOURCE = b(mr.mapClassName(ns, "net.minecraft.class_1282"));
        METHOD_ENTITY_DAMAGE_NAME = mr.mapMethodName(ns, "net.minecraft.class_1297",
                "method_5643", "(Lnet/minecraft/class_1282;F)Z");
        METHOD_ENTITY_DAMAGE_DESC = String.format("(L%s;F)Z", CLASS_DAMAGE_SOURCE);
        METHOD_EVENT_DAMAGE_INVOKE_DESC = String.format("(L%s;L%s;F)Z", CLASS_ENTITY, CLASS_DAMAGE_SOURCE);
    }

    private static @NotNull String b(@NotNull String s) {
        return s.replace('.', '/');
    }

    public static void initialize() { /* clinit */ }

    public static boolean matchesDamageMethod(@NotNull String name, @NotNull String desc) {
        return METHOD_ENTITY_DAMAGE_NAME.equals(name) && METHOD_ENTITY_DAMAGE_DESC.equals(desc);
    }
}
