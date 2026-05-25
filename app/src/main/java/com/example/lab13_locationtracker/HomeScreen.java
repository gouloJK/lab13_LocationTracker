package com.example.lab13_locationtracker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HomeScreen extends AppCompatActivity {

    private Button openMapViewBtn;
    private Button saveCurrentSpotBtn;
    private TextView gpsStatusText;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private double currentAltitude = 0.0;
    private float locationAccuracy = 0.0f;
    private RequestQueue networkRequestQueue;
    private LocationManager systemLocationManager;
    private String serverInsertUrl = "http://10.0.2.2:8080/lab13_backend/save_coordinates.php";
    private static final int LOCATION_PERMISSION_ID = 200;
    private static final String TAG = "HomeScreen";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_home_screen);

        networkRequestQueue = Volley.newRequestQueue(getApplicationContext());
        systemLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        openMapViewBtn = findViewById(R.id.openMapViewBtn);
        saveCurrentSpotBtn = findViewById(R.id.saveCurrentSpotBtn);
        gpsStatusText = findViewById(R.id.gpsStatusText);

        openMapViewBtn.setOnClickListener(v -> {
            startActivity(new Intent(HomeScreen.this, MapDisplayScreen.class));
        });

        saveCurrentSpotBtn.setOnClickListener(v -> {
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                saveLocationToServer();
            } else {
                Toast.makeText(this, "Waiting for GPS signal... Move around or set location in emulator", Toast.LENGTH_LONG).show();
            }
        });

        verifyAndRequestPermissions();
    }

    private void verifyAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_ID);
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                gpsStatusText.setText("GPS Status: Permission Denied");
                Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        systemLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                5,
                new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        currentAltitude = location.getAltitude();
                        locationAccuracy = location.getAccuracy();

                        String msg = String.format(Locale.getDefault(),
                                "GPS Ready | Lat: %.4f, Lng: %.4f", currentLatitude, currentLongitude);
                        gpsStatusText.setText(msg);

                        Log.d(TAG, "Location: " + currentLatitude + ", " + currentLongitude);
                    }

                    @Override
                    public void onProviderEnabled(@NonNull String provider) {
                        gpsStatusText.setText("GPS Status: Enabled");
                    }

                    @Override
                    public void onProviderDisabled(@NonNull String provider) {
                        gpsStatusText.setText("GPS Status: Please enable GPS");
                    }
                });
    }

    private void saveLocationToServer() {
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();

        StringRequest request = new StringRequest(Request.Method.POST, serverInsertUrl,
                response -> {
                    Log.d(TAG, "Server response: " + response);
                    Toast.makeText(HomeScreen.this, "Location saved successfully!", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    String errorMsg = "Failed";
                    if (error.networkResponse != null) {
                        errorMsg = "Error " + error.networkResponse.statusCode;
                        Log.e(TAG, "Error: " + new String(error.networkResponse.data));
                    } else if (error.getMessage() != null) {
                        errorMsg = error.getMessage();
                    }
                    Log.e(TAG, "Error: " + errorMsg, error);
                    Toast.makeText(HomeScreen.this, "Failed: " + errorMsg, Toast.LENGTH_LONG).show();
                }) {
            @Override
            protected Map<String, String> getParams() {
                HashMap<String, String> params = new HashMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                params.put("lat", String.valueOf(currentLatitude));
                params.put("lng", String.valueOf(currentLongitude));
                params.put("altitude_val", String.valueOf(currentAltitude));
                params.put("recorded_at", sdf.format(new Date()));
                params.put("device_code", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
                return params;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(10000, 3, 1.0f));
        networkRequestQueue.add(request);
    }
}