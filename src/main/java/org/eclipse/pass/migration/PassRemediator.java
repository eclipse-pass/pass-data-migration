package org.eclipse.pass.migration;

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
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.apache.commons.io.FileUtils;
import org.eclipse.pass.support.client.ModelUtil;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Source;

public class PassRemediator {
    private Path input_package;

    // identifier -> object
    private final Map<String, JsonObject> objects;

    // name -> target type
    private final Map<String, String> relations;

    private static String get_string(JsonObject o, String key) {
        return get_string(o, key, null);
    }

    private static String get_string(JsonObject o, String key, String value) {
        if (o.containsKey(key)) {
            return o.getString(key);
        }

        if (value != null) {
            return value;
        }

        throw new RuntimeException("Object missing required key " + key + ": " + o);
    }

    private static JsonArray get_array(JsonObject o, String key) {
        if (o.containsKey(key)) {
            return o.getJsonArray(key);
        }

        throw new RuntimeException("Object missing required key " + key + ": " + o);
    }

    private static List<String> get_string_array(JsonObject o, String key) {
        return get_array(o, key).stream().map(v -> JsonString.class.cast(v).getString()).toList();
    }

    public PassRemediator(Path input_package) throws IOException {
        this.objects = new HashMap<>();
        this.relations = new HashMap<>();
        this.input_package = input_package;

        // TODO lookup like in importer..

        relations.put("submitter", "User");
        relations.put("pi", "User");
        relations.put("coPis", "User");

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

    public void fixFieldNames() {
        List<JsonObject> updated = new ArrayList<>();

        // Publication.abstract -> Publication.publicationAbstract
        get_objects_of_type("Publication").forEach(o -> {
            if (o.containsKey("abstract")) {
                JsonObjectBuilder builder = Json.createObjectBuilder(o);

                builder.add("publicationAbstract", o.get("abstract"));
                builder.remove("abstract");

                updated.add(builder.build());
            }
        });

        update(updated.stream());
    }

    public void normalizeAwardNumbers() {
        List<JsonObject> updated_grants = new ArrayList<>();

        get_objects_of_type("Grant").forEach(o -> {
            String num = get_string(o, "awardNumber");

            num = ModelUtil.normalizeAwardNumber(num);

            if (num == null) {
                throw new RuntimeException("Unable to normalize awardNumber of grant: " + o);
            }

            updated_grants.add(Json.createObjectBuilder(o).add("awardNumber", num).build());
        });

        update(updated_grants.stream());
    }

    // Return null if no need to check for dupes for that type
    private String get_unique_key(JsonObject o) {
        String type = get_string(o, "type");

        switch (type) {
        case "Grant":
            return get_string(o, "awardNumber");
        case "Journal":
            return get_string(o, "journalName");
        // + "@" + String.join(",", get_string_array(o, "issns"));
        case "Funder":
            return get_string(o, "name") + "," + get_string(o, "localKey");
        case "Submission":
            String source = get_string(o, "source");

            // Only consider non-pass submissions
            if (source.equals(Source.PASS.getValue())) {
                return null;
            }

            return get_string(o, "publication");
        case "Publication":
            return get_string(o, "title");
        case "RepositoryCopy":
            return get_string(o, "publication") + "," + get_string(o, "repository", "");
        default:
            return null;
        }
    }

    private JsonObject replace(JsonObject o, String key, JsonValue value) {
        JsonObjectBuilder result = Json.createObjectBuilder(o);

        if (o.containsKey(key)) {
            JsonValue old_value = o.get(key);

            if (old_value.getValueType() == ValueType.ARRAY) {
                value = Json.createArrayBuilder(old_value.asJsonArray()).add(value).build();
            }

            result.add(key, value);
        }

        return result.build();
    }

    private void fix_duplicates(Map<String, List<Relation>> target_relations, List<JsonObject> dupes) {
        System.err.println(dupes);

        JsonObject prime = dupes.remove(0);
        String prime_id = get_string(prime, "id");
        JsonValue prime_id_value = Json.createValue(prime_id);

        System.err.println("Prime: " + prime_id);

        // Switch all relations which target a duplicate to the prime
        dupes.forEach(dup -> {
            String dup_id = get_string(dup, "id");
            List<Relation> rels = target_relations.get(dup_id);

            System.err.println("Handling duplicate: " + dup_id);

            if (rels != null) {
                rels.forEach(r -> {
                    System.err.println("Updating relationship " + r.source + " " + r.name + " " + r.target);

                    JsonObject source = objects.get(r.source);

                    // If source is null, then source was a removed duplicate and we don't need to do anything
                    if (source != null) {
                        objects.put(r.source, replace(source, r.name, prime_id_value));
                    }
                });
            }

            objects.remove(dup_id);
        });
    };

    private static class Relation {
        private final String name;
        private final String source;
        private final String target;

        public Relation(String source, String name, String target) {
            this.name = name;
            this.source = source;
            this.target = target;
        }

        public Relation(String source, String name, JsonValue target) {
            this(source, name, JsonString.class.cast(target).getString());
        }
    }

    private List<Relation> get_relations(JsonObject o) {
        List<Relation> result = new ArrayList<>();
        String source = get_string(o, "id");
        Class<?> object_type = JsonUtil.getPassJavaType(get_string(o, "type"));

        o.forEach((k, v) -> {
            if (!k.equals("id") && !k.equals("type") && !k.equals("context")) {
                Class<?> klass = JsonUtil.getPropertyJavaType(object_type, k);

                if (PassEntity.class.isAssignableFrom(klass)) {
                    result.add(new Relation(source, k, v));
                } else if (klass == List.class) {
                    v.asJsonArray().stream().forEach(v2 -> {
                        result.add(new Relation(source, k, v2));
                    });
                }
            }
        });

        return result;
    }

    public void fixDuplicates() {
        // Unique key -> list of duplicate objects
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

        // Remove objects without duplicates from dupe_map
        List<String> toremove = new ArrayList<>();
        dupe_map.forEach((key, dupes) -> {
            if (dupes.size() == 1) {
                toremove.add(key);
            }
        });

        toremove.forEach(dupe_map::remove);

        System.err.println("Number of objects: " + objects.size());
        System.err.println("Number of duplicate sets: " + dupe_map.size());

        // Target object id -> list of relationships with this object as a target
        Map<String, List<Relation>> target_relations = new HashMap<>();

        objects.values().forEach(o -> {
            get_relations(o).forEach(r -> {
                List<Relation> rels = target_relations.get(r.target);

                if (rels == null) {
                    rels = new ArrayList<>();
                    target_relations.put(r.target, rels);
                }

                rels.add(r);
            });
        });

        dupe_map.values().forEach(dupes -> {
            fix_duplicates(target_relations, dupes);
        });

        System.err.println("Number of objects after removing duplicates: " + objects.size());
    }

    public void removeUselessObjects() {
        List<JsonObject> toremove = new ArrayList<>();

        get_objects_of_type("Grant").forEach(o -> {
            if (!o.containsKey("awardNumber")) {
                toremove.add(o);
            }
        });

        get_objects_of_type("Funder").forEach(o -> {
            if (!o.containsKey("localKey") || !o.containsKey("name")) {
                toremove.add(o);
            }
        });

        get_objects_of_type("File").forEach(o -> {
            if (!o.containsKey("submission") && !o.containsKey("uri")) {
                toremove.add(o);
            }
        });

        toremove.forEach(o -> {
            objects.remove(get_string(o, "id"));
        });

        System.err.println("Removed " + toremove.size());
    }

    public void writePackage(Path output_dir) throws IOException {
        FileUtils.copyDirectory(input_package.toFile(), output_dir.toFile(), true);
        PackageUtil.writeObjects(output_dir, objects.values().stream());
    }
}
