package com.example.aleksei.androidcannongame;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.example.aleksei.androidcannongame.game.Blocker;
import com.example.aleksei.androidcannongame.game.Cannon;
import com.example.aleksei.androidcannongame.game.GameElement;
import com.example.aleksei.androidcannongame.game.Target;

import java.util.ArrayList;
import java.util.Random;

public class CannonView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CannonView";

    // Игровые константы
    public static final int MISS_PENALTY = 2; // штраф при промахе
    public static final int HIT_REWARD = 3; // прибавка при попадании

    // Константы для рисования пушки
    public static final double CANNON_BASE_RADIUS_PERCENT = 3.0 / 40;
    public static final double CANNON_BARREL_WIDTH_PERCENT = 3.0 / 40;
    public static final double CANNON_BARREL_LENGTH_PERCENT = 1.0 / 10;

    // Константы для рисования ядра
    public static final double CANNONBALL_RADIUS_PERCENT = 3.0 / 80;
    public static final double CANNONBALL_SPEED_PERCENT = 3.0 / 2;

    // Константы для рисования мишеней
    public static final double TARGET_WIDTH_PERCENT = 1.0 / 40;
    public static final double TARGET_LENGTH_PERCENT = 3.0 / 20;
    public static final double TARGET_FIRST_X_PERCENT = 3.0 / 5;
    public static final double TARGET_SPACING_PERCENT = 1.0 / 60;
    public static final double TARGET_PIECES = 9;
    public static final double TARGET_MIN_SPEED_PERCENT = 3.0 / 4;
    public static final double TARGET_MAX_SPEED_PERCENT = 6.0 / 4;

    // Константы для рисования блока
    public static final double BLOCKER_WIDTH_PERCENT = 1.0 / 40;
    public static final double BLOCKER_LENGTH_PERCENT = 1.0 / 4;
    public static final double BLOCKER_X_PERCENT = 1.0 / 2;
    public static final double BLOCKER_SPEED_PERCENT = 1.0;

    // Размер текста составляет 1/8 ширины экрана
    public static final double TEXT_SIZE_PERCENT = 1.0 / 18;

    private CannonThread cannonThread; // управляет циклом игры
    private Activity activity; // для отображения окна в потоке GUI
    private boolean dialogIsDisplayed = false;

    // Игровые объекты
    private Cannon cannon;
    private Blocker blocker;
    private ArrayList<Target> targets;

    private int screenWidth;
    private int screenHeight;

    // Переменные для игрового цикла
    private boolean gameOver;
    private double timeLeft;
    private int shotsFired;
    private double totalElapsedTime;

    // Управление звуком
    public static final int TARGET_SOUND_ID = 0;
    public static final int CANNON_SOUND_ID = 1;
    public static final int BLOCKER_SOUND_ID = 2;
    private SoundPool soundPool; // воспроизведение звуков
    private SparseIntArray soundMap; // связь идентификаторов с SoundPool

    // Переменные Paint для рисования на экране
    private Paint textPaint; // для вывода текста
    private Paint backgroundPaint; // для стирания области рисования

    public CannonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        activity = (Activity) context;

        // Регистрация слушателя SurfaceHolder.Callback
        getHolder().addCallback(this);

        // Настройка атрибутов для воспроизведения звука
        AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
        attrBuilder.setUsage(AudioAttributes.USAGE_GAME);

        // Инициализация SoundPool для воспроизведения звука
        SoundPool.Builder builder = new SoundPool.Builder();
        builder.setMaxStreams(1);
        builder.setAudioAttributes(attrBuilder.build());
        soundPool = builder.build();

        // Предварительная загрузка звуков
        soundMap = new SparseIntArray(3);
        soundMap.put(TARGET_SOUND_ID, soundPool.load(context, R.raw.target_hit, 1));
        soundMap.put(CANNON_SOUND_ID, soundPool.load(context, R.raw.cannon_fire, 1));
        soundMap.put(BLOCKER_SOUND_ID, soundPool.load(context, R.raw.blocker_hit, 1));

        textPaint = new Paint();
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        screenWidth = w;
        screenHeight = h;

        textPaint.setTextSize((int) TEXT_SIZE_PERCENT * screenHeight);
        textPaint.setAntiAlias(true);
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void playSound(int soundId) {
        soundPool.play(soundMap.get(soundId), 1, 1, 1, 0, 1f);
    }

    public void newGame() {
        cannon = new Cannon(this,
                (int) (CANNON_BASE_RADIUS_PERCENT * screenHeight),
                (int) (CANNON_BARREL_LENGTH_PERCENT * screenWidth),
                (int) (CANNON_BARREL_WIDTH_PERCENT * screenHeight)
        );
        Random random = new Random();
        targets = new ArrayList<>();
        int targetX = (int) (TARGET_FIRST_X_PERCENT * screenWidth);
        int targetY = (int) ((0.5 - TARGET_LENGTH_PERCENT / 2) * screenHeight);

        for (int i = 0; i < TARGET_PIECES; i++) {
            // Случайная скорость от min до max
            double velocity = screenHeight * (random.nextDouble() * (TARGET_MAX_SPEED_PERCENT - TARGET_MIN_SPEED_PERCENT) + TARGET_MIN_SPEED_PERCENT);

            // Чередование мишеней белых и черных
            int color = (i % 2 == 0)
                    ? getResources().getColor(R.color.dark, getContext().getTheme())
                    : getResources().getColor(R.color.light, getContext().getTheme());

            velocity *= -1;

            // Создание и добавление новой мишени в список
            targets.add(new Target(
                    this, color, HIT_REWARD, targetX, targetY,
                    (int) (TARGET_WIDTH_PERCENT * screenWidth),
                    (int) (TARGET_LENGTH_PERCENT * screenHeight),
                    (int) velocity
            ));

            // Смещение следующей мишени вправо
            targetX += (TARGET_WIDTH_PERCENT + TARGET_SPACING_PERCENT) * screenWidth;
        }

        // Создание нового блока
        blocker = new Blocker(this, Color.BLACK, MISS_PENALTY,
                (int) (BLOCKER_X_PERCENT * screenWidth),
                (int) ((0.5 - BLOCKER_LENGTH_PERCENT / 2) * screenHeight),
                (int) (BLOCKER_WIDTH_PERCENT * screenWidth),
                (int) (BLOCKER_LENGTH_PERCENT * screenHeight),
                (float) (BLOCKER_SPEED_PERCENT * screenHeight));

        timeLeft = 10;
        shotsFired = 0;
        totalElapsedTime = 0.0;

        // Если игра закончена, начать новую
        if (gameOver) {
            gameOver = false;
            cannonThread = new CannonThread(getHolder());
            cannonThread.start();
        }
        hideSystemBars();
    }

    private void updatePositions(double elapsedTimeMS) {
        double interval = elapsedTimeMS / 1000.0; // в секунды

        // Обновление позиции ядра
        if (cannon.getCannonBall() != null) {
            cannon.getCannonBall().update(interval);
        }

        // Обновление позиции блока
        blocker.update(interval);

        // Обновление позиции мишени
        for (GameElement target : targets) {
            target.update(interval);
        }

        // Уменьшение оставшегося времени
        timeLeft -= interval;

        // Если счетчик достиг нуля
        if (timeLeft <= 0) {
            timeLeft = 0.0;
            gameOver = true;
            cannonThread.setRunning(false);
            showGameOverDialog(R.string.lose);
        }

        // Если все мишени поражены
        if (targets.isEmpty()) {
            cannonThread.setRunning(false);
            showGameOverDialog(R.string.win);
            gameOver = true;
        }
    }

    // Метод определяет угол наклона пушки и стреляет, если ядро не находится на экране
    public void alignAndFireCannonBall(MotionEvent event) {
        Point touchPoint = new Point((int) event.getX(), (int) event.getY());

        // Вычисление расстояния точки касания от центра экрана по оси y
        double centerMinusY = (screenHeight / 2 - touchPoint.y);

        // Вычисление угла ствола относительно горизонтали
        double angle = Math.atan2(touchPoint.x, centerMinusY);

        // Наведение в точку касания
        cannon.align(angle);

        // Пушка стреляет, если ядро не находится на экране
        if (cannon.getCannonBall() == null || !cannon.getCannonBall().isOnScreen()) {
            cannon.fireCannonBall();
            ++shotsFired;
        }
    }

    // AlertDialog при завершении игры
    private void showGameOverDialog(final int messageId) {
        final DialogFragment gameResult =
                new DialogFragment() {
                    @Override
                    public Dialog onCreateDialog(Bundle bundle) {
                        AlertDialog.Builder builder =
                                new AlertDialog.Builder(getActivity());
                        builder.setTitle(getResources().getString(messageId));

                        builder.setMessage(getResources().getString(
                                R.string.results_format, shotsFired, totalElapsedTime));
                        builder.setPositiveButton(R.string.reset_game,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        dialogIsDisplayed = false;
                                        newGame();
                                    }
                                }
                        );
                        return builder.create();
                    }
                };

        // В UI-потоке FragmentManager используется для вывода DialogFragment
        activity.runOnUiThread(
                new Runnable() {
                    public void run() {
                        showSystemBars();
                        dialogIsDisplayed = true;
                        gameResult.setCancelable(false); // modal dialog
                        gameResult.show(activity.getFragmentManager(), "results");
                    }
                }
        );
    }

    public void drawGameElements(Canvas canvas) {
        // очистка фона
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(),
                backgroundPaint);

        // вывод оставшегося времени
        canvas.drawText(getResources().getString(
                R.string.time_remaining_format, timeLeft), 50, 100, textPaint);

        cannon.draw(canvas);

        if (cannon.getCannonBall() != null &&
                cannon.getCannonBall().isOnScreen())
            cannon.getCannonBall().draw(canvas);

        blocker.draw(canvas);

        for (GameElement target : targets)
            target.draw(canvas);
    }

    // Проверка столкновений ядра с блоком или мишенями
    public void testForCollisions() {
        // Столкновение с мишенью - удаление мишени
        if (cannon.getCannonBall() != null && cannon.getCannonBall().isOnScreen()) {
            for (int n = 0; n < targets.size(); n++) {
                if (cannon.getCannonBall().collidesWith(targets.get(n))) {
                    targets.get(n).playSound();

                    // Прибавление награды
                    timeLeft += targets.get(n).getHitReward();

                    cannon.removeCannonBall();
                    targets.remove(n);
                    --n;
                    break;
                }
            }
        } else {
            cannon.removeCannonBall();
        }

        // Проверка столкновения с блоком
        if (cannon.getCannonBall() != null && cannon.getCannonBall().collidesWith(blocker)) {
            blocker.playSound(); // play Blocker hit sound

            // reverse ball direction
            cannon.getCannonBall().reverseVelocityX();

            // deduct blocker's miss penalty from remaining time
            timeLeft -= blocker.getMissPenalty();
        }
    }

    public void stopGame() {
        if (cannonThread != null)
            cannonThread.setRunning(false); // tell thread to terminate
    }

    public void releaseResources() {
        soundPool.release();
        soundPool = null;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (!dialogIsDisplayed) {
            newGame();
            cannonThread = new CannonThread(surfaceHolder);
            cannonThread.setRunning(true);
            cannonThread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        boolean retry = true;
        cannonThread.setRunning(false);

        while (retry) {
            try {
                cannonThread.join();
                retry = false;
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrupted", e);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            alignAndFireCannonBall(e);
        }
        return true;
    }

    // Поток для управления циклом игры
    private class CannonThread extends Thread {
        private SurfaceHolder surfaceHolder; // для работы с Canvas
        private boolean threadIsRunning = true;

        public CannonThread(SurfaceHolder holder) {
            surfaceHolder = holder;
            setName("CannonThread");
        }

        public void setRunning(boolean running) {
            threadIsRunning = running;
        }

        @Override
        public void run() {
            Canvas canvas = null;
            long previousFrameTime = System.currentTimeMillis();

            while (threadIsRunning) {
                try {
                    // Получение Canvas для монопольного рисования из этого потока
                    canvas = surfaceHolder.lockCanvas(null);

                    // блокировка SurfaceHolder для рисования
                    synchronized (surfaceHolder) {
                        long currentTime = System.currentTimeMillis();
                        double elapsedTimeMS = currentTime - previousFrameTime;
                        totalElapsedTime += elapsedTimeMS / 1000.0;
                        updatePositions(elapsedTimeMS);
                        testForCollisions();
                        drawGameElements(canvas);
                        previousFrameTime = currentTime;
                    }
                } finally {
                    // Вывести содержимое Canvas на CannonView и разрешить использовать другим потокам
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    }

    // Скрытие системных панелей и панели приложения
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    // Вывод системных панелей и панели приложения
    private void showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
