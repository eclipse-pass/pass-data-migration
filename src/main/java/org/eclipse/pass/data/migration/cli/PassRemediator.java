package org.eclipse.pass.data.migration.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.commons.io.FileUtils;
import org.eclipse.pass.data.migration.PackageUtil;

public class PassRemediator {
    private Path input_package;
    private Map<String, JsonObject> objects;

    private static String get_string(JsonObject o, String key) {
        if (o.containsKey(key)) {
            return o.getString(key);
        }

        throw new RuntimeException("Object missing required key " + key + ": " + o);
    }

    private static JsonArray get_array(JsonObject o, String key) {
        if (o.containsKey(key)) {
            return o.getJsonArray(key);
        }

        throw new RuntimeException("Object missing required key " + key + ": " + o);
    }

    public PassRemediator(Path input_package) throws IOException {
        this.objects = new HashMap<>();
        this.input_package = input_package;

        update(PackageUtil.readObjects(input_package));
    }

    private Stream<JsonObject> get_objects_of_type(String type) {
        return objects.values().stream().filter(o -> {
            return get_string(o, "type").equals(type);
        });
    }

    private void update(Stream<JsonObject> updated_objects) {
        updated_objects.forEach(o -> {
            objects.put(get_string(o, "id"), o);
        });
    }

    public void fixLocatorIds() {
        List<JsonObject> updated_users = new ArrayList<>();

        get_objects_of_type("User").forEach(o -> {
            JsonObjectBuilder user_builder = Json.createObjectBuilder(o);

            JsonArray locators = get_array(o, "locatorIds");
            JsonArrayBuilder locators_builder = Json.createArrayBuilder();

            for (int i = 0; i < locators.size(); i++) {
                String loc = locators.getString(i);

                loc = loc.replace(":jhed:", ":eppn:");
                loc = loc.replace(":hopkinsid:", ":unique-id:");

                locators_builder.add(loc);
            }

            updated_users.add(user_builder.add("locatorIds", locators_builder).build());
        });

        update(updated_users.stream());
    }

    private void normalizeAwardNumbers() {
        List<JsonObject> updated_grants = new ArrayList<>();

        get_objects_of_type("Grant").forEach(o -> {
            String num = get_string(o, "awardNumber");

            // num = ModelUtil.normalizeAwardNumber(num);

            if (num == null) {
                throw new RuntimeException("Unable to normalize awardNumber of grant: " + o);
            }

            updated_grants.add(Json.createObjectBuilder(o).add("awardNumber", num).build());
        });

        update(updated_grants.stream());
    }

    private String get_unique_key(JsonObject o) {
        String type = get_string(o, "type");

        switch (type) {
        case "User":

            break;
        case "Grant":

            break;
        case "Funder":

            break;
        case "Submission":

            break;
        default:

            break;
        }

        return null;
    }

    public void fixDuplicates() {
        Map<String, List<JsonObject>> dupe_map = new HashMap<>();

        objects.values().forEach(o -> {
            String key = get_unique_key(o);

            if (key != null) {
                List<JsonObject> dupes = dupe_map.get(key);

                if (dupes == null) {
                    dupes = new ArrayList<>();
                    dupe_map.put(key, dupes);
                }

                dupes.add(o);
            }
        });

    }

    private void removeUselessObjects() {
        List<JsonObject> toremove = new ArrayList<>();
// TODO remove grants without awardnumbers
        get_objects_of_type("File").forEach(o -> {
            if (!o.containsKey("submission") && !o.containsKey("uri")) {
                toremove.add(o);
            }
        });

        toremove.forEach(o -> {
            objects.remove(get_string(o, "id"));
        });
    }

    public void writePackage(Path output_dir) throws IOException {
        FileUtils.copyDirectory(input_package.toFile(), output_dir.toFile(), true);
        PackageUtil.writeObjects(output_dir, objects.values().stream());
    }
}
