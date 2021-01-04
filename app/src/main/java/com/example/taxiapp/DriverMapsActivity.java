package com.example.taxiapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DriverMapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private static final int CHECK_SETTINGS_CODE = 444;
    private static final int REQUEST_LOCATION_PERMISSION = 333;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationCallback locationCallback;
    private Location currentLocation;

    private boolean isLocationUpdatesActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_maps);

        Button signOutButton = findViewById(R.id.activity_driver_maps_btn_sign_out);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();


        signOutButton.setOnClickListener(v -> {
            auth.signOut();
            signOutDriver();
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        buildLocationRequest();
        buildLocationCallBack();
        buildLocationSettingsRequest();

        startLocationUpdates();
    }

    // Sign out driver and delete hes location from firebase
    public void signOutDriver() {
        // Get driver id
        String driverUserId = currentUser.getUid();
        DatabaseReference drivers = FirebaseDatabase.getInstance().getReference().child("drivers");

        // Delete drivers location from firebase
        GeoFire geoFire = new GeoFire(drivers);
        geoFire.removeLocation(driverUserId);

        // After sign out return driver to choose mode
        Intent intent = new Intent(DriverMapsActivity.this, ChooseModeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if(currentLocation != null){
            // Places marker on users current location and moves camera on it
            LatLng driverLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            mMap.addMarker(new MarkerOptions().position(driverLocation).title("Your location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(driverLocation));
        }

    }

    // Track drivers location if permission is given
    private void startLocationUpdates(){
        isLocationUpdatesActive = true;

        // Determine if the relevant system settings are enabled on the device to carry out the desired location request
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this, locationSettingsResponse -> {
                    if (ActivityCompat.checkSelfPermission(
                            DriverMapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(DriverMapsActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                                    PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    // Request location tracking updates
                    fusedLocationProviderClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.myLooper()
                    );
                    updateLocationUi();

                }).addOnFailureListener(this, e -> {
                    // Handle location settings errors
                    int statusCode = ((ApiException) e).getStatusCode();

                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                ResolvableApiException resolvableApiException =
                                        (ResolvableApiException) e;
                                resolvableApiException.startResolutionForResult(
                                        DriverMapsActivity.this,
                                        CHECK_SETTINGS_CODE
                                );
                            } catch (IntentSender.SendIntentException sie) {
                                sie.printStackTrace();
                            }
                            break;

                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String message = "Adjust location settings on your device";
                            Toast.makeText(DriverMapsActivity.this, message, Toast.LENGTH_LONG).show();

                            isLocationUpdatesActive = false;
                    }
                    updateLocationUi();
                });
    }

    // Stop drivers location tracking
    private void stopLocationUpdates(){
        if (!isLocationUpdatesActive) {
            return;
        }

        fusedLocationProviderClient.removeLocationUpdates(locationCallback).addOnCompleteListener(this, task -> isLocationUpdatesActive = false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if driver agreed to change location
        if (requestCode == CHECK_SETTINGS_CODE) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    // Driver has agreed to change location
                    startLocationUpdates();
                    break;
                case Activity.RESULT_CANCELED:
                    // Driver has not agreed to change location
                    isLocationUpdatesActive = false;
                    updateLocationUi();
                    break;
            }
        }
    }

    // Build location settings request for driver
    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    // Build location call back
    private void buildLocationCallBack() {

        // Receives notification from the FusedLocationProviderApi when the device location has changed or can no longer be determined
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                currentLocation = locationResult.getLastLocation();
                updateLocationUi();
            }
        };
    }

    // Get drivers current location and sets marker on map, and sets location in firebase
    private void updateLocationUi() {
        if (currentLocation != null) {
            // Get drivers current location and set marker on map, move camera on marker
            LatLng driverLocation = new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(driverLocation));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(12));
            mMap.addMarker(new MarkerOptions().position(driverLocation).title("Your location"));

            // Get drivers userId and write location in Firebase
            String driverUserId = currentUser.getUid();
            DatabaseReference driversGeoFire = FirebaseDatabase.getInstance().getReference().child("driversGeoFire");
            GeoFire geoFire = new GeoFire(driversGeoFire);
            geoFire.setLocation(driverUserId, new GeoLocation(currentLocation.getLatitude(),currentLocation.getLongitude()));
        }
    }

    // Request drivers high accuracy location every 3-10 seconds
    private void buildLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    // Ask driver for location permission if he declines that explain to driver why app needs permission using snack bar
    private void requestLocationPermission() {

        // Checks if we should explain to driver why app needs permission
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);

        // This message will be shown to driver if he declined location tracking permission message first time
        if (shouldProvideRationale) {
            showSnackBar(
                    "Location permission is needed for " + "app functionality", "OK",
                    v -> ActivityCompat.requestPermissions(DriverMapsActivity.this, new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            },
                            REQUEST_LOCATION_PERMISSION
                    )
            );
        } else {
            // If app asks for location tracking permission first time then no explanation will be shown to driver
            ActivityCompat.requestPermissions(DriverMapsActivity.this, new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    // Create snack bar to use it, for explanation to driver why app needs location tracking permission
    private void showSnackBar(final String mainText, final String action, View.OnClickListener listener) {
        Snackbar.make(findViewById(android.R.id.content), mainText, Snackbar.LENGTH_INDEFINITE).setAction(action, listener).show();
    }

    // Handle request permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length <= 0) {
                // onRequestPermissions Request was cancelled
                Toast.makeText(this,"Request was cancelled",Toast.LENGTH_LONG).show();
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Start tracking drivers current location
                if (isLocationUpdatesActive) {
                    startLocationUpdates();
                }
            } else {
                // If driver declines tracking permission second time
                showSnackBar(
                        "Turn on location on settings", "Settings",
                        v -> {
                            // Redirect driver to settings
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                            intent.setData(uri);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                );
            }
        }
    }

    // Check if drivers location tracking is permitted to app
    private boolean checkLocationPermission() {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }


    @Override
    protected void onPause() {
        super.onPause();

        // Stop tracking drivers location
        stopLocationUpdates();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Check tracking permission, start tracking drivers current location
        if (isLocationUpdatesActive && checkLocationPermission()) {
            startLocationUpdates();
        } else if (!checkLocationPermission()) {
            requestLocationPermission();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Sign out driver delete current location from firebase
        String driverUserId = currentUser.getUid();
        DatabaseReference drivers = FirebaseDatabase.getInstance().getReference().child("drivers");
        GeoFire geoFire = new GeoFire(drivers);
        geoFire.removeLocation(driverUserId);


    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // Sign out driver delete current location from firebase
        String driverUserId = currentUser.getUid();
        DatabaseReference drivers = FirebaseDatabase.getInstance().getReference().child("drivers");
        GeoFire geoFire = new GeoFire(drivers);
        geoFire.removeLocation(driverUserId);

        // Redirect driver to home screen
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);

    }
}