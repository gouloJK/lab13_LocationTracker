package com.example.lab13_locationtracker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

public class MapDisplayScreen extends AppCompatActivity {

    private MapView mapDisplayView;
    private ProgressBar dataLoadingSpinner;
    private FloatingActionButton refreshMarkersFab;
    private RequestQueue networkQueue;
    private String serverFetchUrl = "http://10.0.2.2:8080/lab13_backend/fetch_coordinates.php";
    private static final String TAG = "MapDisplayScreen";
    private List<GeoPoint> allPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // ⚠️ CRITICAL: Configure OSMDroid BEFORE setContentView
        // This fixes the map tiles not loading
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Set tile cache path
        Configuration.getInstance().setOsmdroidTileCache(getCacheDir());

        // Load configuration
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid_prefs", MODE_PRIVATE));

        setContentView(R.layout.activity_map_display);

        // Initialize views
        mapDisplayView = findViewById(R.id.mapDisplayView);
        dataLoadingSpinner = findViewById(R.id.dataLoadingSpinner);
        refreshMarkersFab = findViewById(R.id.refreshMarkersFab);

        // Setup map with tile loading fix
        setupMapConfiguration();

        // Initialize network
        networkQueue = Volley.newRequestQueue(getApplicationContext());

        // Set refresh button click
        refreshMarkersFab.setOnClickListener(v -> fetchAndDisplayLocations());

        // Load markers after a short delay to let map initialize
        mapDisplayView.postDelayed(() -> fetchAndDisplayLocations(), 1000);

        Log.d(TAG, "MapDisplayScreen created successfully");
    }

    private void setupMapConfiguration() {
        // ⚠️ CRITICAL FIXES FOR MAP TILES:

        // 1. Use multiple tile sources for fallback
        try {
            mapDisplayView.setTileSource(TileSourceFactory.MAPNIK);
        } catch (Exception e) {
            Log.e(TAG, "MAPNIK failed, trying default tiles", e);
            mapDisplayView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        }

        // 2. Enable hardware acceleration for smooth rendering
        mapDisplayView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 3. Enable all controls
        mapDisplayView.setBuiltInZoomControls(true);
        mapDisplayView.setMultiTouchControls(true);

        // 4. Set tile scaling for better display
        mapDisplayView.setTilesScaledToDpi(true);

        // 5. Enable map rotation
        mapDisplayView.setMapOrientation(0, true);

        // 6. Set min/max zoom levels
        mapDisplayView.setMinZoomLevel(3.0);
        mapDisplayView.setMaxZoomLevel(20.0);

        // 7. Set default position (Casablanca, Morocco)
        mapDisplayView.getController().setZoom(14.0);
        mapDisplayView.getController().setCenter(new GeoPoint(33.5731, -7.5898));

        // 8. Force refresh
        mapDisplayView.invalidate();

        Log.d(TAG, "Map configured - Cache dir: " + Configuration.getInstance().getOsmdroidTileCache().getAbsolutePath());
    }

    private void fetchAndDisplayLocations() {
        dataLoadingSpinner.setVisibility(View.VISIBLE);
        mapDisplayView.getOverlays().clear();
        allPoints.clear();

        Log.d(TAG, "Fetching from: " + serverFetchUrl);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                serverFetchUrl,
                null,
                response -> {
                    dataLoadingSpinner.setVisibility(View.GONE);

                    try {
                        String status = response.getString("status");

                        if (!"success".equals(status)) {
                            Toast.makeText(this, "Server error: " + status, Toast.LENGTH_LONG).show();
                            return;
                        }

                        JSONArray locations = response.getJSONArray("location_data");

                        Log.d(TAG, "Found " + locations.length() + " locations");

                        if (locations.length() == 0) {
                            Toast.makeText(this,
                                    "No locations saved yet!\nGo back and click 'SAVE MY POSITION'",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Add markers for each location
                        for (int i = 0; i < locations.length(); i++) {
                            JSONObject loc = locations.getJSONObject(i);
                            double lat = loc.getDouble("lat");
                            double lng = loc.getDouble("lng");
                            String time = loc.optString("recorded_at", "N/A");
                            addVisibleMarker(lat, lng, time, i + 1);
                        }

                        // Refresh map
                        mapDisplayView.invalidate();

                        // Zoom to show all markers
                        zoomToShowAllMarkers();

                        Toast.makeText(this,
                                "✓ Loaded " + locations.length() + " location(s)",
                                Toast.LENGTH_SHORT).show();

                    } catch (JSONException e) {
                        Log.e(TAG, "JSON error: " + e.getMessage(), e);
                        Toast.makeText(this, "Error parsing data", Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    dataLoadingSpinner.setVisibility(View.GONE);

                    String errorMsg;
                    if (error.networkResponse != null) {
                        errorMsg = "Server error " + error.networkResponse.statusCode;
                    } else if (error.getMessage() != null) {
                        errorMsg = error.getMessage();
                    } else {
                        errorMsg = "Unknown error";
                    }

                    Log.e(TAG, "Error: " + errorMsg, error);
                    Toast.makeText(this, "Failed: " + errorMsg, Toast.LENGTH_LONG).show();
                });

        request.setRetryPolicy(new DefaultRetryPolicy(15000, 2, 1.0f));
        networkQueue.add(request);
    }

    private void addVisibleMarker(double lat, double lng, String timestamp, int number) {
        GeoPoint point = new GeoPoint(lat, lng);
        allPoints.add(point);

        Marker marker = new Marker(mapDisplayView);
        marker.setPosition(point);
        marker.setTitle("📍 Location #" + number);
        marker.setSnippet("Time: " + timestamp);

        // Create bright red marker icon
        Drawable markerIcon = createLargeMarkerIcon();
        marker.setIcon(markerIcon);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Show info window on tap
        marker.setOnMarkerClickListener((clickedMarker, mapView) -> {
            if (clickedMarker.isInfoWindowShown()) {
                clickedMarker.closeInfoWindow();
            } else {
                clickedMarker.showInfoWindow();
            }
            return true;
        });

        mapDisplayView.getOverlays().add(marker);
        Log.d(TAG, "Marker #" + number + " added at: " + lat + ", " + lng);
    }

    private Drawable createLargeMarkerIcon() {
        int size = 120;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Outer red circle
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // White border
        Paint borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, borderPaint);

        // Inner white circle
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint);

        // Center red dot
        paint.setColor(Color.RED);
        canvas.drawCircle(size / 2f, size / 2f, size / 6f, paint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private void zoomToShowAllMarkers() {
        if (allPoints.isEmpty()) {
            return;
        }

        if (allPoints.size() == 1) {
            mapDisplayView.getController().setZoom(16.0);
            mapDisplayView.getController().animateTo(allPoints.get(0));
        } else {
            double north = -90, south = 90, east = -180, west = 180;

            for (GeoPoint point : allPoints) {
                north = Math.max(north, point.getLatitude());
                south = Math.min(south, point.getLatitude());
                east = Math.max(east, point.getLongitude());
                west = Math.min(west, point.getLongitude());
            }

            double latPadding = (north - south) * 0.2;
            double lngPadding = (east - west) * 0.2;

            BoundingBox boundingBox = new BoundingBox(
                    north + latPadding,
                    east + lngPadding,
                    south - latPadding,
                    west - lngPadding
            );

            mapDisplayView.zoomToBoundingBox(boundingBox, true, 50);
        }

        mapDisplayView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapDisplayView.onResume();
        fetchAndDisplayLocations();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapDisplayView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkQueue != null) {
            networkQueue.cancelAll(request -> true);
        }
    }
}