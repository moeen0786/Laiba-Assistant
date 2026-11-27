package com.laiba.assistant;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

public final class InternetConnectionManager {

    public interface Listener {
        void onInternetStateChanged(boolean available);
    }

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler =
        new Handler(Looper.getMainLooper());

    private volatile Listener listener;
    private boolean registered;

    private final ConnectivityManager.NetworkCallback callback =
        new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {
                // Wait for onCapabilitiesChanged() for validated status.
            }

            @Override
            public void onLost(Network network) {
                publish(false);
            }

            @Override
            public void onCapabilitiesChanged(
                    Network network,
                    NetworkCapabilities capabilities) {
                publish(isValidated(capabilities));
            }

            @Override
            public void onUnavailable() {
                publish(false);
            }
        };

    public InternetConnectionManager(Context context) {
        this.context = context.getApplicationContext();

        connectivityManager = (ConnectivityManager)
            this.context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            );
    }

    public synchronized void start(Listener listener) {
        this.listener = listener;

        if (connectivityManager == null) {
            publish(false);
            return;
        }

        if (!registered) {
            try {
                connectivityManager.registerDefaultNetworkCallback(
                    callback
                );
                registered = true;
            } catch (RuntimeException error) {
                publish(hasValidatedInternet(context));
                return;
            }
        }

        publish(hasValidatedInternet(context));
    }

    public synchronized void stop() {
        if (registered && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(
                    callback
                );
            } catch (RuntimeException ignored) {
            }
        }

        registered = false;
        listener = null;
    }

    public boolean hasValidatedInternet() {
        return hasValidatedInternet(context);
    }

    public static boolean hasValidatedInternet(Context context) {
        if (context == null) return false;

        try {
            ConnectivityManager manager =
                (ConnectivityManager) context
                    .getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            if (manager == null) return false;

            Network active = manager.getActiveNetwork();
            if (active == null) return false;

            NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(active);

            return isValidated(capabilities);

        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean isValidated(
            NetworkCapabilities capabilities) {

        return capabilities != null
            && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            );
    }

    private void publish(boolean available) {
        Listener target = listener;
        if (target == null) return;

        mainHandler.post(() -> {
            if (listener == target) {
                target.onInternetStateChanged(available);
            }
        });
    }
}
