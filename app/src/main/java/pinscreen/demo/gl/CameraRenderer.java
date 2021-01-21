package pinscreen.demo.gl;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.firestore.auth.User;


import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import pinscreen.demo.R;
import pinscreen.facekit.FaceKit;
import pinscreen.facekit.anchordata.FaceKitAnchorData;

/**
 * FaceKit demo
 * GL Renderer for camera
 * reference:
 * https://github.com/yulu/ShaderCam
 * for basic openGL setup
 * Created by Cosimo on 10/26/18.
 */

public class CameraRenderer extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final String TAG = "FaceKitDemo";
    private final String LICENSE_KEY = "FC76yhRHnIezcV_NNVJeq7iOiDmgcP3CVNDuGlvg2CGRVTwlQ_mPIu2KDupemmpTYWPLSlQzBdMTeLTyg8T_xj8xGbnlNvkRJUujF5Qj02UC5MSTZJnLss5lUhTlDFqgZk_PpCH4rCkv.s6yaqxG7YhMrke2DUUubgWUcmn9jPA-";
    private final boolean renderMesh = false;
    private final boolean renderLandmarks = true;
    private final boolean renderVideo = true;

    private Context mContext;

    public TextView textViewBlendshapes;
    public TextView textViewTimer;

    public FaceKit mFaceKit;
    private final Shader mVideoShader = new Shader();
    private final Shader mMeshShader = new Shader();
    private final Shader mLandmarkShader = new Shader();

    private int mScreenWidth;
    private int mScreenHeight;
    private int mWorldWidth;
    private int mWorldHeight;
    private long mTexTimestamp = 0;

    private int mDeviceToWorldRotation;
    private int mWorldFlipped;

    private int mTextureHandle;
    /**
     * OpenGL params
     */
    private ByteBuffer mFullQuadVertices;
    private ByteBuffer mLandmarks;
    private ByteBuffer mTriangles;
    private ByteBuffer mPositions;
    private float[] mTransformM = new float[16];
    private float[] mOrientationM = new float[16];
    private float[] mRatio = new float[2];
    private float[] mProjectionMat = new float[16];
    private float[] mModelMat = new float[16];

    private int mIboTriangles = -1;
    private DatabaseReference mDatabase;

    public CameraRenderer(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public CameraRenderer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    private void init() {

        //Create full scene quad buffer
        final byte FULL_QUAD_COORDS[] = {-1, 1, -1, -1, 1, 1, 1, -1};
        mFullQuadVertices = ByteBuffer.allocateDirect(4 * 2);
        mFullQuadVertices.put(FULL_QUAD_COORDS).position(0);

        mLandmarks = ByteBuffer.allocateDirect(4 * 2 * 66);
        mPositions = ByteBuffer.allocateDirect(4 * 3 * 10822);

        setPreserveEGLContextOnPause(true);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mFaceKit = new FaceKit(mContext, LICENSE_KEY);
        mFaceKit.SetFaceAnchorCallbacks(new OnDataAvailableCallback());
    }

    private void updateOrientation(FaceKitAnchorData anchorData) {
        // scale to fit
        float scaleW = 1.0f * mScreenWidth / mWorldWidth;
        float scaleH = 1.0f * mScreenHeight / mWorldHeight;
        float scale = scaleW > scaleH ? scaleH : scaleW;
        mRatio[0] = scale * mWorldWidth / mScreenWidth;
        mRatio[1] = scale * mWorldHeight / mScreenHeight;
        // OpenGL rotation is counterclockwise
        Matrix.setRotateM(mOrientationM, 0,  - mDeviceToWorldRotation,
                0f, 0f, 1f);
        if (mWorldFlipped == 1) {
            Matrix.scaleM(mOrientationM, 0, -1, 1, 1);
        }

        { // projection matrix
            float[] ortho = new float[16];
            float[] s = new float[16];
            Matrix.orthoM(ortho, 0,
                    -.5f * mWorldWidth, .5f * mWorldWidth,
                    -.5f * mWorldHeight, .5f * mWorldHeight,
                    -1.0f * mWorldWidth, 1.0f * mWorldWidth);
            Matrix.setIdentityM(s, 0);
            s[0] = mRatio[0];
            s[5] = mRatio[1];
            Matrix.multiplyMM(mProjectionMat, 0, s, 0, ortho, 0);
        }
        { // transform matrix
            float[] s = new float[16]; // scale
            Matrix.setIdentityM(s, 0);
            s[0] = anchorData.transform.scale;
            s[5] = anchorData.transform.scale;
            s[10] = anchorData.transform.scale;

            float[] r = new float[16];
            Matrix.setRotateM(r, 0,
                    (float)Math.toDegrees(Math.acos(anchorData.transform.quaternion.w)),
                    anchorData.transform.quaternion.x,
                    anchorData.transform.quaternion.y,
                    anchorData.transform.quaternion.z);

            float[] t = new float[16]; // translation
            Matrix.setIdentityM(t, 0);
            t[12] = anchorData.transform.translation[0];
            t[13] = anchorData.transform.translation[1];
            t[14] = anchorData.transform.translation[2];

            float[] tmp = new float[16];
            Matrix.multiplyMM(tmp, 0, r, 0, s, 0);
            Matrix.multiplyMM(mModelMat, 0, t, 0, tmp, 0);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        //load and compile shader
        try {
            mVideoShader.setProgram(R.raw.vshader, R.raw.fshader, mContext);
            mMeshShader.setProgram(R.raw.vshader_mesh, R.raw.fshader_mesh, mContext);
            mLandmarkShader.setProgram(R.raw.vshader_point, R.raw.fshader_point, mContext);
            mFaceKit.initGL();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        mScreenWidth = width;
        mScreenHeight = height;

        if (mFaceKit != null) {
            mFaceKit.startRunning();
        }

        // start render
        requestRender();
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        // load video texture
        if (mFaceKit != null)
            mFaceKit.updateGL(mTexTimestamp);

        // clear
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glViewport(0, 0, mScreenWidth, mScreenHeight);

        if (renderVideo)
        { // render video
            mVideoShader.useProgram();

            int uTransformM = mVideoShader.getHandle("uTransformM");
            int uOrientationM = mVideoShader.getHandle("uOrientationM");
            int uRatioV = mVideoShader.getHandle("ratios");
            int aPosition = mVideoShader.getHandle("aPosition");

            GLES20.glUniformMatrix4fv(uTransformM, 1, false, mTransformM, 0);
            GLES20.glUniformMatrix4fv(uOrientationM, 1, false, mOrientationM, 0);
            GLES20.glUniform2fv(uRatioV, 1, mRatio, 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureHandle);

            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_BYTE, false, 0, mFullQuadVertices);
            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
        if (renderMesh)
        { // render mesh
            if (mTriangles != null) {
                if (mIboTriangles == -1) { // bind IBO once, avoid duplicated binding
                    int[] buffers = new int[1];
                    GLES20.glGenBuffers(1, buffers, 0);
                    mIboTriangles = buffers[0];
                    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mIboTriangles);
                    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * 3 * 21510, mTriangles, GLES20.GL_STATIC_DRAW);
                }

                GLES20.glEnable(GLES20.GL_DEPTH_TEST);
                GLES20.glClearDepthf(1.0f);
                GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

                mMeshShader.useProgram();

                int uProjectionM = mMeshShader.getHandle("uProjectionM");
                int uModelM = mMeshShader.getHandle("uModelM");
                int aPosition = mLandmarkShader.getHandle("aPosition");

                GLES20.glUniformMatrix4fv(uProjectionM, 1, false, mProjectionMat, 0);
                GLES20.glUniformMatrix4fv(uModelM, 1, false, mModelMat, 0);

                GLES20.glEnableVertexAttribArray(aPosition);
                GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, mPositions);

                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mIboTriangles);
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, 21510 * 3, GLES20.GL_UNSIGNED_SHORT, 0);

                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            }
        }
        if (renderLandmarks)
        { // render landmark
            mLandmarkShader.useProgram();

            int uProjectionM = mLandmarkShader.getHandle("uProjectionM");
            int aPosition = mLandmarkShader.getHandle("aPosition");

            GLES20.glUniformMatrix4fv(uProjectionM, 1, false, mProjectionMat, 0);

            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, mLandmarks);
            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 66);
            //Firebase
         //   mDatabase = FirebaseDatabase.getInstance().getReference("fackitdata");
        //    String userId = mDatabase.push().getKey();
            // creating user object
            //User user = new User(mLandmarks, "prathamesh@ajnalens.com");
          //  int a  = GLES20.GL_POINTS;

// pushing user to 'users' node using the userId
          //  mDatabase.child(userId).setValue(a);
        }
    }


    @IgnoreExtraProperties
    public class User {

        public ByteBuffer name;
        public String email;

        // Default constructor required for calls to
        // DataSnapshot.getValue(User.class)
        public User() {
        }

        public User(ByteBuffer name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // don't start facekit until texture is ready
    }

    @Override
    public void onPause() {
        mFaceKit.stopRunning();
        super.onPause();
    }

    public void onDestroy() {
        mFaceKit.stopRunning();
        mFaceKit = null;
    }

    private final static String[] blendShapes = {
            "mouthRollLower",
            "mouthStretch_L",
            "mouthSmile_R",
            "cheekSquint_R",
            "mouthFrown_L",
            "cheekPuff",
            "mouthSmile_L",
            "mouthRollUpper",
            "mouthPucker",
            "mouthLowerDown_L",
            "mouthFrown_R",
            "mouthShrugLower",
            "eyeLookDown_R",
            "mouthStretch_R",
            "eyeBlink_R",
            "eyeSquint_R",
            "eyeWide_R",
            "eyeLookUp_R",
            "browDown_R",
            "mouthLowerDown_R",
            "noseSneer_R",
            "browOuterUp_R",
            "mouthPress_L",
            "browOuterUp_L",
            "eyeLookUp_L",
            "browInnerUp",
            "mouthRight",
            "eyeWide_L",
            "mouthDimple_R",
            "mouthFunnel",
            "cheekSquint_L",
            "mouthClose",
            "mouthUpperUp_R",
            "mouthUpperUp_L",
            "mouthPress_R",
            "eyeLookIn_R",
            "eyeLookOut_R",
            "eyeLookIn_L",
            "eyeLookOut_L",
            "mouthLeft",
            "eyeBlink_L",
            "eyeLookDown_L",
            "eyeSquint_L",
            "noseSneer_L",
            "mouthDimple_L",
            "browDown_L",
            "jawRight",
            "jawForward",
            "jawOpen",
            "mouthShrugUpper",
            "jawLeft",
    };

    private class OnDataAvailableCallback implements FaceKit.OnDataAvailableListener {
        private long iterations = 0;
        private long totalFPS = 0;
        private double startTime = 0;

        @Override
        public void onDataAvailable(FaceKitAnchorData anchorData) {
            if (mTriangles == null) {
                mTriangles = ByteBuffer.allocateDirect(2 * 3 * 21510);
                mTriangles.order(ByteOrder.LITTLE_ENDIAN);
                mTriangles.put(mFaceKit.getTriangles());
                mTriangles.position(0);
            }

            // update texture spec
            mTexTimestamp = anchorData.texture.timestamp;
            mTextureHandle = anchorData.texture.handle;
            mTransformM = anchorData.texture.transformM;
            mWorldWidth = anchorData.texture.width;
            mWorldHeight = anchorData.texture.height;
            mDeviceToWorldRotation = anchorData.texture.rotation;
            mWorldFlipped = anchorData.texture.flipped;
            updateOrientation(anchorData);


            mLandmarks.order(ByteOrder.LITTLE_ENDIAN);
            mLandmarks.position(0);
            mLandmarks.asFloatBuffer().put(anchorData.landmarks, 0, 2 * 66);

            mPositions.order(ByteOrder.LITTLE_ENDIAN);
            mPositions.position(0);
            mPositions.put(mFaceKit.getPositions());
            mPositions.position(0);

            mDatabase = FirebaseDatabase.getInstance().getReference("facekitdata");
            String userId = mDatabase.push().getKey();
            // creating user object
            //User user = new User(mLandmarks, "prathamesh@ajnalens.com");
//            float [] a  = anchorData.landmarks;

// pushing user to 'users' node using the userId
//            for(int i=0 ; i < a.length; i++) {
//                mDatabase.child(userId).setValue(a[i]);
//            }
           // Toast.makeText(getContext(),"Landmarks"+ anchorData.landmarks, Toast.LENGTH_SHORT).show();

            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < 51; i++) {
                stringBuilder.append(String.format(Locale.US,"%s: %.2f\n", blendShapes[i], anchorData.blendShapes[i]));
                Log.i("FaceKit","Blendshapes [" + i + "] = " + Float.toString(anchorData.blendShapes[i]) );
                mDatabase.child("Blendshapes"+ i).setValue(Float.toString(anchorData.blendShapes[i]));
                //Toast.makeText(getContext(),"BlendShapes["+i+"]= " + anchorData.blendShapes[i], Toast.LENGTH_LONG).show();

            }

            final String text = stringBuilder.toString();

            if (textViewBlendshapes != null) {
                textViewBlendshapes.post(new Runnable() {
                    public void run() {
                        textViewBlendshapes.setText(text);
                    }
                });
            }

            // simple FPS estimator
            double curTime = System.currentTimeMillis();
            if (startTime != 0) {
                totalFPS += (curTime - startTime);
                Log.i(TAG, String.format("Overall FPS: %.2f", 1000.0 / (totalFPS / ++iterations)));
            }
            startTime = curTime;

            requestRender(); // Texture update can only be done in GL thread
        }
    }
}
