package com.boogeraids.combatai.game;

public enum GameState {
    IDLE,       // 게임 없음
    INTRO,      // 스테이지 타이틀 카드, 캐릭터 정지
    PLAYING,    // 플레이 중
    DYING,      // 사망 연출
    CLEARING,   // 클리어 연출
    VICTORY     // 전체 클리어
}
