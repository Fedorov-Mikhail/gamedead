package com.gamedead.game;

public enum Difficulty {
    EASY("Лёгкая",0.75,0.65,0.8), NORMAL("Обычная",1,1,1), HARD("Тяжёлая",1.45,1.35,1.25);

    private final String title;
    private final double healthMultiplier,damageMultiplier,spawnMultiplier;
    Difficulty(String title,double healthMultiplier,double damageMultiplier,double spawnMultiplier){
        this.title=title;this.healthMultiplier=healthMultiplier;
        this.damageMultiplier=damageMultiplier;this.spawnMultiplier=spawnMultiplier;
    }
    public String title(){return title;}
    public double healthMultiplier(){return healthMultiplier;}
    public double damageMultiplier(){return damageMultiplier;}
    public double spawnMultiplier(){return spawnMultiplier;}
}
