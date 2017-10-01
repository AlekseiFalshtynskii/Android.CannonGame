package com.example.aleksei.androidcannongame.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.example.aleksei.androidcannongame.CannonView;

public class GameElement {
    protected CannonView view;
    protected Paint paint = new Paint();
    protected Rect shape;
    private float velocityY;
    private int soundId;

    public GameElement(CannonView view, int color, int soundId, int x, int y,
                       int width, int length, float velocityY) {
        this.view = view;
        paint.setColor(color);
        shape = new Rect(x, y, x + width, y + length);
        this.soundId = soundId;
        this.velocityY = velocityY;
    }

    public void update(double interval) {
        // Обновление вертикальной позиции
        shape.offset(0, (int) (velocityY * interval));

        // Если GameElement сталкивается со стеной, изменить направление на противоложное
        if (shape.top < 0 && velocityY < 0 || shape.bottom > view.getScreenHeight() && velocityY > 0) {
            velocityY *= -1;
        }
    }

    public void draw(Canvas canvas) {
        canvas.drawRect(shape, paint);
    }

    public void playSound() {
        view.playSound(soundId);
    }
}
