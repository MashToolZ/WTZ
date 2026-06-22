package xyz.mashtoolz.wtz.features.mount.enclosure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public record EnclosureState(String location, List<Row> rows) {

    public JsonObject toSyncJson() {
        JsonArray mounts = new JsonArray();
        for (Row row : rows) {
            if (row.mainMount() == null) {
                mounts.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }

            JsonObject mount = row.mainMount().toJson();
            JsonArray feedItems = new JsonArray();
            for (QueueItem item : row.queue()) feedItems.add(item.toJson());
            mount.add("feedSlots", feedItems);
            mounts.add(mount);
        }

        JsonObject data = new JsonObject();
        data.add("mounts", mounts);
        if (location != null) data.addProperty("location", location);
        return data;
    }

    public record Row(int index, int mountSlot, MountData mainMount, List<QueueItem> queue) {
        public boolean isBreedingQueue() {
            return mainMount != null && queue.stream().anyMatch(item -> item.kind() == QueueKind.MOUNT);
        }
    }

    public record MountData(JsonObject data, boolean breedingAlert) {
        public JsonObject toJson() {
            return data.deepCopy();
        }
    }

    public record QueueItem(QueueKind kind, JsonObject data) {
        public JsonObject toJson() {
            JsonObject json = data.deepCopy();
            json.addProperty("kind", kind.jsonName);
            return json;
        }
    }

    public enum QueueKind {
        MOUNT("mount"),
        MATERIAL("material");

        private final String jsonName;

        QueueKind(String jsonName) {
            this.jsonName = jsonName;
        }
    }
}
