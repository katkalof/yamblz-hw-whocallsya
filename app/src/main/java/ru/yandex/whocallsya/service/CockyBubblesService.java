package ru.yandex.whocallsya.service;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.WeakHashMap;

import ru.yandex.whocallsya.R;
import ru.yandex.whocallsya.bubble.BubbleLayout;
import ru.yandex.whocallsya.bubble.BubblesLayoutCoordinator;
import ru.yandex.whocallsya.bubble.InformingLayout;
import rx.subjects.PublishSubject;

import static android.telephony.TelephonyManager.EXTRA_STATE;


public class CockyBubblesService extends BaseBubblesService {

    public static final String PHONE_NUMBER = "PHONE_NUMBER";
    WeakHashMap<String, BubbleLayout> bubbles = new WeakHashMap<>();
    //    private BubbleTrashLayout bubblesTrash;
    private InformingLayout infoLayout;
    private BubblesLayoutCoordinator layoutCoordinator;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getExtras().containsKey(PHONE_NUMBER) && intent.getExtras().containsKey(EXTRA_STATE)) {
            String phoneState = intent.getStringExtra(EXTRA_STATE);
            String phoneNumber = intent.getStringExtra(PHONE_NUMBER);
            if (phoneState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                if (unknownPhoneNumber(phoneNumber) && !bubbles.containsKey(phoneNumber)) {
                    addBubble(phoneNumber);
                }
            } else if (phoneState.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                removeBubble(phoneNumber);
            } else if (phoneState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                removeBubbleIfMissed(phoneNumber);
            }
            return START_NOT_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("whocallsya", "onCreateService");
//        bubblesTrash = new BubbleTrashLayout(this);
//        addBubbleLayout(R.layout.bubble_trash, bubblesTrash, buildLayoutParamsForTrash());
        layoutCoordinator = new BubblesLayoutCoordinator.Builder(this)
                .setWindowManager(getWindowManager())
//                .setTrashView(bubblesTrash)
                .build();
        infoLayout = new InformingLayout(this);
        addBubbleLayout(R.layout.info_layout, infoLayout, buildLayoutParamsForInfo());
        infoLayout.setLayoutCoordinator(layoutCoordinator);
    }

    public void addBubble(String number) {
        logBubble(number, "addBubble");
        BubbleLayout bubbleView = (BubbleLayout) LayoutInflater.from(this).inflate(R.layout.bubble_main, null);
        WindowManager.LayoutParams layoutParams = buildLayoutParamsForBubble();
        bubbleView.setNumber(number);
        bubbleView.setWindowManager(getWindowManager());
        bubbleView.setViewParams(layoutParams);
        bubbleView.setLayoutCoordinator(layoutCoordinator);
        bubbleView.setShouldStickToWall(true);
        bubbleView.setOnBubbleClickListener(bubble -> {
            if (bubble.isShownOpen()) {
                removeBubble(bubble.getNumber());
            } else {
                bubble.goToBottom();
                bubble.changeImageView();
                String lastNumber = infoLayout.getLastSearchingNumber();
                infoLayout.setData(number);
                if (infoLayout.isOpen()) {
                    changeBubble(lastNumber);
                } else {
                    infoLayout.show();
                }
            }
        });
        bubbles.put(number, bubbleView);
        addViewToWindow(bubbleView);
    }

    public void removeBubble(String number) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (bubbles.containsKey(number)) {
                getWindowManager().removeView(bubbles.get(number));
                logBubble(number, "removeBubble");
                bubbles.remove(number);
                if (infoLayout.getLastSearchingNumber().equals(number) && infoLayout.isOpen()) {
                    infoLayout.unShow();
                }
            }
            if (bubbles.isEmpty()) {
//                getWindowManager().removeView(bubblesTrash);
                getWindowManager().removeView(infoLayout);
                stopSelf();
            }
        });
    }

    private void removeBubbleIfMissed(String phoneNumber) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Для правильной работы приложения АОН необходимо разрешить" +
                    " доступ к Вашим последним вызовам", Toast.LENGTH_LONG).show();
        } else {
            PublishSubject<String> subject = PublishSubject.create();
            MissedCallContentObserver h = new MissedCallContentObserver(new Handler(), getContentResolver(), phoneNumber, subject);
            subject.subscribe(s -> {
                if (s.equals("Incoming")) {
                    removeBubble(phoneNumber);
                }
                getContentResolver().unregisterContentObserver(h);
                subject.onCompleted();
            });
            getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, true, h);
        }
    }

    public void changeLastBubble() {
        if (infoLayout != null) {
            changeBubble(infoLayout.getLastSearchingNumber());
        }
    }

    private void changeBubble(String number) {
        if (!number.isEmpty() && bubbles.containsKey(number)) {
            bubbles.get(number).changeImageView();
        }
    }
}