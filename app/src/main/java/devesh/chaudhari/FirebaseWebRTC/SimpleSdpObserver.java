package devesh.chaudhari.FirebaseWebRTC;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

class SimpleSdpObserver implements SdpObserver {

    String TAG="AppRTC: SDP: ";
    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(TAG, "onCreateSuccess: ");
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "onSetSuccess: ");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.d(TAG, "onCreateFailure: "+s);
    }

    @Override
    public void onSetFailure(String s) {
        Log.d(TAG, "onSetFailure: "+s);
    }

}
