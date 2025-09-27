package com.mvxgreen.ytdloader;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.mvxgreen.ytdloader.databinding.ActivityMainBinding;

public class AdsManager {
    private static final String TAG = AdsManager.class.getCanonicalName();
    private static InterstitialAd mInterstitialAd;

    private static final String PKG_SPOTIFLYER = "com.mvxgreen.spotloader",
            MSG_BAD_TOKEN_EXCEPTION = "caught bad token exception!";

    // INTERSTITIAL
    // REAL ID:
    // TEST ID:
    private static final String ID_INTER_REAL = "ca-app-pub-7417392682402637/2662302255",
            ID_INTER_TEST = "ca-app-pub-3940256099942544/1033173712",
            ID_BANNER_REAL = "ca-app-pub-7417392682402637/2853873948",
            ID_BANNER_TEST = "ca-app-pub-3940256099942544/9214589741",

            ID_INTERSTITIAL = ID_INTER_TEST,
            ID_BANNER = ID_BANNER_TEST;

    /**
     * Decide which ad to display based on runs
     *
     * @param runs # of runs
     * @param main main activity
     */
    public static void showLocalAd(int runs, MainActivity main) {
        int adIndex = (runs%3);
        switch (adIndex){
            case 0:
                showSpotiflyerAd(main, adIndex);
                break;
            case 1:
                showRateAd(main);
                break;
            case 2:

                break;
            default:

        }
    }

    /**
     * Ask the user to rate the app
     * @param main main activity
     */
    public static void showRateAd(MainActivity main) {
        Log.i(TAG, "Showing rate ad");

        final String appPackageName = main.getApplicationContext().getPackageName();

        final Dialog dialog = new Dialog(new ContextThemeWrapper(main, R.style.DialogDrip));
        dialog.setTitle(main.getString(R.string.msg_rate_dialog_title));

        LinearLayout ll = new LinearLayout(main);
        ll.setOrientation(LinearLayout.VERTICAL);

        TextView tv = new TextView(main);
        String msg = main.getString(R.string.msg_rate_dialog_body);
        tv.setText(msg);
        tv.setWidth(280);
        tv.setPadding(4, 0, 4, 43);
        tv.setTextAppearance(R.style.TextAppFragBody);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ll.addView(tv);

        LinearLayout l2 = new LinearLayout(main);
        l2.setOrientation(LinearLayout.HORIZONTAL);
        l2.setBottom(ll.getBottom());
        l2.setForegroundGravity(Gravity.BOTTOM);

        Button b3 = new Button(new ContextThemeWrapper(main, R.style.ButtonDripBad));
        b3.setText(main.getString(R.string.msg_rate_button2));
        b3.setOnClickListener(v -> dialog.dismiss());
        l2.addView(b3);

        Button b1 = new Button(new ContextThemeWrapper(main, R.style.ButtonDripGood));
        b1.setText(main.getString(R.string.msg_rate_button1));
        b1.setOnClickListener(v -> {
            main.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="
                    + appPackageName)));
            dialog.dismiss();
        });
        l2.addView(b1);

        ll.addView(l2);
        dialog.setContentView(ll);
        if (!main.isFinishing()) {
            try {
                dialog.show();
            } catch (Exception e) {
                Log.w(TAG, MSG_BAD_TOKEN_EXCEPTION);
            }
        }
    }

    /**
     * Ask user to install spotify downloader
     * @param main context
     */
    public static void showSpotiflyerAd(MainActivity main, int adIndex) {
        Log.i(TAG, "Showing spotiflyer ad...");
        final Dialog dialog = new Dialog(new ContextThemeWrapper(main, R.style.DialogDrip));
        dialog.setTitle(main.getString(R.string.ad_title_spotiflyer));

        LinearLayout ll = new LinearLayout(main);
        ll.setOrientation(LinearLayout.VERTICAL);

        RelativeLayout cl = new RelativeLayout(main);
        cl.setGravity(RelativeLayout.CENTER_HORIZONTAL);

        String msg = main.getString(R.string.ad_body_spotiflyer);

        /*
        int iconBottom;
        ImageView imageView = new ImageView(main);
        imageView.setImageResource(R.drawable.promo_spotiflyer);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(640, 320));
        cl.addView(imageView);
        iconBottom = imageView.getBottom();
         */

        ll.addView(cl);

        TextView body = new TextView(main);
        body.setText(msg);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 16, 0, 20);
        body.setPadding(8, 8, 8, 8);
        body.setLayoutParams(params);
        body.setTextAppearance(R.style.TextAppFragBody);
        body.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ll.addView(body);

        LinearLayout l2 = new LinearLayout(main);
        l2.setOrientation(LinearLayout.HORIZONTAL);
        l2.setBottom(ll.getBottom());
        l2.setForegroundGravity(Gravity.CENTER);

        Button b2 = new Button(new ContextThemeWrapper(main, R.style.ButtonDripBad));
        b2.setText(main.getString(R.string.ad_btn_negative));
        b2.setWidth(320);
        b2.setOnClickListener(v -> dialog.dismiss());
        l2.addView(b2);

        Button b1 = new Button(new ContextThemeWrapper(main, R.style.ButtonDripGood));
        b1.setText(main.getString(R.string.ad_btn_positive));
        b1.setWidth(320);
        b1.setOnClickListener(v -> {
            main.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="
                    + PKG_SPOTIFLYER)));
            dialog.dismiss();
        });
        l2.addView(b1);

        ll.addView(l2);
        dialog.setContentView(ll);
        if (!main.isFinishing()) {
            try {
                dialog.show();
            } catch (Exception e) {
                Log.w(TAG, MSG_BAD_TOKEN_EXCEPTION);
            }
        }
    }

    // ADMOB
    public static void loadAdmobInterstitialAd(MainActivity main) {
        Log.i(TAG, "loadAdmobInterstitialAd");
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(main, ID_INTERSTITIAL, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ia) {
                        Log.i(TAG, "interstitial onLoaded");
                        // The mInterstitialAd reference will be null until
                        // an ad is loaded.
                        mInterstitialAd = ia;
                        mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback(){
                            @Override
                            public void onAdClicked() {
                                // Called when a click is recorded for an ad.
                                Log.d(TAG, "Ad was clicked.");
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                // Called when ad is dismissed.
                                // Set the ad reference to null so you don't show the ad a second time.
                                Log.d(TAG, "Ad dismissed fullscreen content.");
                                mInterstitialAd = null;
                                // load next interstitial ad
                                AdsManager.loadAdmobInterstitialAd(main);
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                // Called when ad fails to show.
                                Log.e(TAG, "Ad failed to show fullscreen content.");
                                mInterstitialAd = null;

                            }

                            @Override
                            public void onAdImpression() {
                                // Called when an impression is recorded for an ad.
                                Log.d(TAG, "Ad recorded an impression.");
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                // Called when ad is shown.
                                Log.d(TAG, "Ad showed fullscreen content.");
                            }
                        });
                        Log.i(TAG, "onAdLoaded");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        // Handle the error
                        Log.d(TAG, loadAdError.toString());
                        mInterstitialAd = null;

                    }
                });
    }

    public static void showInterstitialAd(MainActivity main) {
        if (mInterstitialAd != null) {
            mInterstitialAd.show(main);
        } else {
            Log.d("TAG", "mInterstitialAd wasn't ready yet.");
        }
    }

    private static AdSize getBannerAdSize(MainActivity main, ActivityMainBinding binding) {
        // Determine the screen width (less decorations) to use for the ad width.
        Display display = main.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        float density = outMetrics.density;

        float adWidthPixels = binding.bannerContainer.getWidth();

        // If the ad hasn't been laid out, default to the full screen width.
        if (adWidthPixels == 0) {
            adWidthPixels = outMetrics.widthPixels;
        }

        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(main, adWidth);
    }

    public static void loadBanner(MainActivity main, ActivityMainBinding binding) {
        // Create a new ad view.
        AdView adView = new AdView(main);
        adView.setAdSize(getBannerAdSize(main, binding));

        adView.setAdUnitId(ID_BANNER);

        // Replace ad container with new ad view.
        binding.bannerContainer.removeAllViews();
        binding.bannerContainer.addView(adView);

        // Start loading the ad in the background.
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }
}
