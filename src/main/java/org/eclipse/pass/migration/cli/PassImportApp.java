package org.eclipse.pass.migration.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.eclipse.pass.migration.JsonUtil;
import org.eclipse.pass.migration.PackageUtil;
import org.eclipse.pass.support.client.ModelUtil;
import org.eclipse.pass.support.client.PassClient;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.PmcParticipation;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.User;
import org.eclipse.pass.support.client.model.UserRole;

public class PassImportApp {
    private PassImportApp() {
    }

    // Create a PassEntity and set the id. It must have an appropriate constructor.
    private static PassEntity create_pass_entity(String id, String type) {
        try {
            return (PassEntity) JsonUtil.getPassJavaType(type).getConstructor(String.class).newInstance(id);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Failed to create: " + type, e);
        }
    }

    // Create a PassEntity
    private static PassEntity create_pass_entity(String type) {
        try {
            return (PassEntity) Class.forName("org.eclipse.pass.support.client.model." + type).getConstructor()
                    .newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to create: " + type, e);
        }
    }

    private static String resolve_ref(Map<String, PassEntity> entities, JsonValue v) {
        return resolve_ref(entities, JsonString.class.cast(v).getString());
    }

    private static String resolve_ref(Map<String, PassEntity> entities, String id) {
        if (!entities.containsKey(id)) {
            throw new RuntimeException("Cannot find entity: " + id);
        }

        return entities.get(id).getId();
    }

    // Set a value on an object using a set method.
    private static void set_value(Object obj, String key, JsonValue json_value, Map<String, PassEntity> entities) {
        String set_method = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        Class<?> java_type = JsonUtil.getPropertyJavaType(obj.getClass(), key);

        Object value;

        switch (json_value.getValueType()) {
        case ARRAY:
            if (key.equals("roles")) {
                value = json_value.asJsonArray().stream().map(v -> UserRole.of(JsonString.class.cast(v).getString()))
                        .toList();
            } else if (key.equals("schemas")) {
                value = json_value.asJsonArray().stream().map(v -> URI.create(JsonString.class.cast(v).getString()))
                        .toList();
            } else if (key.equals("grants")) {
                value = json_value.asJsonArray().stream().map(v -> new Grant(resolve_ref(entities, v))).toList();
            } else if (key.equals("coPis")) {
                value = json_value.asJsonArray().stream().map(v -> new User(resolve_ref(entities, v))).toList();
            } else if (key.equals("preparers")) {
                value = json_value.asJsonArray().stream().map(v -> new User(resolve_ref(entities, v))).toList();
            } else if (key.equals("effectivePolicies")) {
                value = json_value.asJsonArray().stream().map(v -> new Policy(resolve_ref(entities, v))).toList();
            } else if (key.equals("repositories")) {
                value = json_value.asJsonArray().stream().map(v -> new Repository(resolve_ref(entities, v))).toList();
            } else {
                value = json_value.asJsonArray().stream().map(v -> JsonString.class.cast(v).getString()).toList();
            }

            break;
        case FALSE:
            value = Boolean.FALSE;
            break;
        case NULL:
            throw new RuntimeException("JSON null type unsupported");
        case NUMBER:
            throw new RuntimeException("JSON number type unsupported");
        case OBJECT:
            throw new RuntimeException("JSON object type unsupported");
        case STRING:
            String s = JsonString.class.cast(json_value).getString();
            if (java_type == String.class) {
                value = s;
            } else if (java_type == URI.class) {
                value = URI.create(s);
            } else if (java_type == ZonedDateTime.class) {
                value = ZonedDateTime.parse(s, ModelUtil.dateTimeFormatter());
            } else if (Enum.class.isAssignableFrom(java_type)) {
                value = create_pass_enum(s, java_type);
            } else if (PassEntity.class.isAssignableFrom(java_type)) {
                value = create_pass_entity(resolve_ref(entities, s), java_type.getSimpleName());
            } else {
                throw new RuntimeException("Unknown type " + java_type + " for " + key);
            }

            break;
        case TRUE:
            value = Boolean.TRUE;
            break;
        default:
            throw new RuntimeException("Unknown JSON type");
        }

        try {
            Class<?> value_class = value.getClass();

            // Handle the set method taking a List instead of a List implementation
            if (value instanceof List) {
                value_class = List.class;
            }

            obj.getClass().getMethod(set_method, value_class).invoke(obj, value);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke: " + set_method, e);
        }
    }

    private static Object create_pass_enum(String s, Class<?> java_type) {
        // Hack for enum which does not follow the pattern
        if (java_type == PmcParticipation.class) {
            return PmcParticipation.valueOf(s);
        }

        try {
            return java_type.getMethod("of", String.class).invoke(null, s);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e) {
            throw new RuntimeException("Failed parse enum: " + java_type, e);
        }
    }

    private static PassEntity as_pass_entity(JsonObject o, Map<String, PassEntity> entities) {
        PassEntity result = create_pass_entity(o.getString("type"));

        o.forEach((k, v) -> {
            if (!k.equals("type") && !k.equals("context")) {
                try {
                    set_value(result, k, v, entities);
                } catch (Exception e) {
                    throw new RuntimeException("Error: Failed to set value " + k + " from " + o, e);
                }
            }
        });

        return result;
    }

    private static class Counter {
        int value = 0;
    }

    public static void main(String[] args) throws IOException {
        PassClient client = PassClient.newInstance();

        if (args.length != 1) {
            System.err.println("Usage: INPUT_DIR");
            System.exit(1);
        }

        Path input_dir = Path.of(args[0]);

        // Map from the original entity id to the entity
        Map<String, PassEntity> entities = new HashMap<>();

        List<String> type_order = List.of("User", "Repository", "Journal", "Publisher", "Repository", "Policy",
                "Funder", "Grant", "Publication", "Submission", "RepositoryCopy", "Deposit", "SubmissionEvent", "File");
        Counter total = new Counter();

        for (String type : type_order) {
            System.err.println("Importing " + type);

            Counter count = new Counter();

            PackageUtil.readObjects(input_dir).filter(o -> o.getString("type").equals(type)).forEach(o -> {
                PassEntity entity = null;

                count.value++;

                try {
                    entity = as_pass_entity(o, entities);
                    entities.put(entity.getId(), entity);

                    if (type.equals("File")) {
                        File f = File.class.cast(entity);
                        URI uri = client.uploadBinary(f.getName(),
                                PackageUtil.readFileFully(input_dir, f.getUri().getPath()));

                        String path = uri.getRawPath();

                        int i = path.indexOf("/file/");

                        if (i == -1) {
                            throw new RuntimeException("Malformed file uri: " + f);
                        }


                        f.setUri(new URI(path.substring(i)));
                    }

                    client.createObject(entity);
                } catch (Exception e) {
                    System.err.println("Error: Failed on json " + o);
                    if (entity != null) {
                        System.err.println("Entity: " + entity);
                    }
                    System.err.println("Exception: " + e.getMessage());
                    System.exit(1);
                }
            });

            total.value += count.value;
            System.err.println("Number imported: " + count.value);
        }

        System.err.println("Total imported: " + total.value);
    }
}
