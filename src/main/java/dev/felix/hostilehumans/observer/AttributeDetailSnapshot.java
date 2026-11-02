package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.progression.SpecialtyAttributes;
import dev.felix.hostilehumans.progression.SpecialtyData;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;

/** Read-only server result; contains no world/entity references and no client-side arithmetic. */
public record AttributeDetailSnapshot(UUID target, int entityId, String name, String faction, boolean boss,
        boolean enabled, float currentHealth, float budget, long tick, List<Label> labels, List<Row> rows) {
    public record Label(String key, List<String> dimensions) { public Label { dimensions = List.copyOf(dimensions); } }
    public record Layer(String key, String operation, double value) { }
    public record Note(String key, String detail) { }
    public record Row(String dimension, double base, double stored, double effective, double value,
                      boolean known, double baseline, String unit, List<Layer> layers, List<Note> notes) {
        public Row { layers = List.copyOf(layers); notes = List.copyOf(notes); }
    }
    public AttributeDetailSnapshot { labels = List.copyOf(labels); rows = List.copyOf(rows); }
    public void write(FriendlyByteBuf out) {
        out.writeUUID(target); out.writeVarInt(entityId); out.writeUtf(name, 128); out.writeUtf(faction, 256);
        out.writeBoolean(boss); out.writeBoolean(enabled); out.writeFloat(currentHealth); out.writeFloat(budget); out.writeLong(tick);
        out.writeVarInt(labels.size());
        for (Label label : labels) {
            out.writeUtf(label.key(), 64); out.writeVarInt(label.dimensions().size());
            for (String dimension : label.dimensions()) out.writeUtf(dimension, 32);
        }
        out.writeVarInt(rows.size());
        for (Row row : rows) {
            out.writeUtf(row.dimension(), 32); out.writeDouble(row.base()); out.writeDouble(row.stored()); out.writeDouble(row.effective());
            out.writeDouble(row.value()); out.writeBoolean(row.known()); out.writeDouble(row.baseline()); out.writeUtf(row.unit(), 32);
            out.writeVarInt(row.layers().size());
            for (Layer layer : row.layers()) { out.writeUtf(layer.key(),64); out.writeUtf(layer.operation(),16); out.writeDouble(layer.value()); }
            out.writeVarInt(row.notes().size());
            for (Note note : row.notes()) { out.writeUtf(note.key(),64); out.writeUtf(note.detail(),256); }
        }
    }
    public static AttributeDetailSnapshot read(FriendlyByteBuf in) {
        UUID target = in.readUUID(); int id = in.readVarInt(); String name = in.readUtf(128), faction = in.readUtf(256);
        boolean boss = in.readBoolean(), enabled = in.readBoolean(); float health = in.readFloat(), budget = in.readFloat(); long tick = in.readLong();
        if (!Float.isFinite(health) || !Float.isFinite(budget) || budget <= 0) throw new IllegalArgumentException("Invalid attribute header");
        int count = count(in, 3); List<Label> labels = new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            String key = in.readUtf(64); int n = count(in,SpecialtyAttributes.DIMENSIONS); List<String> dimensions = new ArrayList<>(n);
            for (int j=0;j<n;j++) dimensions.add(dimension(in.readUtf(32)));
            labels.add(new Label(key, dimensions));
        }
        count = count(in,SpecialtyAttributes.DIMENSIONS); if(count != SpecialtyAttributes.DIMENSIONS) throw new IllegalArgumentException("Expected thirteen attribute rows");
        List<Row> rows = new ArrayList<>(count); Set<String> seen = new HashSet<>();
        for (int i=0;i<count;i++) {
            String dimension = dimension(in.readUtf(32)); if (!seen.add(dimension)) throw new IllegalArgumentException("Duplicate dimension");
            double base=finite(in.readDouble()), stored=finite(in.readDouble()), effective=finite(in.readDouble()), value=finite(in.readDouble());
            boolean known=in.readBoolean(); double baseline=finite(in.readDouble()); String unit=in.readUtf(32);
            int n=count(in,20); List<Layer> layers=new ArrayList<>(n);
            for(int j=0;j<n;j++) {
                String key=in.readUtf(64), operation=in.readUtf(16); double amount=finite(in.readDouble());
                if(!Set.of("multiply","add","set").contains(operation)) throw new IllegalArgumentException("Unknown layer operation");
                layers.add(new Layer(key,operation,amount));
            }
            n=count(in,24); List<Note> notes=new ArrayList<>(n);
            for(int j=0;j<n;j++) notes.add(new Note(in.readUtf(64),in.readUtf(256)));
            rows.add(new Row(dimension,base,stored,effective,value,known,baseline,unit,layers,notes));
        }
        return new AttributeDetailSnapshot(target,id,name,faction,boss,enabled,health,budget,tick,labels,rows);
    }
    private static int count(FriendlyByteBuf in,int maximum) { int n=in.readVarInt(); if(n<0||n>maximum)throw new IllegalArgumentException("Attribute collection limit");return n; }
    private static double finite(double value) { if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite attribute");return value; }
    private static String dimension(String key) { SpecialtyData.dimension(key); return key; }
}
