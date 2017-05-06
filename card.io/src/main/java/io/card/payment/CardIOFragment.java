package io.card.payment;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;

import io.card.payment.i18n.LocalizedStrings;
import io.card.payment.i18n.StringKey;

/**
 * Created by ahasbini on 5/3/2017.
 */

public class CardIOFragment extends Fragment {

    private static final String TAG = CardIOFragment.class.getSimpleName();

    private static final int PERMISSION_REQUEST_ID = 12;

    private static final int ORIENTATION_PORTRAIT = 1;
    private static final int ORIENTATION_PORTRAIT_UPSIDE_DOWN = 2;
    private static final int ORIENTATION_LANDSCAPE_RIGHT = 3;
    private static final int ORIENTATION_LANDSCAPE_LEFT = 4;

    static private int numFragmentAllocations;

    private Callback callback;
    private LinearLayout rootView;

    private CardScanner mCardScanner;
    private Rect mGuideFrame;
    private int mFrameOrientation;

    public CardIOFragment setCallBack(Callback callBack) {
        this.callback = callBack;
        return this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = new LinearLayout(getActivity());
        rootView.setBackgroundColor(getResources().getColor(android.R.color.black));
        rootView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        numFragmentAllocations++;
        // NOTE: java native asserts are disabled by default on Android.
        if (numFragmentAllocations != 1) {
            // it seems that this can happen in the autotest loop, but it doesn't seem to break.
            // was happening for lemon... (ugh, long story) but we're now protecting the underlying
            // DMZ/scanner from over-release.
            Log.i(TAG, String.format(
                    "INTERNAL WARNING: There are %d (not 1) CardIOFragment allocations!",
                    numFragmentAllocations));
        }

        // TODO: 5/3/2017 ahasbini: check theme implementation
        // TODO: 5/3/2017 ahasbini: check orientation detection and implementation

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        resetCheckConditionsInitScan();
    }

    private void resetCheckConditionsInitScan() {
        final Activity activity = getActivity();

        rootView.removeAllViews();

        try {
            // Check permissions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (activity.checkSelfPermission(Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "permission denied to camera - requesting it");

                    LinearLayout permissionRequestView = new LinearLayout(activity);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    layoutParams.gravity = Gravity.CENTER;
                    permissionRequestView.setLayoutParams(layoutParams);
                    permissionRequestView.setOrientation(LinearLayout.VERTICAL);

                    TextView messageTextView = new TextView(activity);
                    // TODO: 5/3/2017 ahasbini: add string translations
                    // messageTextView.setText(LocalizedStrings.getString(StringKey.PERMISSION_REQUEST));
                    messageTextView.setText("Camera Permission is need to start card scanning");
                    messageTextView.setTextColor(getResources().getColor(android.R.color.white));
                    // TODO: 5/3/2017 ahasbini: check if result is as expected
                    int margin = getResources().getDimensionPixelOffset(R.dimen.cio_4dp);
                    messageTextView.setPadding(margin, margin, margin, margin);
                    messageTextView.setGravity(Gravity.CENTER);
                    permissionRequestView.addView(messageTextView);

                    Button grantPermissionButton = new Button(activity);
                    grantPermissionButton.setText("Allow");
                    grantPermissionButton.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_ID);
                            }
                        }
                    });
                    permissionRequestView.addView(grantPermissionButton);

                    return;
                }
            }

            if (!checkCamera()) {
                // Check processor and camera support
                if (callback != null) {
                    callback.onFailure();
                }
            } else {
                // Init camera scanning
                showCameraScannerOverlay();
            }
        } catch (Exception e) {
            handleGeneralExceptionError(e);
        }
    }

    private boolean checkCamera() {
        try {
            if (!Util.hardwareSupported()) {
                StringKey errorKey = StringKey.ERROR_NO_DEVICE_SUPPORT;
                String localizedError = LocalizedStrings.getString(errorKey);
                Log.w(Util.PUBLIC_LOG_TAG, errorKey + ": " + localizedError);

                return false;
            }
        } catch (CameraUnavailableException e) {
            StringKey errorKey = StringKey.ERROR_CAMERA_CONNECT_FAIL;
            String localizedError = LocalizedStrings.getString(errorKey);

            Log.e(Util.PUBLIC_LOG_TAG, errorKey + ": " + localizedError);

            return false;
        }

        return true;
    }

    private void showCameraScannerOverlay() {
        try {
            mGuideFrame = new Rect();

            mFrameOrientation = ORIENTATION_PORTRAIT;

            // TODO: 5/3/2017 ahasbini: implement tester
            /*if (getIntent().getBooleanExtra(PRIVATE_EXTRA_CAMERA_BYPASS_TEST_MODE, false)) {
                if (!getActivity().getPackageName().contentEquals("io.card.development")) {
                    throw new IllegalStateException("Illegal access of private extra");
                }
                // use reflection here so that the tester can be safely stripped for release
                // builds.
                Class<?> testScannerClass = Class.forName("io.card.payment.CardScannerTester");
                Constructor<?> cons = testScannerClass.getConstructor(this.getClass(),
                        Integer.TYPE);
                mCardScanner = (CardScanner) cons.newInstance(new Object[] { this,
                        mFrameOrientation });
            } else {*/
                mCardScanner = new CardScanner(this, mFrameOrientation);
            /*}*/
        } catch (Exception e) {
            handleGeneralExceptionError(e);
        }
    }

    private void handleGeneralExceptionError(Exception e) {
        String localizedError = LocalizedStrings.getString(StringKey.ERROR_CAMERA_UNEXPECTED_FAIL);

        rootView.removeAllViews();

        TextView errorTextView = new TextView(getActivity());
        errorTextView.setText(localizedError);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        errorTextView.setGravity(Gravity.CENTER);
        errorTextView.setLayoutParams(layoutParams);
        rootView.addView(errorTextView);

        Log.e(Util.PUBLIC_LOG_TAG, "Unknown exception, please post the stack trace as a GitHub issue", e);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                resetCheckConditionsInitScan();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    public interface Callback {

        void onFailure();
    }
}
