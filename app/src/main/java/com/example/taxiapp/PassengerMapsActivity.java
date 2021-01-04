package com.example.taxiapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
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

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class PassengerMapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private static final int CHECK_SETTINGS_CODE = 444;
    private static final int REQUEST_LOCATION_PERMISSION = 333;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference driversGeoFire;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationCallback locationCallback;
    private Location currentLocation;

    private boolean isLocationUpdatesActive;

    private Button bookTaxiButton;

    private int searchRadius = 1;
    private boolean isDriverFound = false;
    private String nearestDriverId;
    private Marker driverMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_maps);

        Button signOutButton = findViewById(R.id.activity_passenger_maps_btn_sign_out);
        bookTaxiButton = findViewById(R.id.activity_passenger_maps_btn_book_taxi);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        driversGeoFire = FirebaseDatabase.getInstance().getReference().child("driversGeoFire");

        signOutButton.setOnClickListener(v -> {
            auth.signOut();
            signOutPassenger();
        });

        bookTaxiButton.setOnClickListener(v -> {
            bookTaxiButton.setText(R.string.getting_your_taxi);

            gettingNearestTaxi();
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
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

    private void gettingNearestTaxi() {
        // Get passengers current location and set search radius
        GeoFire geoFire = new GeoFire(driversGeoFire);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(currentLocation.getLatitude(), currentLocation.getLongitude()), searchRadius);

        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                // If driver was found get drivers key and calculate distance to passenger
                if(!isDriverFound){
                    isDriverFound = true;
                    nearestDriverId = key;
                    
                    getNearestDriverLocation();
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                // If driver wasn't found increase search radius for 1km, 10km is max if 10km is reached start searching from 1km
                if(!isDriverFound){
                    if(searchRadius <= 10){
                        searchRadius++;
                    }else {
                        searchRadius = 1;
                    }
                    gettingNearestTaxi();
                }
            }
        });
    }

    // Get drivers coordinates set marker for passenger and calculate distance to passenger
    private void getNearestDriverLocation() {
        bookTaxiButton.setText(R.string.getting_your_driver_location);

        DatabaseReference nearestDriverLocation = FirebaseDatabase.getInstance().getReference().child("driversGeoFire").child(nearestDriverId).child("l");
        nearestDriverLocation.addValueEventListener(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    // Get drivers coordinates from Firebase as List<Object>
                    List<Object> driverLocationParameters = (List<Object>) snapshot.getValue();

                    double latitude = 0;
                    double longitude = 0;

                    // Parse drivers coordinates to double
                    assert driverLocationParameters != null;
                    if(driverLocationParameters.get(0) != null ) {
                        latitude = Double.parseDouble(driverLocationParameters.get(0).toString());
                    }
                    if(driverLocationParameters.get(1) != null ) {
                        longitude = Double.parseDouble(driverLocationParameters.get(1).toString());
                    }

                    LatLng driverLatLng = new LatLng(latitude,longitude);

                    // Remove marker
                    if(driverMarker != null){
                        driverMarker.remove();
                    }

                    // Get drivers location
                    Location driverLocation = new Location("");
                    driverLocation.setLatitude(latitude);
                    driverLocation.setLongitude(longitude);

                    // Calculate distance from driver to passenger
                    float distanceToDriver = driverLocation.distanceTo(currentLocation);
                    bookTaxiButton.setText(getString(R.string.distance_to_driver) + distanceToDriver);

                    // Set drivers new marker
                    driverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title(getString(R.string.your_driver_is_here)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    // Sign out passenger and delete hes location from firebase
    public void signOutPassenger() {
        // Get passenger id
        String passengerUserId = currentUser.getUid();
        DatabaseReference passengers = FirebaseDatabase.getInstance().getReference().child("passengers");

        // Delete passengers location from firebase
        GeoFire geoFire = new GeoFire(passengers);
        geoFire.removeLocation(passengerUserId);

        // After sign out return driver to choose mode
        Intent intent = new Intent(PassengerMapsActivity.this, ChooseModeActivity.class);
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
            // Add a marker in Sydney and move the camera
            LatLng passengerLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            mMap.addMarker(new MarkerOptions().position(passengerLocation).title("Your location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(passengerLocation));
        }

    }

    // Track passengers location if permission is given
    private void startLocationUpdates(){
        isLocationUpdatesActive = true;

        // Determine if the relevant system settings are enabled on the device to carry out the desired location request
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this, locationSettingsResponse -> {
                    if (ActivityCompat.checkSelfPermission(
                            PassengerMapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(PassengerMapsActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
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
                                        PassengerMapsActivity.this,
                                        CHECK_SETTINGS_CODE
                                );
                            } catch (IntentSender.SendIntentException sie) {
                                sie.printStackTrace();
                            }
                            break;

                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String message = "Adjust location settings on your device";
                            Toast.makeText(PassengerMapsActivity.this, message, Toast.LENGTH_LONG).show();

                            isLocationUpdatesActive = false;
                    }
                    updateLocationUi();
                });
    }

    // Stop passengers location tracking
    private void stopLocationUpdates(){
        if (!isLocationUpdatesActive) {
            return;
        }

        fusedLocationProviderClient.removeLocationUpdates(locationCallback).addOnCompleteListener(this, task -> isLocationUpdatesActive = false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if passenger agreed to change location
        if (requestCode == CHECK_SETTINGS_CODE) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    // Passenger has agreed to change location
                    startLocationUpdates();
                    break;
                case Activity.RESULT_CANCELED:
                    // Passenger has not agreed to change location
                    isLocationUpdatesActive = false;
                    updateLocationUi();
                    break;
            }
        }
    }

    // Build location settings request for passenger
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

    // Get passengers current location and sets marker on map, and sets location in firebase
    private void updateLocationUi() {
        if (currentLocation != null) {
            // Get passengers current location and set marker on map, move camera on marker
            LatLng passengerLocation = new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(passengerLocation));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(12));
            mMap.addMarker(new MarkerOptions().position(passengerLocation).title("Your location"));

            // Get passengers userId and write location in Firebase
            String passengerUserId = currentUser.getUid();
            DatabaseReference passengersGeoFire = FirebaseDatabase.getInstance().getReference().child("passengersGeoFire");
            GeoFire geoFire = new GeoFire(passengersGeoFire);
            geoFire.setLocation(passengerUserId, new GeoLocation(currentLocation.getLatitude(),currentLocation.getLongitude()));
        }
    }

    // Request passengers high accuracy location every 3-10 seconds
    private void buildLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    // Ask passenger for location permission if he declines that explain to passenger why app needs permission using snack bar
    private void requestLocationPermission() {

        // Checks if we should explain to passenger why app needs permission
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);

        // This message will be shown to passenger if he declined location tracking permission message first time
        if (shouldProvideRationale) {
            showSnackBar(
                    "Location permission is needed for " + "app functionality", "OK",
                    v -> ActivityCompat.requestPermissions(PassengerMapsActivity.this, new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            },
                            REQUEST_LOCATION_PERMISSION
                    )
            );
        } else {
            // If app asks for location tracking permission first time then no explanation will be shown to passenger
            ActivityCompat.requestPermissions(PassengerMapsActivity.this, new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    // Create snack bar to use it, for explanation to passenger why app needs location tracking permission
    private void showSnackBar(final String mainText, final String action, View.OnClickListener listener) {
        Snackbar.make(findViewById(android.R.id.content), mainText, Snackbar.LENGTH_INDEFINITE).setAction(action, listener).show();
    }

    // Handle request permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length <= 0) {
                // onRequestPermissions Request was cancelled
                Toast.makeText(this,"Request was cancelled", Toast.LENGTH_LONG).show();
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Start tracking passengers current location
                if (isLocationUpdatesActive) {
                    startLocationUpdates();
                }
            } else {
                // If driver declines tracking permission second time
                showSnackBar(
                        "Turn on location on settings",
                        "Settings",
                        v -> {
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

    // Check if passengers location tracking is permitted to app
    private boolean checkLocationPermission() {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }


    @Override
    protected void onPause() {
        super.onPause();

        // Stop tracking passengers location
        stopLocationUpdates();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Check tracking permission, start tracking passengers current location
        if (isLocationUpdatesActive && checkLocationPermission()) {
            startLocationUpdates();
        } else if (!checkLocationPermission()) {
            requestLocationPermission();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Sign out passenger delete current location from firebase
        String passengerUserId = currentUser.getUid();
        DatabaseReference passengers = FirebaseDatabase.getInstance().getReference().child("drivers");
        GeoFire geoFire = new GeoFire(passengers);
        geoFire.removeLocation(passengerUserId);

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // Sign out passenger delete current location from firebase
        String passengerUserId = currentUser.getUid();
        DatabaseReference passengers = FirebaseDatabase.getInstance().getReference().child("drivers");
        GeoFire geoFire = new GeoFire(passengers);
        geoFire.removeLocation(passengerUserId);

        // Redirect passenger to home screen
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);


    }
}