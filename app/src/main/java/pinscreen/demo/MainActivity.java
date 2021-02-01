package pinscreen.demo;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

//Firebase
import androidx.appcompat.app.AppCompatActivity;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ValueEventListener;


import pinscreen.demo.gl.CameraRenderer;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.security.AccessController.getContext;

//import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {
    private CameraRenderer mRenderer;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private DatabaseReference ref;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        mRenderer = findViewById(R.id.renderer_view);

        mRenderer.textViewBlendshapes = findViewById(R.id.text_view);
        mRenderer.textViewTimer = findViewById(R.id.text_timestamp);
//Firebase
       ref = FirebaseDatabase.getInstance().getReference().getRoot().child("facekitdata");
//        String userId = mDatabase.push().getKey();
        // creating user object
        //User user = new User("Test", "prathamesh@ajnalens.com");

// pushing user to 'users' node using the userId
       // mDatabase.child(userId).setValue(user);


        if (Build.VERSION.SDK_INT >= 23) {
            final int cameraPermission = checkSelfPermission(Manifest.permission.CAMERA);
            final int writePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (cameraPermission != PERMISSION_GRANTED || writePermission != PERMISSION_GRANTED) {
                Log.i("FaceKitDemo", "Requesting permission");
                final String[] permissions = {
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
                requestPermissions(permissions, REQUEST_CAMERA_PERMISSION);
            }
        }

    }


   @Override
    public void onPause(){
        super.onPause();
        mRenderer.onPause();
    }

    @Override
    public void onResume(){
        super.onResume();
        mRenderer.onResume();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mRenderer.onDestroy();

    }
}
