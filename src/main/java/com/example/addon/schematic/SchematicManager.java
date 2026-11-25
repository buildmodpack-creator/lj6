package com.example.addon.schematic;

import net.minecraft.core.BlockPos;

/** Shared loaded schematic, anchor and quarter-turn transform. */
public final class SchematicManager {
    private static SchematicData current;
    private static BlockPos anchor;
    private static int rotation;
    private SchematicManager() {}

    public static void setCurrent(SchematicData schematic){current=schematic;anchor=null;}
    public static SchematicData getCurrent(){return current;}
    public static void setAnchor(BlockPos pos){anchor=pos==null?null:pos.immutable();}
    public static BlockPos getAnchor(){return anchor;}
    public static void setRotation(int degrees){rotation=((degrees%360)+360)%360;}
    public static int getRotation(){return rotation;}
    public static BlockPos toWorldPos(int x,int y,int z,BlockPos origin){
        int rx=x,rz=z;
        switch(rotation){case 90->{rx=z;rz=-x;}case 180->{rx=-x;rz=-z;}case 270->{rx=-z;rz=x;}default->{}}
        return origin.offset(rx,y,rz);
    }
    public static BlockPos toLocalPos(BlockPos world,BlockPos origin){
        int dx=world.getX()-origin.getX(),dy=world.getY()-origin.getY(),dz=world.getZ()-origin.getZ();
        return switch(rotation){case 90->new BlockPos(-dz,dy,dx);case 180->new BlockPos(-dx,dy,-dz);case 270->new BlockPos(dz,dy,-dx);default->new BlockPos(dx,dy,dz);};
    }
    public static void clear(){current=null;anchor=null;rotation=0;}
}