package devesh.chaudhari.FirebaseWebRTC;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static org.webrtc.SessionDescription.Type.ANSWER;
import static org.webrtc.SessionDescription.Type.OFFER;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private static final int PERMISSION_REQUEST = 2;

    public static final int VIDEO_RESOLUTION_WIDTH = 1280;
    public static final int VIDEO_RESOLUTION_HEIGHT = 720;
    public static final int FPS = 30;

    String RoomID="room01";

    String TAG="AppRTC: ";

    SurfaceViewRenderer surfaceView;
    SurfaceViewRenderer surfaceView2;
    EglBase rootEglBase;
    PeerConnectionFactory factory;
    FirebaseDatabase database;

     AudioManager audioManager;
    PeerConnection peerConnection;
    
    MediaConstraints audioConstraints;
    VideoTrack videoTrackFromCamera;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    List<String> STUNList= Arrays.asList(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
            "stun:stun.vodafone.ro:3478",
            "stun:stun.samsungsmartcam.com:3478",
            "stun:stun.services.mozilla.com:3478"

    );
    DatabaseReference SendData;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

         database = FirebaseDatabase.getInstance();
        surfaceView=findViewById(R.id.surface_view);
        surfaceView2=findViewById(R.id.surface_view2);

audioManager=(AudioManager) getSystemService(Context.AUDIO_SERVICE);
if(!audioManager.isBluetoothScoOn()){
    audioManager.startBluetoothSco();
}

requestPermissions();

      initializeSurfaceViews();
       initializePeerConnectionFactory();
        createVideoTrackFromCameraAndShowIt();
        initializePeerConnections();
        startStreamingVideo();

    }

    @Override
    public void onBackPressed() {
        Disconnect();
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Some permissions have been granted
        // ...
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Some permissions have been denied
        // ...
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Dynamic permissions are not required before Android M.
            //   onPermissionsGranted();
            return;
        }
        methodRequiresTwoPermission();

        String[] missingPermissions = getMissingPermissions();
        if (missingPermissions.length != 0) {
            requestPermissions(missingPermissions, PERMISSION_REQUEST);
        } else {
            // onPermissionsGranted();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private String[] getMissingPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return new String[0];
        }

        PackageInfo info;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to retrieve permissions.");
            return new String[0];
        }

        if (info.requestedPermissions == null) {
            Log.w(TAG, "No requested permissions.");
            return new String[0];
        }

        ArrayList<String> missingPermissions = new ArrayList<>();
        for (int i = 0; i < info.requestedPermissions.length; i++) {
            if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
                missingPermissions.add(info.requestedPermissions[i]);
            }
        }
        Log.d(TAG, "Missing permissions: " + missingPermissions);

        return missingPermissions.toArray(new String[missingPermissions.size()]);
    }

    @AfterPermissionGranted(PERMISSION_REQUEST)
    private void methodRequiresTwoPermission() {
        String[] perms = getMissingPermissions();
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            // ...
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, "Requires Permission",
                    PERMISSION_REQUEST, perms);
        }
    }


    private void initializeSurfaceViews() {
        rootEglBase = EglBase.create();
        surfaceView.init(rootEglBase.getEglBaseContext(), null);
        surfaceView.setEnableHardwareScaler(true);
        surfaceView.setMirror(true);

        surfaceView2.init(rootEglBase.getEglBaseContext(), null);
        surfaceView2.setEnableHardwareScaler(true);
        surfaceView2.setMirror(true);

        //add one more
    }
    private void initializePeerConnectionFactory() {
        VideoEncoderFactory encoderFactory;
        VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */,false);
        decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        //factory.setVideoHwAccelerationOptions(rootEglBase.getEglBaseContext(), rootEglBase.getEglBaseContext());
    }

    private void createVideoTrackFromCameraAndShowIt() {

        VideoCapturer videoCapturer = createVideoCapturer();
        //VideoSource videoSource=null;
        //Create a VideoSource instance
        VideoSource videoSource;
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            videoSource = factory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());


        VideoEncoderFactory videoEncoderFactory =
                new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext()
                        , true, true);
        for (int i = 0; i < videoEncoderFactory.getSupportedCodecs().length; i++) {
            Log.d(TAG, "Supported codecs: " + videoEncoderFactory.getSupportedCodecs()[i].name);
        }

        videoTrackFromCamera = factory.createVideoTrack("100", videoSource);

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();

        //create an AudioSource instance
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("101", audioSource);

        if (videoCapturer != null) {
         //   videoCapturer.startCapture(1024, 720, 30);
            videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);

        }
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        videoTrackFromCamera.setEnabled(true);

        videoTrackFromCamera.addSink(surfaceView);



    }

    private void initializePeerConnections() {
        peerConnection = createPeerConnection(factory);

    }
    private void startStreamingVideo() {
        MediaStream mediaStream = factory.createLocalMediaStream("ARDAMS");
        mediaStream.addTrack(videoTrackFromCamera);
        mediaStream.addTrack(localAudioTrack);
        peerConnection.addStream(mediaStream);

     //   sendMessage("got user media");
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
    }
    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;
        Logging.d(TAG, "Creating capturer using camera1 API.");
        //videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
        if (useCamera2()) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private PeerConnection createPeerConnection(PeerConnectionFactory factory) {
        Log.d(TAG, "createPeerConnection: ");
        //==
        // Add ICE Servers
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();

        // STUN 1
//        PeerConnection.IceServer.Builder iceServerBuilder = PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302");
  //      iceServerBuilder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK); //this does the magic.
    //    PeerConnection.IceServer iceServer =  iceServerBuilder.createIceServer();

        for(String i:STUNList){
      PeerConnection.IceServer.Builder iceServerBuilder = PeerConnection.IceServer.builder(i);
               iceServerBuilder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK); //this does the magic.
                PeerConnection.IceServer iceServer =  iceServerBuilder.createIceServer();
                iceServers.add(iceServer);
}


        //==
       // ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        //iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        //MediaConstraints pcConstraints = new MediaConstraints();

        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
               /*
               * HAVE_LOCAL_OFFER
               * HAVE_REMOTE_OFFER
               */
                Log.d(TAG, "onSignalingChange: "+signalingState);

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: "+iceConnectionState);
                ConnectionStatus(iceConnectionState.toString());
                /*
                 NEW,
    CHECKING,
    CONNECTED,
    COMPLETED,
    FAILED,
    DISCONNECTED,
    CLOSED;
    * */

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange: "+b);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: "+iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate: "+iceCandidate);
                JSONObject message = new JSONObject();

                try {
                    message.put("type", "candidate");
                    message.put("label", iceCandidate.sdpMLineIndex);
                    message.put("id", iceCandidate.sdpMid);
                    message.put("candidate", iceCandidate.sdp);

                    Log.d(TAG, "onIceCandidate: sending candidate " + message);
                  SendData2DB(message);
                    Log.d(TAG, "onIceCandidate: "+message.toString());
                } catch (JSONException e) {

                    Log.e(TAG, "onIceCandidate ERROR: "+e );
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved: "+iceCandidates);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size());
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0);
                remoteAudioTrack.setEnabled(true);
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addSink(surfaceView2);

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onRemoveStream: ");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel: ");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ");
            }

            @Override
            public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
                Log.d(TAG, "onStandardizedIceConnectionChange: "+newState.toString());

            }
        };

        //return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
        return factory.createPeerConnection(rtcConfig, pcObserver);
    }

    public void ConnectionStatus(String s){
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    if(s.equals("CONNECTED")){
                        Toast.makeText(MainActivity.this, "CONNECTED", Toast.LENGTH_SHORT).show();
                    }
                }catch (Exception e){
                    Log.e(TAG, "ConnectionStatus: "+e );
                }
            }
        });


    }
    public void doCall(View v) {
        Log.d(TAG, "doCall: ");

       MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "onCreateSuccess: ");
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "offer");
                    message.put("sdp", sessionDescription.description);
                    SendData2DB(message);
                    Log.d(TAG, "onCreateSuccess: "+message.toString());
                    CandidateDBListner();
                } catch (JSONException e) {

                    Log.e(TAG, "onCreateSuccess ERROR: "+e );
                }
            }
        }, sdpMediaConstraints);

        AnswerDBListner();

    }
    public void doAnswer(View v) {
        OfferDBListner();

    }


    public void doAnswerRun() {
        Log.d(TAG, "doAnswer: ");

        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "answer");
                    message.put("sdp", sessionDescription.description);
                   SendData2DB(message);
                    Log.d(TAG, "onCreateSuccess: "+message.toString());
                    CandidateDBListner();
                } catch (JSONException e) {
                    Log.e(TAG, "onCreateSuccess ERROR: "+e );
                }
            }
        },  new MediaConstraints());// new MediaConstraints()
    }

    void SendData2DB(JSONObject message){
        // Create a new user with a first and last name
        String type="unknown";

        Map<String, Object> data = new HashMap<>();
        try {
            type=message.get("type").toString();
            data.put(type, message.toString());
            if(type.equals("offer")){
            }
            if(type.equals("answer")){
            }
            if(type.equals("candidate")){
            }
            Log.d(TAG, "SendData2DB: "+type);

            if(type.equals("candidate")){
                SendData = database.getReference("WebRTC/"+RoomID+"/candidate");
                SendData.push().setValue(message.toString());
            }else{
                SendData = database.getReference("WebRTC/"+RoomID);
                SendData.updateChildren(data);
            }

          //  SendData.setValue(message.toString());



        }catch (Exception e){
            Log.e(TAG, "SendData2DB: " + e);
        }


    }

    void OfferDBListner(){
        DatabaseReference OfferDB = database.getReference("WebRTC/"+RoomID+"/offer");
        OfferDB.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                 if(dataSnapshot.getValue(String.class)!=null){
                    String value = dataSnapshot.getValue(String.class);
                    try {
                        JSONObject message = new JSONObject(value);
                        Log.d(TAG, "OFFER JSON: " + message.toString());

                        peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(OFFER, message.getString("sdp")));
                        doAnswerRun();
                    }catch (JSONException err){
                        Log.d("Error", err.toString());
                    }

                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }
    void CandidateDBListner(){
        DatabaseReference CandidateDB = database.getReference("WebRTC/"+RoomID+"/candidate");
        CandidateDB.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    for(DataSnapshot ds: dataSnapshot.getChildren()){
                        if(ds.getValue(String.class)!=null){
                            String value = ds.getValue(String.class);
                            try {
                                JSONObject message = new JSONObject(value);
                                Log.d(TAG, "CandidateDBListner(); " + message);

                                IceCandidate candidate = new IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"));
                                peerConnection.addIceCandidate(candidate);
                            }catch (JSONException err){
                                Log.d("Error", err.toString());
                            }
                        }
                    }
                }


            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }
    void AnswerDBListner(){
        DatabaseReference AnswerDB = database.getReference("WebRTC/"+RoomID+"/answer");
        AnswerDB.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                  if(dataSnapshot.getValue(String.class)!=null){
                    String value = dataSnapshot.getValue(String.class);
                    Log.d(TAG, "Value is: " + value);
                    try {
                        JSONObject message = new JSONObject(value);
                        peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(ANSWER, message.getString("sdp")));

                    }catch (JSONException err){
                        Log.d("Error", err.toString());
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });




    }

   void Disconnect(){
        peerConnection.close();

   }
    

}