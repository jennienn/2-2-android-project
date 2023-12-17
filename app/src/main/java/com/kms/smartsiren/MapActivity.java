package com.kms.smartsiren;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.animation.Interpolation;
import com.kakao.vectormap.camera.CameraAnimation;
import com.kakao.vectormap.camera.CameraUpdate;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelStyles;
import com.kakao.vectormap.label.LabelTransition;
import com.kakao.vectormap.label.Transition;
import com.kakao.vectormap.shape.DotPoints;
import com.kakao.vectormap.shape.Polygon;
import com.kakao.vectormap.shape.PolygonOptions;
import com.kakao.vectormap.shape.PolygonStyles;
import com.kakao.vectormap.shape.PolygonStylesSet;
import com.kakao.vectormap.shape.ShapeAnimator;
import com.kakao.vectormap.shape.animation.CircleWave;
import com.kakao.vectormap.shape.animation.CircleWaves;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import android.Manifest;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/*
FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // User is signed in

        } else {
            // No user is signed in

        }
        로그인 했는지 안했는지 검사
 */
public class MapActivity extends AppCompatActivity {

    private KakaoMap kakaoMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final String GEOCODE_URL = "http://dapi.kakao.com/v2/local/search/address.json?query=";
    //Rest API 키, 테스트할려면 바꿔야 함.
    private static final String GEOCODE_USER_INFO = "KakaoAK 1dd2a83a90a775ff04216aa9b7a36ee8";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    LocationRequest locationRequest;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle actionBarDrawerToggle;
    Label centerLabel;
    LabelLayer labelLayer;
    private Polygon animationPolygon;
    private ShapeAnimator shapeAnimator;
    private Map<String, PolygonStylesSet> reportCaseToPolygonStylesMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // 로그인 안됨
            Intent intent = new Intent(MapActivity.this, LoginActivity.class); // Replace LoginActivity with your actual login activity class
            startActivity(intent);
            finish();
        }

        Button btn_address_search = findViewById(R.id.btn_address_search);
        final EditText et_address_search = findViewById(R.id.et_address_search);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        MapView mapView = findViewById(R.id.map_view);
        createLocationRequest();
        createReportCaseToPolygonStylesMap();

        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        TextView navLogout = findViewById(R.id.nav_logout);
        navLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MapActivity.this)
                        .setTitle("안내")
                        .setMessage("로그아웃 하시겠습니까?")
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(MapActivity.this, LoginActivity.class); // Replace LoginActivity with your actual login activity class
                                startActivity(intent);
                                finish();
                            }
                        })
                        .show();
            }
        });

        drawerLayout = findViewById(R.id.drawer_layout);
        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();


        // 데이터 베이스에서 위험지역 가져오기
        DatabaseReference databaseReference;
        databaseReference = FirebaseDatabase.getInstance().getReference("SmartSiren");

        Query mergeCaseQuery = databaseReference.child("CaseInfo");

        mergeCaseQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                    // 가져온 CaseInfo
                    CaseInfo child = postSnapshot.getValue(CaseInfo.class);
                    DangerLabelWave(LatLng.from(child.getLatitude(),child.getLongitude()), "reportCase1"/*child.getCategory()*/);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Getting Post failed, log a message
                // ...
            }
        });


        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        Button goButton = findViewById(R.id.go_btn);
        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapActivity.this, ReportActivity.class); // Replace ReportActivity with your actual report activity class
                getReportLocation.launch(intent);
            }
        });

        mapView.start(new MapLifeCycleCallback() {
            @Override
            public void onMapResumed() {
                super.onMapResumed();
            }

            @Override
            public void onMapPaused() {
                super.onMapPaused();
            }
            @Override
            public void onMapDestroy() {
                // Map API is destroyed
            }
            @Override
            public void onMapError(Exception error) {
                // Error during map usage
            }
        }, new KakaoMapReadyCallback() {
            @Override
            public void onMapReady(KakaoMap Map) {
                kakaoMap = Map;
                labelLayer = kakaoMap.getLabelManager().getLayer();
                if (ContextCompat.checkSelfPermission(MapActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
                } else {
                    getLastLocation();
                    startLocationUpdates();
                }
            }
        });

        btn_address_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = et_address_search.getText().toString();
                new Thread(() -> {
                    LocationSearch(text); // network 동작, 인터넷에서 xml을 받아오는 코드
                }).start();
            }
        });
    }

    // Rest of your methods (createLocationRequest, getLastLocation, startLocationUpdates, onOptionsItemSelected, etc.)
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //위치 요청 설정
    protected void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
                .setIntervalMillis(1000)             // Sets the interval for location updates
                .setMinUpdateIntervalMillis(500)  // Sets the fastest allowed interval of location updates.
                .setWaitForAccurateLocation(true)              // Want Accurate location updates make it true or you get approximate updates
                .setMaxUpdateDelayMillis(100)                   // Sets the longest a location update may be delayed.
                .build();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied. The client can initialize
                // location requests here.
                // ...
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(MapActivity.this,
                                REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                }
            }
        });
    }

    //마지막 위치
    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    // 현재 위치의 위도와 경도를 기반으로 카메라 업데이트 생성
                    CameraUpdate cameraUpdate = CameraUpdateFactory.newCenterPosition(LatLng.from(location.getLatitude(), location.getLongitude()));
                    // 지도의 카메라 위치를 업데이트
                    kakaoMap.moveCamera(cameraUpdate, CameraAnimation.from(500, true, true));

                    centerLabel = labelLayer.addLabel(LabelOptions.from("centerLabel", LatLng.from(location.getLatitude(), location.getLongitude()))
                            .setStyles(LabelStyle.from(R.drawable.pink_marker).setAnchorPoint(0.5f, 0.5f))
                            .setRank(1));
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        //버튼 및 텍스트
        Button btn_current_location = findViewById(R.id.btn_current_location);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    if (locationResult == null) {
                        return;
                    }
                    //Showing the latitude, longitude and accuracy on the home screen.
                    for (Location location : locationResult.getLocations()) {
                        // 현재위치 저장하기(LocationManager)
                        LocationManager.getInstance().setCurrentLocation(location);
                        centerLabel.moveTo(LatLng.from(location.getLatitude(), location.getLongitude()));
                        //현재위치 버튼 눌렀을 때 작동하는 부분
                        btn_current_location.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                if (ContextCompat.checkSelfPermission(MapActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(MapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
                                } else {
                                    CameraUpdate cameraUpdate = CameraUpdateFactory.newCenterPosition(LatLng.from(location.getLatitude(), location.getLongitude()));
                                    // 지도의 카메라 위치를 업데이트
                                    kakaoMap.moveCamera(cameraUpdate, CameraAnimation.from(500, true, true));
                                }
                            }
                        });

                    }
                }
            }
        };
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    //주소 받아오고 해당 주소 위도,경도 받고 카메라 조정
    public void LocationSearch(String address) {

        try {
            String encodedAddress = URLEncoder.encode(address, "UTF-8");
            URL obj = new URL(GEOCODE_URL + encodedAddress);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", GEOCODE_USER_INFO);
            con.setRequestProperty("content-type", "application/json");
            con.setDoOutput(true);
            con.setUseCaches(false);
            con.setDefaultUseCaches(false);

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), Charset.forName("UTF-8")));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            String jsonResponse = response.toString();

            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray documentsArray = jsonObject.getJSONArray("documents");

            if (documentsArray.length() > 0) {
                JSONObject firstDocument = documentsArray.getJSONObject(0);
                double latitude = Double.parseDouble(firstDocument.getString("y"));
                double longitude = Double.parseDouble(firstDocument.getString("x"));
                CameraUpdate cameraUpdate = CameraUpdateFactory.newCenterPosition(LatLng.from(latitude, longitude));
                // 지도의 카메라 위치를 업데이트
                kakaoMap.moveCamera(cameraUpdate, CameraAnimation.from(500, true, true));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 신고 케이스별 circlewave 색설정을 초기화하는 메소드
    private void createReportCaseToPolygonStylesMap() {
        reportCaseToPolygonStylesMap.put("reportCase1", PolygonStylesSet.from(PolygonStyles.from(Color.parseColor("#ff0000")))); // 빨간색
        reportCaseToPolygonStylesMap.put("reportCase2", PolygonStylesSet.from(PolygonStyles.from(Color.parseColor("#00ff00")))); // 초록색
        reportCaseToPolygonStylesMap.put("reportCase3", PolygonStylesSet.from(PolygonStyles.from(Color.parseColor("#0000ff")))); // 파란색
        // 필요에 따라 추가 케이스를 여기에 추가
    }

    //위험 위치 표시 라벨 추가하는 웨이브 추가 메소드
    private void DangerLabelWave(LatLng labelPosition, String reportCase) {
        if (labelPosition != null) {
            // 라벨 스타일 생성
            LabelStyles styles = kakaoMap.getLabelManager()
                    .addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.pink_marker)
                            .setIconTransition(LabelTransition.from(Transition.None, Transition.None))));
            if (labelLayer != null) {
                // 라벨 생성
                labelLayer.addLabel(LabelOptions.from("label_" + System.currentTimeMillis(), labelPosition).setStyles(styles));
                // circleWave 애니메이션을 위한 Polygon 및 Animator 미리 생성
                animationPolygon = kakaoMap.getShapeManager().getLayer().addPolygon(
                        PolygonOptions.from("circlePolygon_"+ System.currentTimeMillis())
                                .setDotPoints(DotPoints.fromCircle(labelPosition, 1.0f))
                                .setStylesSet(reportCaseToPolygonStylesMap.get(reportCase)));
                // circleWave 실제 생성 부분
                CircleWaves circleWaves = CircleWaves.from("circleWaveAnim_"+ System.currentTimeMillis(),
                                CircleWave.from(1, 0, 0, 200))
                        .setHideShapeAtStop(false)
                        .setInterpolation(Interpolation.CubicInOut)
                        .setDuration(3000).setRepeatCount(1600);
                shapeAnimator = kakaoMap.getShapeManager().addAnimator(circleWaves);
                shapeAnimator.addPolygons(animationPolygon);
                shapeAnimator.setHideShapeAtStop(true);
                shapeAnimator.start();
            }
            else{
                Toast.makeText(MapActivity.this, "labelLayer error.", Toast.LENGTH_SHORT).show();
            }
        }
        else{
            Toast.makeText(MapActivity.this, "위치를 찾지 못 했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private final ActivityResultLauncher<Intent> getReportLocation = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == RESULT_OK){
                    // todo - ...
                    Intent data = result.getData();
                    if(data!=null) {
                        double latitude = data.getDoubleExtra("latitude", 0.0);
                        double longitude = data.getDoubleExtra("longitude", 0.0);
                        LatLng labelLocation = LatLng.from(latitude, longitude);
                        String temporaryReportCase = "reportCase1";
                        DangerLabelWave(labelLocation,temporaryReportCase);
                    }
                }
            }
    );


}


