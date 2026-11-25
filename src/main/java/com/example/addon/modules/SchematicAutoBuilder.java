package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.schematic.SchematicData;
import com.example.addon.schematic.SchematicManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Schematic printer using normal client block interaction. It does not send
 * custom packets, bypass server checks, or place blocks outside vanilla range.
 */
public class SchematicAutoBuilder extends Module {
    private final SettingGroup sgBuild=settings.getDefaultGroup();
    private final Setting<Boolean> autoPlace=sgBuild.add(new BoolSetting.Builder().name("printer").description("Place schematic blocks through normal Minecraft interaction.").defaultValue(false).build());
    private final Setting<Boolean> ghost=sgBuild.add(new BoolSetting.Builder().name("ghost-preview").description("Render a nearby, bounded ghost preview.").defaultValue(true).build());
    private final Setting<Boolean> useAnchor=sgBuild.add(new BoolSetting.Builder().name("use-anchor").description("Use the origin set by .ljct-schem anchor.").defaultValue(true).build());
    private final Setting<Integer> originX=sgBuild.add(new IntSetting.Builder().name("origin-x").defaultValue(0).range(-30000000,30000000).visible(()->!useAnchor.get()).build());
    private final Setting<Integer> originY=sgBuild.add(new IntSetting.Builder().name("origin-y").defaultValue(64).range(-64,320).visible(()->!useAnchor.get()).build());
    private final Setting<Integer> originZ=sgBuild.add(new IntSetting.Builder().name("origin-z").defaultValue(0).range(-30000000,30000000).visible(()->!useAnchor.get()).build());
    private final Setting<Integer> rotation=sgBuild.add(new IntSetting.Builder().name("rotation").description("Rotation in degrees: 0, 90, 180 or 270.").defaultValue(0).range(0,270).sliderMin(0).sliderMax(270).build());
    private final Setting<Integer> range=sgBuild.add(new IntSetting.Builder().name("range").defaultValue(4).range(1,6).build());
    private final Setting<Integer> blocksPerTick=sgBuild.add(new IntSetting.Builder().name("blocks-per-tick").defaultValue(1).range(1,2).build());
    private final Setting<Integer> placeDelay=sgBuild.add(new IntSetting.Builder().name("place-delay-ticks").defaultValue(2).range(0,20).build());
    private final Setting<Boolean> stopMissing=sgBuild.add(new BoolSetting.Builder().name("pause-on-missing-block").defaultValue(true).build());
    private final Setting<Integer> previewDistance=sgBuild.add(new IntSetting.Builder().name("preview-distance").defaultValue(32).range(8,96).visible(ghost::get).build());
    private final Setting<Integer> previewLimit=sgBuild.add(new IntSetting.Builder().name("preview-limit").defaultValue(180).range(16,512).visible(ghost::get).build());
    private final Setting<SettingColor> ghostColor=sgBuild.add(new ColorSetting.Builder().name("ghost-color").defaultValue(new SettingColor(90,210,255,105)).visible(ghost::get).build());

    private final List<BlockPos> targets=new ArrayList<>();
    private SchematicData plannedFor;
    private int cursor,delayCounter,placed,total;
    private String status="Idle";
    public static volatile String hudText="Schematic: not loaded";

    public SchematicAutoBuilder(){super(AddonTemplate.CATEGORY,"schematic-auto-builder","Loads a schematic preview and optionally prints blocks through vanilla interaction.");}

    @Override public void onActivate(){resetPlan();}
    @Override public void onDeactivate(){targets.clear();plannedFor=null;cursor=0;delayCounter=0;status="OFF";updateHud();}

    private BlockPos origin(){
        BlockPos anchor=useAnchor.get()?SchematicManager.getAnchor():null;
        return anchor!=null?anchor:new BlockPos(originX.get(),originY.get(),originZ.get());
    }
    private int normalizedRotation(){int r=((rotation.get()%360)+360)%360;return ((r+45)/90*90)%360;}
    private BlockPos worldPos(int x,int y,int z){
        int r=normalizedRotation(); int rx=x,rz=z;
        if(r==90){rx=z;rz=-x;}
        else if(r==180){rx=-x;rz=-z;}
        else if(r==270){rx=-z;rz=x;}
        return origin().offset(rx,y,rz);
    }
    private void resetPlan(){
        targets.clear();cursor=0;placed=0;total=0;delayCounter=0;
        plannedFor=SchematicManager.getCurrent();
        if(plannedFor==null){status="No schematic";updateHud();return;}
        for(int y=0;y<plannedFor.getHeight();y++)for(int z=0;z<plannedFor.getLength();z++)for(int x=0;x<plannedFor.getWidth();x++){
            if(!SchematicData.isAir(plannedFor.getBlockState(x,y,z))){targets.add(worldPos(x,y,z));total++;}
        }
        status="Ready";updateHud();
    }

    @EventHandler private void onTick(TickEvent.Pre event){
        if(mc.player==null||mc.level==null)return;
        if(SchematicManager.getCurrent()!=plannedFor)resetPlan();
        if(plannedFor==null||!autoPlace.get()||cursor>=targets.size())return;
        if(delayCounter>0){delayCounter--;return;}
        int actions=0;
        while(actions<blocksPerTick.get()&&cursor<targets.size()){
            BlockPos target=targets.get(cursor);
            String expected=plannedFor.getBlockState(localX(target),localY(target),localZ(target));
            var existing=mc.level.getBlockState(target);
            if(BuiltInRegistries.BLOCK.getKey(existing.getBlock()).toString().equals(SchematicData.getBlockId(expected))){
                cursor++;continue;
            }
            if(!mc.level.hasChunkAt(target)){status="Waiting for chunk";updateHud();return;}
            if(!existing.isAir()&&!existing.canBeReplaced()){status="Blocked at "+target.toShortString();updateHud();return;}
            if(mc.player.distanceToSqr(Vec3.atCenterOf(target))>range.get()*range.get()){status="Out of range";updateHud();return;}
            int slot=findHotbarSlot(SchematicData.getBlockId(expected));
            if(slot<0){status="Missing "+SchematicData.getBlockId(expected);updateHud();if(stopMissing.get())return;cursor++;continue;}
            mc.player.getInventory().selected=slot;
            BlockHitResult hit=findSupport(target);
            if(hit==null){status="No valid support face";updateHud();return;}
            // Use the same client interaction path as a manual right-click.
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
            actions++;cursor++;placed++;delayCounter=placeDelay.get();
            if(delayCounter>0)break;
        }
        status=cursor>=targets.size()?"Complete":"Printing";
        updateHud();
    }

    private int localX(BlockPos p){return p.getX()-origin().getX();}
    private int localY(BlockPos p){return p.getY()-origin().getY();}
    private int localZ(BlockPos p){return p.getZ()-origin().getZ();}
    private int findHotbarSlot(String blockId){
        for(int i=0;i<9;i++){var stack=mc.player.getInventory().getItem(i);if(!stack.isEmpty()&&stack.getItem() instanceof BlockItem item&&BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString().equals(blockId))return i;}
        return -1;
    }
    private BlockHitResult findSupport(BlockPos target){
        for(Direction direction:Direction.values()){
            BlockPos support=target.relative(direction);
            var state=mc.level.getBlockState(support);
            if(state.isAir()||state.canBeReplaced())continue;
            Direction face=direction.getOpposite();
            if(!state.isFaceSturdy(mc.level,support,face))continue;
            return new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX()*0.5,face.getStepY()*0.5,face.getStepZ()*0.5),face,support,false);
        }
        return null;
    }

    @EventHandler private void onRender3D(Render3DEvent event){
        if(!ghost.get()||mc.player==null||mc.level==null||plannedFor==null)return;
        double maxSq=previewDistance.get()*previewDistance.get();int drawn=0,scan=Math.min(targets.size(),Math.max(previewLimit.get()*8,previewLimit.get()));
        SettingColor color=ghostColor.get();
        for(int i=cursor;i<scan&&drawn<previewLimit.get();i++){
            BlockPos p=targets.get(i);
            if(mc.player.distanceToSqr(Vec3.atCenterOf(p))>maxSq)continue;
            event.renderer.box(new AABB(p).inflate(0.001),color,color,ShapeMode.Lines,0);drawn++;
        }
    }
    private void updateHud(){
        int remaining=Math.max(0,total-placed);
        int percent=total==0?0:Math.min(100,placed*100/total);
        hudText="Schematic "+percent+"% | "+remaining+" left | "+status;
    }
}