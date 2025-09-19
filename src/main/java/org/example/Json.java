package org.example;


import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;


public final class Json {
    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new JsonSerializer<Instant>() {
                public JsonElement serialize(Instant src, Type t, JsonSerializationContext c){
                    return new JsonPrimitive(src.toString());
                }
            })
            .registerTypeAdapter(Instant.class, new JsonDeserializer<Instant>() {
                public Instant deserialize(JsonElement json, Type t, JsonDeserializationContext c){
                    return Instant.parse(json.getAsString());
                }
            })
            .setPrettyPrinting()
            .create();


    private Json() {}
}