package io.github.speedbridgemc.entityevents.impl;

import blue.endless.jankson.*;
import blue.endless.jankson.api.SyntaxError;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class ScanResultCache {
    public static final class Entry {
        public final @NotNull String modId;
        public final @Nullable String fileChecksum;
        public final @NotNull ImmutableSet<String> entitySubclasses;

        public Entry(@NotNull String modId, @Nullable String fileChecksum, @NotNull ImmutableSet<String> entitySubclasses) {
            this.modId = modId;
            this.fileChecksum = fileChecksum;
            this.entitySubclasses = entitySubclasses;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger("EntityEvents|ScanResultCache");
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("speedbridge")
            .resolve("entity-events").resolve("scan_cache.json5").normalize();
    private final Jankson jankson = Jankson.builder().build();
    private final JsonGrammar grammar = JsonGrammar.builder()
            .printTrailingCommas(true)
            .build();
    private final Object2ReferenceOpenHashMap<String, Entry> backingMap =
            new Object2ReferenceOpenHashMap<>();

    public void load() {
        LOGGER.info("Loading scan result cache from \"{}\"...", path.toString());
        backingMap.clear();
        try (InputStream input = Files.newInputStream(path)) {
            JsonObject root = jankson.load(input);
            for (String modId : root.keySet()) {
                JsonObject entry = root.getObject(modId);
                if (entry == null)
                    continue;
                String fileChecksum = entry.get(String.class, "file_checksum");
                JsonArray classNames = entry.get(JsonArray.class, "entity_subclasses");
                if (classNames == null)
                    continue;
                ImmutableSet.Builder<String> builder = ImmutableSet.builder();
                for (int j = 0; j < classNames.size(); j++) {
                    String className = classNames.getString(j, "");
                    if (className.isEmpty())
                        continue;
                    builder.add(className);
                }
                putEntry(new Entry(modId, fileChecksum, builder.build()));
            }
        } catch (NoSuchFileException e) {
            LOGGER.info("Scan result cache does not exist. Entity Events will now scan for Entity subclasses.");
        } catch (IOException e) {
            LOGGER.error("Failed to read cached results from file!", e);
        } catch (SyntaxError e) {
            LOGGER.error("Failed to parse cached results into JSON!", e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Object2ReferenceMap.Entry<String, Entry> mapEntry : backingMap.object2ReferenceEntrySet()) {
            Entry entry = mapEntry.getValue();
            JsonObject entryJson = new JsonObject();
            if (entry.fileChecksum != null)
                entryJson.put("file_checksum", new JsonPrimitive(entry.fileChecksum));
            JsonArray classNames = new JsonArray();
            for (String className : entry.entitySubclasses)
                classNames.add(new JsonPrimitive(className));
            entryJson.put("entity_subclasses", classNames);
            root.put(mapEntry.getKey(), entryJson);
        }
        String json = root.toJson(grammar);
        LOGGER.info("Caching and saving scan results to \"{}\"...", path.toString());
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            LOGGER.error("Failed to create folder to save cache results!", e);
            return;
        }
        try (OutputStream output = Files.newOutputStream(path);
             OutputStreamWriter writer = new OutputStreamWriter(output)) {
            writer.write(json);
        } catch (IOException e) {
            LOGGER.error("Failed to save cached results!", e);
        }
    }

    public @Nullable Entry getEntry(@NotNull String modId) {
        return backingMap.get(modId);
    }

    public void putEntry(@NotNull Entry entry) {
        backingMap.put(entry.modId, entry);
    }

    public void removeEntry(@NotNull String modId) {
        backingMap.remove(modId);
    }

    public @NotNull ReferenceCollection<Entry> getAllEntries() {
        return backingMap.values();
    }
}
