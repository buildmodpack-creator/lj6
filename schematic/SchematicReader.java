package com.example.addon.schematic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Reads Sponge schematic v2/v3 and Litematica .litematic files. */
public final class SchematicReader {
    private static final long MAX_NBT_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_BLOCKS = 16_000_000;
    private SchematicReader() {}

    public static SchematicData read(Path file) throws IOException {
        if (file == null || file.getFileName() == null) throw new IOException("No schematic file selected.");
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_NBT_BYTES));
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".schem")) return readSponge(root);
        if (name.endsWith(".litematic")) return readLitematic(root);
        throw new IOException("Supported formats: .schem and .litematic.");
    }

    private static SchematicData readSponge(CompoundTag root) throws IOException {
        if (root.contains("Schematic")) root = root.getCompound("Schematic");
        int version = root.getInt("Version");
        if (version != 2 && version != 3) throw new IOException("Unsupported Sponge version: " + version);
        int w = dimension(root, "Width"), h = dimension(root, "Height"), l = dimension(root, "Length");
        int volume = checkedVolume(w, h, l);
        CompoundTag blocks = version == 3 ? root.getCompound("Blocks") : root;
        String dataKey = version == 3 ? "Data" : "BlockData";
        if (!blocks.contains("Palette") || !blocks.contains(dataKey)) throw new IOException("Missing palette or block data.");
        Map<Integer, String> palette = new LinkedHashMap<>();
        CompoundTag p = blocks.getCompound("Palette");
        for (String state : p.getAllKeys()) {
            int index = p.getInt(state);
            if (index < 0 || palette.put(index, normalize(state)) != null) throw new IOException("Invalid palette index.");
        }
        if (palette.isEmpty()) throw new IOException("Empty palette.");
        return new SchematicData(w, h, l, decodeVarInts(blocks.getByteArray(dataKey), volume, palette), palette);
    }

    private static SchematicData readLitematic(CompoundTag root) throws IOException {
        if (!root.contains("Regions")) throw new IOException("Litematic has no Regions compound.");
        CompoundTag regions = root.getCompound("Regions");
        if (regions.isEmpty()) throw new IOException("Litematic has no regions.");
        Map<String, Region> decoded = new LinkedHashMap<>();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for (String name : regions.getAllKeys()) {
            CompoundTag r = regions.getCompound(name);
            int[] pos=r.getIntArray("Position"), size=r.getIntArray("Size");
            if (pos.length!=3 || size.length!=3) throw new IOException("Invalid region geometry: "+name);
            int w=Math.abs(size[0]), h=Math.abs(size[1]), l=Math.abs(size[2]);
            if (w==0 || h==0 || l==0) continue;
            int x=size[0]<0?pos[0]-w+1:pos[0], y=size[1]<0?pos[1]-h+1:pos[1], z=size[2]<0?pos[2]-l+1:pos[2];
            Region data=decodeRegion(r,w,h,l,x,y,z);
            decoded.put(name,data);
            minX=Math.min(minX,x); minY=Math.min(minY,y); minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,x+w-1); maxY=Math.max(maxY,y+h-1); maxZ=Math.max(maxZ,z+l-1);
        }
        if(decoded.isEmpty()) throw new IOException("Litematic has no non-empty regions.");
        int w=maxX-minX+1,h=maxY-minY+1,l=maxZ-minZ+1,volume=checkedVolume(w,h,l);
        String[] states=new String[volume]; Arrays.fill(states,"minecraft:air");
        Map<Integer,String> palette=new LinkedHashMap<>(); int next=0;
        for(Region r:decoded.values()) {
            for(String state:r.palette) {
                if(!palette.containsValue(state)) palette.put(next++,state);
            }
            for(int y=0;y<r.h;y++) for(int z=0;z<r.l;z++) for(int x=0;x<r.w;x++) {
                int src=x+z*r.w+y*r.w*r.l;
                int dx=r.x-minX+x,dy=r.y-minY+y,dz=r.z-minZ+z;
                states[dx+dz*w+dy*w*l]=r.states[src];
            }
        }
        return new SchematicData(w,h,l,states,palette);
    }

    private static Region decodeRegion(CompoundTag r,int w,int h,int l,int x,int y,int z) throws IOException {
        int volume=checkedVolume(w,h,l);
        ListTag list=r.getList("BlockStatePalette",Tag.TAG_COMPOUND);
        if(list.isEmpty()) throw new IOException("Empty Litematica block-state palette.");
        String[] palette=new String[list.size()];
        for(int i=0;i<list.size();i++) {
            CompoundTag entry=list.getCompound(i);
            String state=normalize(entry.getString("Name"));
            CompoundTag props=entry.getCompound("Properties");
            if(!props.isEmpty()) {
                StringBuilder b=new StringBuilder(state).append('['); boolean first=true;
                for(String key:props.getAllKeys()) { if(!first)b.append(','); b.append(key).append('=').append(props.get(key).getAsString()); first=false; }
                state=b.append(']').toString();
            }
            palette[i]=state;
        }
        long[] data=r.getLongArray("BlockStates");
        int bits=Math.max(2,32-Integer.numberOfLeadingZeros(palette.length-1));
        int perLong=64/bits; long mask=(1L<<bits)-1L;
        if(data.length<(volume+perLong-1)/perLong) throw new IOException("Truncated Litematica BlockStates.");
        String[] states=new String[volume];
        for(int i=0;i<volume;i++) {
            int index=(int)((data[i/perLong]>>>((i%perLong)*bits))&mask);
            if(index>=palette.length) throw new IOException("Invalid Litematica palette reference.");
            states[i]=palette[index];
        }
        return new Region(w,h,l,x,y,z,palette,states);
    }

    private static int dimension(CompoundTag t,String key) throws IOException {
        int value=t.getShort(key)&0xFFFF;
        if(value<1) throw new IOException("Invalid dimension: "+key);
        return value;
    }
    private static int checkedVolume(int w,int h,int l) throws IOException {
        long n=(long)w*h*l;
        if(w<1||h<1||l<1||n>MAX_BLOCKS) throw new IOException("Schematic exceeds "+MAX_BLOCKS+" blocks or has invalid dimensions.");
        return (int)n;
    }
    private static String normalize(String s) { if(s==null||s.isBlank())return "minecraft:air"; return s.indexOf(':')>=0?s:"minecraft:"+s; }
    private static String[] decodeVarInts(byte[] bytes,int count,Map<Integer,String> palette) throws IOException {
        String[] out=new String[count]; int cursor=0;
        for(int i=0;i<count;i++) {
            int value=0,shift=0; boolean done=false;
            while(cursor<bytes.length&&shift<=28) {
                int b=bytes[cursor++]&255; value|=(b&127)<<shift;
                if((b&128)==0){done=true;break;} shift+=7;
            }
            if(!done)throw new IOException("Truncated Sponge block data.");
            String state=palette.get(value); if(state==null)throw new IOException("Missing palette entry "+value);
            out[i]=state;
        }
        if(cursor!=bytes.length)throw new IOException("Unexpected trailing block data.");
        return out;
    }
    private record Region(int w,int h,int l,int x,int y,int z,String[] palette,String[] states) {}
}