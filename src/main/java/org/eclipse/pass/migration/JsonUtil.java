package org.eclipse.pass.migration;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class JsonUtil {
    private static final Map<String, Class<?>> json_property_type = new HashMap<>();

    public static Class<?> getPropertyJavaType(Class<?> klass, String key) {
        if (json_property_type.containsKey(key)) {
            return json_property_type.get(key);
        }

        String get_method = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        Class<?> type = null;

        for (Method m : klass.getMethods()) {
            if (m.getName().equals(get_method)) {
                type = m.getReturnType();
                break;
            }
        }

        if (type == null) {
            throw new RuntimeException("Could not find key " + key + " in " + klass.getName());
        }

        json_property_type.put(key, type);
        return type;
    }

    public static Class<?> getPassJavaType(String type) {
        try {
            return Class.forName("org.eclipse.pass.support.client.model." + type);
        } catch (IllegalArgumentException | SecurityException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to find class for type : " + type, e);
        }
    }
}
