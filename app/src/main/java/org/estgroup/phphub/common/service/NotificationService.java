package org.estgroup.phphub.common.service;

import android.accounts.AccountManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.orhanobut.logger.Logger;
import com.squareup.otto.Produce;

import org.estgroup.phphub.api.entity.NotificationEntity;
import org.estgroup.phphub.api.entity.element.Notification;
import org.estgroup.phphub.common.App;
import org.estgroup.phphub.common.event.NotificationChangeEvent;
import org.estgroup.phphub.common.provider.BusProvider;
import org.estgroup.phphub.common.transformer.SchedulerTransformer;
import org.estgroup.phphub.common.util.Utils;
import org.estgroup.phphub.model.UserModel;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import eu.unicate.retroauth.AuthAccountManager;
import rx.functions.Action1;
import rx.functions.Func1;

public class NotificationService extends Service {
    private final static int UPDATE_INTERVAL = 3000;

    private Timer timer = new Timer();

    @Inject
    AuthAccountManager authAccountManager;

    @Inject
    AccountManager accountManager;

    @Inject
    UserModel userModel;

    String accountType, tokenType;

    @Override
    public void onCreate() {
        super.onCreate();
        accountType = Utils.getAccountType(this);
        tokenType = Utils.getTokenType(this);
        ((App) getApplication()).getApiComponent().inject(this);
        BusProvider.getInstance().register(this);

        notificationTask();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        timer.cancel();
        BusProvider.getInstance().unregister(this);
    }

    public void notificationTask() {
        if (timer == null) {
            return;
        }

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (Utils.logined(NotificationService.this, accountManager)) {
                    userModel.once()
                            .setToken(authAccountManager.getAuthToken(
                                    Utils.getActiveAccount(NotificationService.this, authAccountManager),
                                    accountType,
                                    tokenType
                            ))
                            .getMyNotifications()
                            .compose(new SchedulerTransformer<NotificationEntity>())
                            .map(new Func1<NotificationEntity, List<Notification>>() {
                                @Override
                                public List<Notification> call(NotificationEntity notificationEntity) {
                                    return notificationEntity.getData();
                                }
                            })
                            .subscribe(new Action1<List<Notification>>() {
                                @Override
                                public void call(List<Notification> notifications) {
                                    BusProvider.getInstance().post(new NotificationChangeEvent(notifications.size()));
                                }
                            }, new Action1<Throwable>() {
                                @Override
                                public void call(Throwable throwable) {
                                    Logger.e(throwable.toString());
                                }
                            });
                }
            }
        }, 0, UPDATE_INTERVAL);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
