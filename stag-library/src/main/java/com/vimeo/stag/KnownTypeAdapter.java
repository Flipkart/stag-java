package com.vimeo.stag;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KnownTypeAdapter {

    private static ListTypeAdapter listTypeAdapter;

    public static <T> ListTypeAdapter getListTypeAdapter(TypeAdapter<T> valueTypeAdapter) {
        if (null == listTypeAdapter) {
            listTypeAdapter = new ListTypeAdapter<>(valueTypeAdapter);
        }
        return listTypeAdapter;
    }

    public static class ListTypeAdapter<T> extends TypeAdapter<List<T>> {

        private TypeAdapter<T> valueTypeAdapter;

        public ListTypeAdapter(TypeAdapter<T> valueTypeAdapter) {
            this.valueTypeAdapter = valueTypeAdapter;
        }

        @Override
        public void write(JsonWriter writer, List<T> value) throws IOException {
            writer.beginArray();
            for (T item : value) {
                valueTypeAdapter.write(writer, item);
            }
            writer.endArray();
        }

        @Override
        public List<T> read(JsonReader reader) throws IOException {
            if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                return null;
            }
            reader.beginObject();

            List<T> object = new ArrayList<>();

            while (reader.hasNext()) {
                com.google.gson.stream.JsonToken jsonToken = reader.peek();
                if (jsonToken == com.google.gson.stream.JsonToken.NULL) {
                    reader.skipValue();
                    continue;
                }

                if (jsonToken == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        object.add(valueTypeAdapter.read(reader));
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();
            return object;
        }
    }
}