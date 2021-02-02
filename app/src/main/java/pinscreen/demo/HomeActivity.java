package pinscreen.demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

//import android.support.annotation.NonNull;
//import android.support.design.widget.BottomNavigationView;
//import android.support.v7.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);


        BottomNavigationView bottomNavigationView = (BottomNavigationView)
                findViewById(R.id.bottom_navigation);

        bottomNavigationView.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_favorites:
                                Toast.makeText(getApplicationContext(), "Join the meeting", Toast.LENGTH_SHORT).show();
                                Intent inentProfile2 = new Intent(pinscreen.demo.HomeActivity.this, MainActivityFaceKit.class);
                                startActivity(inentProfile2);
                                break;
                            case R.id.action_home:
                                Toast.makeText(getApplicationContext(), "Home", Toast.LENGTH_SHORT).show();
                                break;
                            case R.id.action_profile:
                                Intent inentProfile = new Intent(pinscreen.demo.HomeActivity.this, ProfileActivity.class);
                                startActivity(inentProfile);
                                break;
                        }
                        return true;
                    }
                });
    }
}