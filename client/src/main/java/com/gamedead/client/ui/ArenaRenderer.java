package com.gamedead.client.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.gamedead.game.ArenaMap;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.*;
import java.util.*;

final class ArenaRenderer implements Disposable {
    static final Color[] COLORS={Color.valueOf("9dccff"),Color.valueOf("c6a2ff"),Color.valueOf("91d6a0"),Color.valueOf("f0cc7e")};
    private final Texture texture;
    private final Map<String,TextureRegion> regions=new HashMap<>();
    private final Map<UUID,Position> positions=new HashMap<>();
    private final SpriteBatch batch=new SpriteBatch();
    private final ShapeRenderer shapes=new ShapeRenderer();
    private final FitViewport viewport=new FitViewport(ArenaMap.WIDTH,ArenaMap.HEIGHT);
    private final BitmapFont font;
    private final GlyphLayout text=new GlyphLayout();
    private WorldSnapshot world;
    private float time;

    ArenaRenderer(BitmapFont font){
        this.font=font;
        texture=new Texture(Gdx.files.internal("game/0x72/dungeon.png"));
        texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
        for(String line:Gdx.files.internal("game/0x72/tile-list.txt").readString("UTF-8").split("\\R")){
            if(line.isBlank())continue;String[] p=line.split("\\s+");
            regions.put(p[0],new TextureRegion(texture,Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4])));
        }
        resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
    }
    void accept(WorldSnapshot next){
        if(world==null || !world.worldId().equals(next.worldId()))positions.clear();
        world=next;
        positions.keySet().removeIf(id->next.players().stream().noneMatch(p->p.accountId().equals(id)));
        for(var player:next.players()){
            Position p=positions.computeIfAbsent(player.accountId(),ignored->new Position((float)player.x(),(float)player.y()));
            if(Math.abs(player.x()-p.x)>30 || Math.abs(player.y()-p.y)>30){p.x=(float)player.x();p.y=(float)player.y();}
            if(Math.abs(player.x()-p.targetX)>0.1)p.left=player.x()<p.targetX;
            p.targetX=(float)player.x();p.targetY=(float)player.y();p.moving=player.moving();
        }
    }
    void draw(float delta,RoomSnapshot room){
        if(world==null)return;
        time+=delta;viewport.apply();batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();batch.setColor(Color.WHITE);
        for(int y=0;y<ArenaMap.ROWS;y++)for(int x=0;x<ArenaMap.COLUMNS;x++){
            draw("floor_"+(1+(x*17+y*5)%8),x*16,y*16,16,16);
            if(ArenaMap.wall(x,y))draw(y==ArenaMap.ROWS-1?"wall_top_mid":"wall_mid",x*16,y*16,16,16);
        }
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(var member:room.members()){
            Position p=positions.get(member.accountId());if(p==null)continue;
            float alpha=1-(float)Math.exp(-delta*20);p.x+=(p.targetX-p.x)*alpha;p.y+=(p.targetY-p.y)*alpha;
            Color color=COLORS[Math.floorMod(member.color(),COLORS.length)];
            shapes.setColor(color.r,color.g,color.b,0.35f);shapes.ellipse(p.x-7,p.y-3,14,6,16);
        }
        shapes.end();batch.begin();
        var ordered=room.members().stream().filter(m->positions.containsKey(m.accountId())).sorted(Comparator.comparingDouble(m->-positions.get(m.accountId()).y)).toList();
        for(var member:ordered){
            Position p=positions.get(member.accountId());int frame=(int)(time*(p.moving?10:5))%4;
            batch.setColor(member.connected()?Color.WHITE:Color.GRAY);
            String animation="wizzard_m_"+(p.moving?"run":"idle")+"_anim_f"+frame;
            draw(animation,p.x+(p.left?8:-8),p.y-4,p.left?-16:16,28);
            font.setColor(COLORS[Math.floorMod(member.color(),COLORS.length)]);
            text.setText(font,member.displayName());font.draw(batch,text,p.x-text.width/2,p.y+33);
        }
        batch.setColor(Color.WHITE);batch.end();font.setColor(Color.WHITE);
    }
    private void draw(String key,float x,float y,float width,float height){batch.draw(regions.get(key),x,y,width,height);}
    void resize(int width,int height){viewport.update(width,height,true);}
    @Override public void dispose(){texture.dispose();batch.dispose();shapes.dispose();}
    private static final class Position {
        float x,y,targetX,targetY;boolean moving,left;
        Position(float x,float y){this.x=targetX=x;this.y=targetY=y;}
    }
}
