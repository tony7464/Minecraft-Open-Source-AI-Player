package com.joserayos.aiplayers.brain.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Tiny JSON-Schema builder for tool parameters. */
public final class Schema {
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    public static Schema object() { return new Schema(); }

    public Schema str(String name, String description, boolean req) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "string");
        p.addProperty("description", description);
        properties.add(name, p);
        if (req) required.add(name);
        return this;
    }

    public Schema integer(String name, String description, int min, int max, boolean req) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "integer");
        p.addProperty("description", description);
        p.addProperty("minimum", min);
        p.addProperty("maximum", max);
        properties.add(name, p);
        if (req) required.add(name);
        return this;
    }

    public JsonObject build() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        o.add("properties", properties.deepCopy());
        o.add("required", required.deepCopy());
        return o;
    }
}
