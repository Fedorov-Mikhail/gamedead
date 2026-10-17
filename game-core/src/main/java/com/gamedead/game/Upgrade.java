package com.gamedead.game;

public enum Upgrade {
    POWER(5,"Сила заклинаний","+5 к урону снаряда"),
    HASTE(3,"Скорость атаки","Выстрелы на 0.05 с чаще"),
    MULTISHOT(2,"Дополнительный снаряд","Ещё один снаряд за выстрел"),
    VITALITY(4,"Выносливость","+25 максимального здоровья"),
    SWIFTNESS(3,"Скорость движения","+10% начальной скорости"),
    DASH(3,"Восстановление рывка","Рывок восстанавливается на 0.3 с быстрее");

    private final int maxRank;
    private final String title,description;
    Upgrade(int maxRank,String title,String description){this.maxRank=maxRank;this.title=title;this.description=description;}
    public int maxRank(){return maxRank;}
    public String title(){return title;}
    public String description(){return description;}
}
