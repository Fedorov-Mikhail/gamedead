package com.gamedead.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.gamedead.client.room.*;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.PlayerSnapshot;
import com.gamedead.game.Upgrade;
import com.gamedead.game.Difficulty;
import java.util.UUID;

/** Scene2D presentation only; networking and membership state stay in RoomController. */
final class RoomScreen {
    private final Stage stage;
    private final UiTheme theme;
    private final RoomController controller;
    private UUID account;
    private String layoutKey="",draft="";
    private TextField codeField;
    private Label combatStatus;
    RoomScreen(Stage stage,UiTheme theme,RoomController controller){this.stage=stage;this.theme=theme;this.controller=controller;}
    void show(RoomState state,UUID account){
        this.account=account;
        if(combatStatus!=null && state.world()!=null)combatStatus.setText(combatText(state));
        PlayerSnapshot player=self(state);
        String key=state.connected()+"/"+state.busy()+"/"+(state.room()==null?"none":state.room().version())+"/"+state.message()+"/"+arena(state)
                +"/"+(state.world()==null?"":state.world().phase()+"/"+state.world().saveState())
                +"/"+(player==null?"":player.choiceId()+"/"+player.pendingUpgrades())+"/"+controller.upgradePending();
        if(key.equals(layoutKey))return;layoutKey=key;
        if(codeField!=null){draft=codeField.getText();codeField=null;}
        stage.clear();combatStatus=null;
        Table root=new Table();root.setFillParent(true);root.pad(24);stage.addActor(root);
        if(arena(state)){hud(root,state);return;}
        Table card=new Table();card.background(theme.skin.getDrawable("panel"));card.pad(26);card.defaults().growX().padBottom(10);
        card.add(label(state.room()==null?"Собрать отряд":"Твой отряд","heading")).row();
        if(state.room()==null){
            card.add(label("Создай комнату или введи код приглашения друга.","small")).row();
            card.add(button("Создать комнату","room-create",()->controller.action("create",null),state)).height(48).row();
            card.add(label("Код комнаты","small")).left().row();
            codeField=new TextField(draft,theme.skin);codeField.setName("room-code");codeField.setDisabled(!state.connected() || state.busy());
            codeField.setTextFieldListener((field,c)->{if(c=='\r' || c=='\n')controller.action("join",new JoinRoomRequest(field.getText()));});
            card.add(codeField).height(44).row();
            card.add(button("Присоединиться","room-join",()->controller.action("join",new JoinRoomRequest(codeField.getText())),state)).height(48).row();
        }else{
            RoomSnapshot room=state.room();
            Label invite=label("КОД: "+room.code(),"heading");invite.setColor(UiTheme.GOLD);card.add(invite).row();
            card.add(button("Копировать код","room-copy",()->Gdx.app.getClipboard().setContents(room.code()),state)).height(40).row();
            for(RoomMember member:room.members()){
                Table row=new Table();
                Label name=label(member.displayName()+(member.accountId().equals(room.ownerId())?" · владелец":""),"default");
                name.setColor(ArenaRenderer.COLORS[Math.floorMod(member.color(),4)]);name.setWrap(true);
                row.add(name).width(440).left();
                row.add(label(!member.connected()?"Нет связи":member.ready()?"Готов":"Не готов","small")).width(180).right();
                card.add(row).minHeight(36).row();
            }
            card.add(label("Участников: "+room.members().size()+" / 4","small")).row();
            card.add(label("Сложность: "+Difficulty.valueOf(room.difficulty()).title(),"small")).row();
            if(room.status().equals("LOBBY")){
                if(room.ownerId().equals(account)){
                    Table choices=new Table();
                    for(Difficulty difficulty:Difficulty.values()){
                        TextButton choice=button(difficulty.title(),"difficulty-"+difficulty.name(),()->controller.action("difficulty",new DifficultyRequest(difficulty.name())),state);
                        choice.setDisabled(choice.isDisabled() || difficulty.name().equals(room.difficulty()));
                        choices.add(choice).uniformX().growX().height(40).pad(3);
                    }
                    card.add(choices).growX().row();
                }
                var self=room.members().stream().filter(m->m.accountId().equals(account)).findFirst().orElse(null);
                boolean ready=self!=null && self.ready();
                card.add(button(ready?"Не готов":"Готов","room-ready",()->controller.action("ready",new ReadyRequest(!ready)),state)).height(46).row();
                if(room.ownerId().equals(account)){
                    TextButton start=button("Начать забег","room-start",()->controller.action("start",null),state);
                    start.setDisabled(start.isDisabled() || room.members().size()<2 || room.members().stream().anyMatch(m->!m.ready() || !m.connected()));
                    card.add(start).height(46).row();
                }
                card.add(label("Для старта нужны минимум два подключённых игрока.\nКаждый участник нажимает «Готов».","small")).row();
                card.add(label("После старта будет 10 секунд до первого натиска.","small")).row();
            }else card.add(label(room.status().equals("STARTING")?"Готовим забег…":"Забег идёт. Ожидаем состояние арены…","small")).row();
            card.add(button("Выйти из комнаты","room-leave",()->controller.action("leave",null),state)).height(44).row();
        }
        if(!state.connected())card.add(buttonAlways("Подключиться","room-connect",controller::connect,state.busy())).height(44).row();
        Label status=label(state.message(),"small");status.setName("room-status");status.setWrap(true);status.setColor(state.error()?UiTheme.ERROR:UiTheme.MUTED);
        card.add(status).minHeight(35).row();
        if(state.room()==null || !state.connected())card.add(buttonAlways("К профилю","room-back",controller::back,false)).height(44).row();
        ScrollPane scroll=new ScrollPane(card,theme.skin);scroll.setScrollingDisabled(true,false);scroll.setFlickScroll(false);
        root.add(scroll).width(760).maxHeight(660).expandY().fillY();stage.setScrollFocus(scroll);
        if(codeField!=null && state.connected())stage.setKeyboardFocus(codeField);
    }
    static boolean arena(RoomState state){return state.visible() && state.connected() && state.room()!=null && state.room().status().equals("PLAYING") && state.world()!=null;}
    void invalidate(){layoutKey="";}
    private void hud(Table root,RoomState state){
        boolean running=state.world().phase().equals("RUNNING");
        root.top().pad(16);Table bar=new Table();bar.background(theme.skin.getDrawable("panel"));bar.pad(10);
        combatStatus=label("","small");combatStatus.setName("combat-status");
        combatStatus.setText(combatText(state));combatStatus.setWrap(true);
        bar.add(combatStatus).minWidth(0).expandX().fillX().left();
        if(state.room().ownerId().equals(account))bar.add(button(running?"Прервать бой":"Вернуться в комнату","arena-stop",()->controller.action("stop",null),state)).width(245).height(44).padLeft(10);
        bar.add(button("Выйти","arena-leave",()->controller.action("leave",null),state)).width(120).height(44).padLeft(10);
        root.add(bar).growX().row();
        if(!running){
            Table result=new Table();result.background(theme.skin.getDrawable("panel"));result.pad(28);
            String title=switch(state.world().phase()){case "WON"->"Отряд выстоял!";case "LOST"->"Отряд пал";default->"Бой прерван";};
            Label heading=label(title,"heading");heading.setName("combat-result");result.add(heading).padBottom(14).row();
            result.add(label("Уничтожено врагов: "+state.world().kills(),"default")).row();
            String saved=switch(state.world().saveState()){
                case "SAVED"->"Результат сохранён в истории забегов.";
                case "RETRYING"->"Сохранение не удалось. Сервер повторяет попытку…";
                default->"Сохраняем результат и опыт аккаунта…";
            };
            Label save=label(saved,"small");save.setName("result-save");result.add(save).padTop(12).row();
            result.add(label("Владелец может вернуть отряд в комнату для нового забега.","small")).padTop(12).row();
            root.add(result).expandY().center().row();
        }else{
            root.add().expandY().row();
            PlayerSnapshot self=self(state);
            if(self!=null && self.pendingUpgrades()>0){
                Table upgrades=new Table();upgrades.setName("upgrade-panel");upgrades.background(theme.skin.getDrawable("panel"));upgrades.pad(12);
                Label title=label("Выбери улучшение · осталось: "+self.pendingUpgrades()+" · бой продолжается","small");title.setName("upgrade-pending");
                upgrades.add(title).colspan(3).left().padBottom(8).row();
                for(int i=0;i<self.upgradeOffers().size();i++){
                    int index=i;Upgrade upgrade=Upgrade.valueOf(self.upgradeOffers().get(i));
                    int rank=self.upgrades().getOrDefault(upgrade.name(),0)+1;
                    TextButton choice=buttonAlways((i+1)+". "+upgrade.title()+" ("+rank+"/"+upgrade.maxRank()+")\n"+upgrade.description(),
                            "upgrade-"+i,()->controller.chooseUpgrade(index),controller.upgradePending());
                    choice.getLabel().setWrap(true);
                    upgrades.add(choice).uniformX().growX().height(132).pad(4);
                }
                root.add(upgrades).growX().padBottom(12).row();
            }
        }
        root.add(label(state.error()?state.message():"WASD — движение   ЛКМ — атака   Shift — рывок   E — помощь   1–3 — улучшение","small")).left();stage.setKeyboardFocus(null);
    }
    private PlayerSnapshot self(RoomState state){return state.world()==null?null:state.world().players().stream().filter(p->p.accountId().equals(account)).findFirst().orElse(null);}
    private String combatText(RoomState state){
        int seconds=(state.world().remainingTicks()+19)/20;PlayerSnapshot self=self(state);
        return "Время: "+String.format("%02d:%02d",seconds/60,seconds%60)+" · "+(state.world().bossSpawned()?"Финальный босс":"Волна "+state.world().wave())
                +" · Здоровье: "+(self==null?0:self.health())+" / "+(self==null?100:self.maxHealth())
                +" · Убито: "+state.world().kills()+"\nУровень отряда: "+state.world().level()+" · Опыт: "+(state.world().level()==10?"МАКС.":state.world().experience()+" / "+state.world().requiredExperience());
    }
    private Label label(String text,String style){return new Label(text,theme.skin,style);}
    private TextButton button(String text,String name,Runnable action,RoomState state){return buttonAlways(text,name,action,!state.connected() || state.busy());}
    private TextButton buttonAlways(String text,String name,Runnable action,boolean disabled){
        TextButton button=new TextButton(text,theme.skin);button.setName(name);button.setDisabled(disabled);
        button.addListener(new ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){if(!button.isDisabled())action.run();}});
        return button;
    }
}
