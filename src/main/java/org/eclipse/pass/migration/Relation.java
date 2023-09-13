package org.eclipse.pass.migration;

import javax.json.JsonString;
import javax.json.JsonValue;

class Relation {
    public final String name;
    public final String source;
    public final String target;

    public Relation(String source, String name, String target) {
        this.name = name;
        this.source = source;
        this.target = target;
    }

    public Relation(String source, String name, JsonValue target) {
        this(source, name, JsonString.class.cast(target).getString());
    }

    @Override
    public String toString() {
        return this.source + " " + this.name + " " + target;
    }
}