package com.boogeraids.combatai.game;

/** 통제권 플레이어의 현재 입력. PlayerInputEvent / 클릭 이벤트로 채워진다. */
public final class InputState {
    public volatile boolean left, right, jump;
    private boolean jumpLatch;
    private boolean leftClick, rightClick;
    private int lastLeftTick = -100, lastRightTick = -100;

    public void set(boolean left, boolean right, boolean jump) {
        if (jump && !this.jump) jumpLatch = true;
        this.left = left;
        this.right = right;
        this.jump = jump;
    }

    /** 틱 사이에 눌렀다 뗀 짧은 점프 입력도 놓치지 않기 위한 래치. */
    public boolean consumeJumpPress() {
        boolean j = jumpLatch;
        jumpLatch = false;
        return j;
    }

    /** @return 실제로 큐에 들어갔는지 (중복 이벤트 디바운스 3틱) */
    public boolean click(boolean isLeft, int now) {
        if (isLeft) {
            if (now - lastLeftTick < 3) return false;
            lastLeftTick = now;
            leftClick = true;
        } else {
            if (now - lastRightTick < 3) return false;
            lastRightTick = now;
            rightClick = true;
        }
        return true;
    }

    public boolean consumeLeft() {
        boolean b = leftClick;
        leftClick = false;
        return b;
    }

    public boolean consumeRight() {
        boolean b = rightClick;
        rightClick = false;
        return b;
    }

    public void reset() {
        left = right = jump = false;
        jumpLatch = leftClick = rightClick = false;
    }
}
