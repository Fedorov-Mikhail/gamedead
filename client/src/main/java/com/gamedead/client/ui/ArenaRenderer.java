package com.gamedead.client.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.math.Vector2;
import com.gamedead.game.ArenaMap;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.*;
import java.util.*;

final class ArenaRenderer implements Disposable {
    static final Color[] COLORS={Color.valueOf("9dccff"),Color.valueOf("c6a2ff"),Color.valueOf("91d6a0"),Color.valueOf("f0cc7e")};
    private final Texture texture;
    private final Map<String,TextureRegion> regions=new HashMap<>();
    private final Map<UUID,Position> positions=new HashMap<>();
    private final Map<Long,Position> enemyPositions=new HashMap<>();
    private final SpriteBatch batch=new SpriteBatch();
    private final ShapeRenderer shapes=new ShapeRenderer();
    private final FitViewport viewport=new FitViewport(ArenaMap.WIDTH,512);
    private final BitmapFont font;
    private final GlyphLayout text=new GlyphLayout();
    private WorldSnapshot world;
    private float time;
    Vector2 aim(UUID player,int screenX,int screenY){
        Position position=positions.get(player);
        Vector2 point=pointer(screenX,screenY);
        if(position==null || point==null)return new Vector2();
        return point.sub(position.x,position.y).nor();
    }
    private Vector2 pointer(int screenX,int screenY){
        int bottomY=Gdx.graphics.getHeight()-screenY;
        if(screenX<viewport.getScreenX() || screenX>=viewport.getScreenX()+viewport.getScreenWidth()
                || bottomY<viewport.getScreenY() || bottomY>=viewport.getScreenY()+viewport.getScreenHeight())return null;
        Vector2 point=viewport.unproject(new Vector2(screenX,screenY));
        return point.x>=0 && point.x<ArenaMap.WIDTH && point.y>=0 && point.y<ArenaMap.HEIGHT?point:null;
    }

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
        if(world==null || !world.worldId().equals(next.worldId())){positions.clear();enemyPositions.clear();}
        world=next;
        positions.keySet().removeIf(id->next.players().stream().noneMatch(p->p.accountId().equals(id)));
        for(var player:next.players()){
            Position p=positions.computeIfAbsent(player.accountId(),ignored->new Position((float)player.x(),(float)player.y()));
            if(Math.abs(player.x()-p.x)>30 || Math.abs(player.y()-p.y)>30){p.x=(float)player.x();p.y=(float)player.y();}
            if(Math.abs(player.x()-p.targetX)>0.1)p.left=player.x()<p.targetX;
            p.targetX=(float)player.x();p.targetY=(float)player.y();p.moving=player.moving();
            if(player.health()<p.health)p.flash=0.2f;
            p.health=player.health();p.revive=(float)player.reviveProgress();
        }
        enemyPositions.keySet().removeIf(id->next.enemies().stream().noneMatch(e->e.id()==id));
        for(var enemy:next.enemies()){
            Position p=enemyPositions.computeIfAbsent(enemy.id(),ignored->new Position((float)enemy.x(),(float)enemy.y()));
            p.left=enemy.x()<p.targetX;p.targetX=(float)enemy.x();p.targetY=(float)enemy.y();p.health=enemy.health();
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
            smooth(p,delta);
            Color color=COLORS[Math.floorMod(member.color(),COLORS.length)];
            shapes.setColor(color.r,color.g,color.b,0.35f);shapes.ellipse(p.x-7,p.y-3,14,6,16);
            shapes.setColor(0.12f,0.12f,0.15f,1);shapes.rect(p.x-10,p.y+27,20,3);
            shapes.setColor(p.health>0?Color.valueOf("91d6a0"):Color.valueOf("ffa99d"));
            shapes.rect(p.x-10,p.y+27,20*p.health/100f,3);
            if(p.revive>0){shapes.setColor(COLORS[0]);shapes.rect(p.x-12,p.y-7,24*p.revive,2);}
        }
        for(Position p:enemyPositions.values()){
            smooth(p,delta);
            shapes.setColor(0.15f,0.07f,0.08f,1);shapes.rect(p.x-6,p.y+14,12,2);
            shapes.setColor(0.9f,0.32f,0.3f,1);shapes.rect(p.x-6,p.y+14,12*p.health/60f,2);
        }
        for(var shot:world.projectiles()){
            shapes.setColor(0.3f,0.6f,1,0.25f);shapes.circle((float)shot.x(),(float)shot.y(),4,12);
            shapes.setColor(0.75f,0.9f,1,1);shapes.circle((float)shot.x(),(float)shot.y(),2,12);
        }
        Vector2 cursor=pointer(Gdx.input.getX(),Gdx.input.getY());
        if(cursor!=null && world.phase().equals("RUNNING")){
            shapes.setColor(COLORS[0]);shapes.rect(cursor.x-4,cursor.y,8,0.7f);shapes.rect(cursor.x,cursor.y-4,0.7f,8);
        }
        shapes.end();batch.begin();
        for(Position p:enemyPositions.values()){
            batch.setColor(Color.WHITE);
            int frame=world.phase().equals("RUNNING")?(int)(time*8)%4:0;
            draw("tiny_zombie_run_anim_f"+frame,p.x+(p.left?8:-8),p.y-4,p.left?-16:16,16);
        }
        var ordered=room.members().stream().filter(m->positions.containsKey(m.accountId())).sorted(Comparator.comparingDouble(m->-positions.get(m.accountId()).y)).toList();
        for(var member:ordered){
            Position p=positions.get(member.accountId());
            boolean moving=p.moving && p.health>0 && world.phase().equals("RUNNING");
            int frame=(int)(time*(moving?10:5))%4;
            batch.setColor(p.flash>0?Color.SALMON:member.connected() && p.health>0?Color.WHITE:Color.GRAY);
            String animation="wizzard_m_"+(moving?"run":"idle")+"_anim_f"+frame;
            draw(animation,p.x+(p.left?8:-8),p.y-4,p.left?-16:16,p.health>0?28:10);
            font.setColor(COLORS[Math.floorMod(member.color(),COLORS.length)]);
            text.setText(font,member.displayName());font.draw(batch,text,p.x-text.width/2,p.y+41);
            if(p.health==0){font.setColor(UiTheme.ERROR);text.setText(font,"Помощь [E]");font.draw(batch,text,p.x-text.width/2,p.y+23);}
        }
        batch.setColor(Color.WHITE);batch.end();font.setColor(Color.WHITE);
    }
    private void draw(String key,float x,float y,float width,float height){batch.draw(regions.get(key),x,y,width,height);}
    private void smooth(Position p,float delta){float alpha=1-(float)Math.exp(-delta*20);p.x+=(p.targetX-p.x)*alpha;p.y+=(p.targetY-p.y)*alpha;p.flash=Math.max(0,p.flash-delta);}
    void resize(int width,int height){viewport.update(width,height,false);viewport.getCamera().position.set(ArenaMap.WIDTH/2f,ArenaMap.HEIGHT/2f,0);viewport.getCamera().update();}
    @Override public void dispose(){texture.dispose();batch.dispose();shapes.dispose();}
    private static final class Position {
        float x,y,targetX,targetY,flash,revive;int health=100;boolean moving,left;
        Position(float x,float y){this.x=targetX=x;this.y=targetY=y;}
    }
}
