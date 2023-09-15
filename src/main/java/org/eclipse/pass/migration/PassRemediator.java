package org.eclipse.pass.migration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String FCREPO_ID_MARKER = "/rest";

    private static final Set<String> non_relation_list_properties = new HashSet<>();

    static {
        non_relation_list_properties.addAll(List.of("issns", "externalIds", "locatorIds", "roles", "schemas"));
    }

    private Path input_package;

    // Identifier -> PASS object
    private final Map<String, JsonObject> objects;

    // Target object id -> list of relationships with this object as a target
    Map<String, List<Relation>> target_relations = new HashMap<>();

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
        this.input_package = input_package;

        update(PackageUtil.readObjects(input_package).map(this::fix_field_names).map(this::normalize_identifiers));

        this.target_relations = getTargetRelations(objects.values().stream());
    }

    // Target id -> list of relations with that target
    public static Map<String, List<Relation>> getTargetRelations(Stream<JsonObject> objects) {
        Map<String, List<Relation>> result = new HashMap<>();

        objects.forEach(o -> {
            get_relations(o).forEach(r -> {
                List<Relation> rels = result.get(r.target);

                if (rels == null) {
                    rels = new ArrayList<>();
                    result.put(r.target, rels);
                }

                rels.add(r);
            });
        });

        return result;
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

    private void fix_locator_ids() {
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

    private JsonObject fix_field_names(JsonObject o) {
        // Publication.abstract -> Publication.publicationAbstract
        if (o.getString("type").equals("Publication")) {
            if (o.containsKey("abstract")) {
                JsonObjectBuilder builder = Json.createObjectBuilder(o);

                builder.add("publicationAbstract", o.get("abstract"));
                builder.remove("abstract");

                return builder.build();
            }
        }

        return o;
    }

    private String normalize_identifier(String id) {
        int i = id.indexOf(FCREPO_ID_MARKER);

        return i == -1 ? id : id.substring(i + FCREPO_ID_MARKER.length());
    }

    // Strip out the hostname portion of all identifiers
    private JsonObject normalize_identifiers(JsonObject o) {
        JsonObjectBuilder builder = Json.createObjectBuilder(o);

        builder.add("id", normalize_identifier(get_string(o, "id")));

        // Update all the targets in relations

        Class<?> object_type = JsonUtil.getPassJavaType(get_string(o, "type"));

        o.forEach((k, v) -> {
            if (!k.equals("id") && !k.equals("type") && !k.equals("context")) {
                Class<?> klass = JsonUtil.getPropertyJavaType(object_type, k);

                if (PassEntity.class.isAssignableFrom(klass)) {
                    builder.add(k, normalize_identifier(JsonString.class.cast(v).getString()));
                } else if (klass == List.class && !non_relation_list_properties.contains(k)) {
                    JsonArrayBuilder ab = Json.createArrayBuilder();

                    v.asJsonArray().stream().forEach(v2 -> {
                        ab.add(normalize_identifier(JsonString.class.cast(v2).getString()));
                    });

                    builder.add(k, ab.build());
                }
            }
        });

        return builder.build();
    }

    private void normalize_award_numbers() {
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

    private void add_unique_key(List<String> keys, String type, JsonObject o, String... props) {
        StringBuilder key = new StringBuilder();

        for (String prop : props) {
            if (o.containsKey(prop)) {
                String s = get_string(o, prop);

                if (!s.isEmpty()) {
                    key.append("," + s);
                }
            }
        }

        if (!key.isEmpty()) {
            keys.add(type + key.toString());
        }
    }

    private List<String> get_unique_keys(JsonObject o) {
        List<String> keys = new ArrayList<>();
        String type = get_string(o, "type");

        // Only check certain types for dupes based on previous research
        switch (type) {
        case "Grant":
            add_unique_key(keys, type, o, "localKey");

            if (keys.isEmpty()) {
                throw new RuntimeException("Cannot generate unique key for " + o);
            }

            break;
        case "Journal": {
            add_unique_key(keys, type, o, "nlmta");

            if (o.containsKey("issns")) {
                String name = get_string(o, "journalName");

                get_string_array(o, "issns").forEach(issn -> {
                    keys.add(type + "," + name + "," + issn);
                });
            }

            if (keys.isEmpty()) {
                throw new RuntimeException("Cannot generate unique key for " + o);
            }

            break;
        }
        case "Funder":
            add_unique_key(keys, type, o, "localKey");

            if (keys.isEmpty()) {
                throw new RuntimeException("Cannot generate unique key for " + o);
            }

            break;
        case "Publisher":
            add_unique_key(keys, type, o, "name", "pmcParticipation");

            if (keys.isEmpty()) {
                throw new RuntimeException("Cannot generate unique key for " + o);
            }

            break;
        case "Submission":
            String source = get_string(o, "source");

            // Only consider non-pass submissions
            if (source.equals(Source.OTHER.getValue())) {
                add_unique_key(keys, type, o, "publication", "submitter");

                if (keys.isEmpty()) {
                    throw new RuntimeException("Cannot generate unique key for " + o);
                }
            }

            break;
        case "Publication":
            add_unique_key(keys, type, o, "title");
            add_unique_key(keys, type, o, "doi");
            add_unique_key(keys, type, o, "pmid");

            if (keys.isEmpty()) {
                throw new RuntimeException("Cannot generate unique key for " + o);
            }

            break;
        case "RepositoryCopy":
            add_unique_key(keys, type, o, "accessUrl");
            add_unique_key(keys, type, o, "repository", "publication");

            if (keys.isEmpty()) {
                throw new RuntimeException("Cannot generate unique key for " + o);
            }
            break;
        }

        return keys;
    }

    private JsonObject replace(JsonObject o, String key, String old_value, String new_value) {
        JsonObjectBuilder result = Json.createObjectBuilder(o);

        if (o.containsKey(key)) {
            JsonValue jv = o.get(key);

            if (jv.getValueType() == ValueType.ARRAY) {
                JsonArrayBuilder ab = Json.createArrayBuilder();

                jv.asJsonArray().forEach(v -> {
                    if (JsonString.class.cast(v).getString().equals(old_value)) {
                        ab.add(new_value);
                    } else {
                        ab.add(v);
                    }
                });

                result.add(key, ab.build());
            } else {
                result.add(key, new_value);
            }
        }

        return result.build();
    }

    private void fix_duplicates(Map<String, List<Relation>> target_relations, List<String> dupes) {
        String prime = dupes.get(0);

        System.err.println("Prime: " + prime);

        // Switch all relations which target a duplicate to the prime
        for (int i = 1; i < dupes.size(); i++) {
            String dup = dupes.get(i);

            List<Relation> rels = target_relations.get(dup);

            System.err.println("  Handling duplicate: " + dup);

            if (rels != null) {
                rels.forEach(r -> {
                    System.err.println("  Updating relationship " + r.source + " " + r.name + " " + r.target);

                    JsonObject source = objects.get(r.source);

                    // If source is null, then source was a removed duplicate and we don't need to
                    // do anything
                    if (source != null) {
                        objects.put(r.source, replace(source, r.name, r.target, prime));

                        // System.err.println("*** " + source + " ****  " + objects.get(r.source));
                    }
                });
            }

            objects.remove(dup);
        }
    };

    private static List<Relation> get_relations(JsonObject o) {
        List<Relation> result = new ArrayList<>();
        String source = get_string(o, "id");
        Class<?> object_type = JsonUtil.getPassJavaType(get_string(o, "type"));

        o.forEach((k, v) -> {
            if (!k.equals("id") && !k.equals("type") && !k.equals("context")) {
                Class<?> klass = JsonUtil.getPropertyJavaType(object_type, k);

                if (PassEntity.class.isAssignableFrom(klass)) {
                    result.add(new Relation(source, k, v));
                } else if (klass == List.class && !non_relation_list_properties.contains(k)) {
                    v.asJsonArray().stream().forEach(v2 -> {
                        result.add(new Relation(source, k, v2));
                    });
                }
            }
        });

        return result;
    }

    private void fix_duplicates() {
        // Unique key -> list of objects with that key
        Map<String, List<JsonObject>> key_map = new HashMap<>();

        objects.values().forEach(o -> {
            get_unique_keys(o).forEach(key -> {
                List<JsonObject> dupes = key_map.get(key);

                if (dupes == null) {
                    dupes = new ArrayList<>();
                    key_map.put(key, dupes);
                }

                dupes.add(o);
            });
        });

        // Remove entries without duplicates
        key_map.entrySet().removeIf((e) -> e.getValue().size() == 1);

        // Coalesce duplicate lists that share keys
        List<List<String>> duplicates_list = new ArrayList<>();

        Set<String> keys = key_map.keySet();

        while (!keys.isEmpty()) {
            String key = keys.iterator().next();

            Set<String> dupes = new HashSet<String>();

            // System.err.println(key);

            // Check keys of duplicate objects for other duplicates from other keys
            key_map.get(key).forEach(o -> {
                get_unique_keys(o).forEach(k -> {
                    List<JsonObject> key_dupes = key_map.get(k);

                    if (key_dupes != null) {
                        key_dupes.stream().map(ko -> get_string(ko, "id")).forEach(dupes::add);
                        keys.remove(k);
                    }
                });
            });

            duplicates_list.add(new ArrayList<>(dupes));
        }

        System.err.println("Number of objects: " + objects.size());
        System.err.println("Number of duplicate sets: " + duplicates_list.size());

        // type -> count
        Map<String, Integer> dupe_counts = new HashMap<>();

        duplicates_list.forEach(dupes -> {
            String type = get_string(objects.get(dupes.get(0)), "type");
            dupe_counts.compute(type, (t, c) -> (c == null ? 0 : c) + dupes.size());
        });

        dupe_counts.forEach((t, c) -> {
            System.err.println("Dupe count for " + t + ": " + c);
        });

        duplicates_list.forEach(dupes -> {
            fix_duplicates(target_relations, dupes);
        });

        System.err.println("Number of objects after removing duplicates: " + objects.size());
    }

    private void remove_useless_objects() {
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
            String id = get_string(o, "id");

            if (target_relations.containsKey(id)) {
                throw new RuntimeException("Useless object is target of relation: " + target_relations.get(id));
            }

            System.err.println("Removing " + o);

            objects.remove(id);
        });

        System.err.println("Removed " + toremove.size());
    }

    public void run() {
        System.err.println("Remove not needed objects");
        remove_useless_objects();

        System.err.println("Fixing User locator ids");
        fix_locator_ids();

        System.err.println("Normalizing Grant award numbers");
        normalize_award_numbers();

        System.err.println("Fixing duplicates");
        fix_duplicates();
    }

    public void writePackage(Path output_dir) throws IOException {
        FileUtils.copyDirectory(input_package.toFile(), output_dir.toFile(), true);
        PackageUtil.writeObjects(output_dir, objects.values().stream());
    }

    Map<String, JsonObject> getObjects() {
        return objects;
    }
}
