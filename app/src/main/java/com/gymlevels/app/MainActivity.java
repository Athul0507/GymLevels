package com.gymlevels.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int BLACK = Color.rgb(3, 7, 8);
    private static final int PANEL = Color.rgb(16, 21, 22);
    private static final int PANEL_2 = Color.rgb(24, 31, 32);
    private static final int WHITE = Color.rgb(245, 255, 252);
    private static final int MUTED = Color.rgb(120, 143, 139);
    private static final int LIME = Color.rgb(0, 226, 167);
    private static final int CYAN = Color.rgb(0, 178, 136);
    private static final int INK = Color.rgb(245, 255, 252);
    private static final int RED = Color.rgb(0, 226, 167);
    private static final int HAIRLINE = Color.rgb(36, 51, 52);
    private static final int REQ_CAMERA = 17;
    private static final int REQ_CAMERA_PERMISSION = 18;
    private static final int REQ_STEPS_PERMISSION = 19;

    private final String[] tabs = {"Home", "Plan", "Body", "Photos", "Steps"};
    private final int[] plateUnits = {200, 150, 100, 50, 25};
    private final int[] plateColors = {
            Color.rgb(41, 175, 255), Color.rgb(242, 199, 68), Color.rgb(70, 235, 128),
            Color.rgb(255, 93, 93), Color.rgb(198, 207, 219)
    };

    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout tabRow;
    private FrameLayout content;
    private int currentTab;
    private boolean exerciseDetailOpen;
    private boolean galleryOpen;
    private boolean activityRecordOpen;
    private HorizontalScrollView homeCarousel;
    private LinearLayout homeCarouselRow;
    private String homeFocusedDay = "";
    private final List<ExercisePlan> routine = new ArrayList<>();
    private final List<String> muscleGroups = new ArrayList<>();
    private ExercisePlan selectedExercise;
    private String selectedMuscleGroup = "Full Body";
    private final LinkedHashMap<Integer, Integer> loadedPlates = new LinkedHashMap<>();
    private WeightModelView barbellView;
    private TextView loadText;
    private boolean dumbbellMode;
    private Uri pendingPhotoUri;
    private Handler handler = new Handler();
    private Runnable photoPlayer;
    private CameraCaptureView cameraCaptureView;

    private SensorManager sensorManager;
    private Sensor stepCounter;
    private TextView stepText;
    private StepRingView stepRingView;
    private int latestSteps = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);

        prefs = getSharedPreferences("gymlevels_local", MODE_PRIVATE);
        loadRoutineConfig();
        selectedMuscleGroup = prefs.getString("selected_muscle_group", muscleGroups.isEmpty() ? "Full Body" : muscleGroups.get(0));
        selectedExercise = routine.get(0);
        for (int unit : plateUnits) {
            loadedPlates.put(unit, 0);
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepCounter = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        buildShell();
        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepCounter != null && hasActivityRecognition()) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        stopCameraPreview();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        if (exerciseDetailOpen) {
            if (activityRecordOpen) showActivityRecordPage(currentDayName());
            else showTab(0);
            return;
        }
        if (activityRecordOpen) {
            showTab(0);
            return;
        }
        if (galleryOpen) {
            showTab(3);
            return;
        }
        if (currentTab != 0) {
            showTab(0);
            return;
        }
        super.onBackPressed();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(16), statusBarHeight() + dp(12), dp(16), dp(8));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        top.addView(titles, new LinearLayout.LayoutParams(0, -1, 1));

        TextView name = label("GYMLEVELS", 30, INK, true);
        titles.addView(name);
        TextView sub = label("NEURAL TRAINING LOG", 11, RED, true);
        sub.setLetterSpacing(0.08f);
        titles.addView(sub);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER);
        tabRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabRow.setBackground(makeRound(PANEL, dp(24), HAIRLINE));
        root.addView(tabRow, new LinearLayout.LayoutParams(-1, dp(58)));
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            TextView tab = navItem(tabs[i], iconForTab(i));
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> showTab(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            tabRow.addView(tab, lp);
        }
    }

    private void showTab(int index) {
        stopCameraPreview();
        exerciseDetailOpen = false;
        galleryOpen = false;
        activityRecordOpen = false;
        currentTab = index;
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView tab = (TextView) tabRow.getChildAt(i);
            tab.setTextColor(i == currentTab ? BLACK : INK);
            tab.setBackground(makeRound(i == currentTab ? RED : Color.TRANSPARENT, dp(20), Color.TRANSPARENT));
        }
        content.removeAllViews();
        handler.removeCallbacksAndMessages(null);
        if (index == 0) buildHome();
        if (index == 1) buildPlan();
        if (index == 2) buildBody();
        if (index == 3) buildPhotos();
        if (index == 4) buildSteps();
    }

    private void buildHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        page.addView(label("Week Deck", 30, INK, true));
        page.addView(label("Swipe the cards. Planned days glow.", 13, MUTED, false));
        addWeekDeck(page);

        page.addView(sectionTitle("Today's Plan"));
        List<ExercisePlan> todays = plannedExercisesForDay(currentDayName());
        if (todays.isEmpty()) {
            page.addView(historyLine("No plan today", "Open Plan to stack your cards", ""));
        }
        for (ExercisePlan exercise : todays) {
            page.addView(exerciseRow(exercise));
        }
    }

    private void buildPlan() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        page.addView(label("Plan", 30, INK, true));
        page.addView(label("Pick a day, choose up to two muscle groups, then save the week.", 13, MUTED, false));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.setMargins(0, dp(16), 0, 0);
        page.addView(grid, gridLp);

        String[] days = weekDays();
        for (int i = 0; i < days.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row, new LinearLayout.LayoutParams(-1, dp(156)));
            row.addView(planDayCard(days[i]), new LinearLayout.LayoutParams(0, -1, 1));
            if (i + 1 < days.length) {
                addGap(row, 10, true);
                row.addView(planDayCard(days[i + 1]), new LinearLayout.LayoutParams(0, -1, 1));
            }
            LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) row.getLayoutParams();
            rowLp.setMargins(0, 0, 0, dp(10));
            row.setLayoutParams(rowLp);
        }
    }

    private void showExerciseDetails(ExercisePlan exercise) {
        stopCameraPreview();
        exerciseDetailOpen = true;
        activityRecordOpen = false;
        selectedExercise = exercise;
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        Button back = actionButton("Back to schedule");
        back.setOnClickListener(v -> showTab(0));
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView title = label(selectedExercise.name, 31, INK, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(12), 0, 0);
        page.addView(title, titleLp);
        page.addView(label(selectedExercise.displayCue(), 13, MUTED, false));

        FrameLayout videoPanel = videoPanel();
        LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(-1, dp(190));
        videoLp.setMargins(0, dp(14), 0, dp(14));
        page.addView(videoPanel, videoLp);

        page.addView(sectionTitle("Record Activity"));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeRow.setBackground(makeRound(PANEL, dp(22), HAIRLINE));
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(-1, dp(52));
        modeLp.setMargins(0, 0, 0, dp(12));
        page.addView(modeRow, modeLp);
        TextView barbellMode = recordModeButton("Barbell", false);
        TextView dumbbellModeButton = recordModeButton("Dumbbell", true);
        barbellMode.setOnClickListener(v -> setRecordMode(false, barbellMode, dumbbellModeButton));
        dumbbellModeButton.setOnClickListener(v -> setRecordMode(true, barbellMode, dumbbellModeButton));
        modeRow.addView(barbellMode, new LinearLayout.LayoutParams(0, -1, 1));
        modeRow.addView(dumbbellModeButton, new LinearLayout.LayoutParams(0, -1, 1));

        barbellView = new WeightModelView(this);
        barbellView.setPlateState(loadedPlates, plateColors, dumbbellMode);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(360));
        barLp.setMargins(0, 0, 0, dp(8));
        page.addView(barbellView, barLp);

        loadText = label("", 16, INK, true);
        loadText.setGravity(Gravity.CENTER);
        page.addView(loadText, new LinearLayout.LayoutParams(-1, dp(34)));
        updateLoadText();

        LinearLayout plateRow = new LinearLayout(this);
        plateRow.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(plateRow, new LinearLayout.LayoutParams(-1, dp(58)));
        for (int i = 0; i < plateUnits.length; i++) {
            int unit = plateUnits[i];
            TextView plate = pill(weightLabel(unit));
            plate.setTextColor(WHITE);
            plate.setBackground(makeRound(plateColors[i], dp(16), Color.TRANSPARENT));
            plate.setOnClickListener(v -> {
                loadedPlates.put(unit, loadedPlates.get(unit) + 1);
                barbellView.refreshLoad();
                updateLoadText();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            lp.setMargins(dp(3), dp(8), dp(3), 0);
            plateRow.addView(plate, lp);
        }

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(controls, new LinearLayout.LayoutParams(-1, dp(58)));
        Button unload = actionButton("Unload");
        unload.setOnClickListener(v -> {
            for (int weight : loadedPlates.keySet()) loadedPlates.put(weight, 0);
            barbellView.refreshLoad();
            updateLoadText();
        });
        controls.addView(unload, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(controls, 8, true);

        EditText reps = input("Reps");
        controls.addView(reps, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(controls, 8, true);

        Button save = actionButton("Save Set");
        save.setTextColor(BLACK);
        save.setBackground(makeRound(RED, dp(4), RED));
        save.setOnClickListener(v -> saveSet(reps.getText().toString()));
        controls.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1));

        page.addView(sectionTitle("Set Progress"));
        SetProgressChartView chart = new SetProgressChartView(this);
        chart.setEntries(exerciseSets(selectedExercise.name));
        page.addView(chart, new LinearLayout.LayoutParams(-1, dp(220)));

        Button history = actionButton("Historic Details");
        history.setOnClickListener(v -> showSetHistoryModal());
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(-1, dp(48));
        historyLp.setMargins(0, dp(12), 0, 0);
        page.addView(history, historyLp);

        page.addView(sectionTitle("Recent Sets"));
        List<JSONObject> setEntries = exerciseSets(selectedExercise.name);
        for (int i = setEntries.size() - 1; i >= 0 && i >= setEntries.size() - 4; i--) {
            JSONObject entry = setEntries.get(i);
            page.addView(historyLine(entry.optString("date"), entry.optString("weight") + " kg x " + entry.optString("reps"), ""));
        }
    }

    private void buildBody() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        page.addView(label("Body Weight", 24, INK, true));
        page.addView(label("Daily trend", 13, MUTED, false));

        WeightChartView chart = new WeightChartView(this);
        chart.setEntries(readArrayObjects("body_weight"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(260));
        lp.setMargins(0, dp(16), 0, dp(12));
        page.addView(chart, lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        EditText weight = input("kg");
        row.addView(weight, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(row, 10, true);
        Button save = actionButton("Log Weight");
        save.setTextColor(BLACK);
        save.setBackground(makeRound(RED, dp(4), RED));
        save.setOnClickListener(v -> {
            double kg = parseDouble(weight.getText().toString());
            if (kg <= 0) {
                toast("Enter your body weight");
                return;
            }
            JSONObject obj = new JSONObject();
            try {
                obj.put("date", today());
                obj.put("weight", kg);
                append("body_weight", obj);
                showTab(2);
            } catch (JSONException ignored) {
            }
        });
        row.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1));

        page.addView(sectionTitle("History"));
        List<JSONObject> entries = readArrayObjects("body_weight");
        for (int i = entries.size() - 1; i >= 0; i--) {
            JSONObject entry = entries.get(i);
            page.addView(historyLine(entry.optString("date"), entry.optString("weight") + " kg", ""));
        }
    }

    private void buildPhotos() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        page.addView(label("Progress Photos", 24, INK, true));
        page.addView(label("Live camera guide with your latest reference below", 13, MUTED, false));

        List<JSONObject> photos = readArrayObjects("photos");
        FrameLayout cameraPanel = new FrameLayout(this);
        cameraPanel.setBackground(makeRound(PANEL, dp(8), HAIRLINE));
        LinearLayout.LayoutParams cameraLp = new LinearLayout.LayoutParams(-1, dp(420));
        cameraLp.setMargins(0, dp(16), 0, dp(12));
        page.addView(cameraPanel, cameraLp);
        if (hasCameraPermission()) {
            cameraCaptureView = new CameraCaptureView(this);
            cameraPanel.addView(cameraCaptureView, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView request = label("Camera access needed for live guide", 18, INK, true);
            request.setGravity(Gravity.CENTER);
            cameraPanel.addView(request, new FrameLayout.LayoutParams(-1, -1));
        }

        PhotoFrameView latestFrame = new PhotoFrameView(this);
        if (!photos.isEmpty()) latestFrame.setImageUri(Uri.parse(photos.get(photos.size() - 1).optString("uri")));
        LinearLayout.LayoutParams latestLp = new LinearLayout.LayoutParams(-1, dp(240));
        latestLp.setMargins(0, 0, 0, dp(12));
        page.addView(latestFrame, latestLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(actions, new LinearLayout.LayoutParams(-1, dp(56)));

        Button capture = actionButton("Capture Today");
        capture.setTextColor(BLACK);
        capture.setBackground(makeRound(RED, dp(4), RED));
        capture.setOnClickListener(v -> startTimedCapture());
        actions.addView(capture, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(actions, 10, true);
        Button play = actionButton("Timelapse");
        play.setOnClickListener(v -> playPhotos(latestFrame, photos));
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button allPhotos = actionButton("Show All Photos");
        allPhotos.setOnClickListener(v -> buildPhotoGallery());
        page.addView(allPhotos, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void buildSteps() {
        LinearLayout page = page();
        content.addView(page);
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        page.addView(label("Steps", 24, INK, true));
        page.addView(label(stepCounter == null ? "No step counter sensor found" : "Native on-device sensor", 13, MUTED, false));

        stepRingView = new StepRingView(this);
        stepRingView.setSteps(latestSteps < 0 ? prefs.getInt("today_steps", 0) : latestSteps);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(-1, dp(340));
        ringLp.setMargins(0, dp(24), 0, dp(10));
        page.addView(stepRingView, ringLp);

        stepText = label(stepLabel(), 42, INK, true);
        stepText.setGravity(Gravity.CENTER);
        page.addView(stepText, new LinearLayout.LayoutParams(-1, dp(64)));

        Button permission = actionButton("Enable Step Access");
        permission.setTextColor(BLACK);
        permission.setBackground(makeRound(RED, dp(4), RED));
        permission.setOnClickListener(v -> requestStepPermission());
        page.addView(permission, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void startTimedCapture() {
        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        if (cameraCaptureView == null) {
            legacyCameraFallback();
            return;
        }
        cameraCaptureView.startCountdownCapture();
    }

    private void saveSet(String repsText) {
        int reps = (int) parseDouble(repsText);
        if (reps <= 0) {
            toast("Enter reps");
            return;
        }
        JSONObject obj = new JSONObject();
        try {
            obj.put("date", today());
            obj.put("exercise", selectedExercise.name);
            obj.put("muscleGroup", primaryMuscleGroup(selectedExercise));
            obj.put("weight", totalLoad());
            obj.put("reps", reps);
            append("sets", obj);
            toast("Set saved");
            showExerciseDetails(selectedExercise);
        } catch (JSONException ignored) {
        }
    }

    private void capturePhoto() {
        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        if (cameraCaptureView != null && cameraCaptureView.capture()) {
            return;
        }
        legacyCameraFallback();
    }

    private void capturePhotoNow() {
        if (cameraCaptureView != null && cameraCaptureView.capture()) return;
        legacyCameraFallback();
    }

    private void saveCameraBytes(byte[] data) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "gymlevels_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GymLevels");
        }
        pendingPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingPhotoUri == null) {
            toast("Could not create photo file");
            return;
        }
        try {
            OutputStream stream = getContentResolver().openOutputStream(pendingPhotoUri);
            if (stream == null) {
                toast("Could not save photo");
                return;
            }
            byte[] output = normalizeCameraJpeg(data);
            stream.write(output);
            stream.close();
            JSONObject obj = new JSONObject();
            obj.put("date", today());
            obj.put("uri", pendingPhotoUri.toString());
            append("photos", obj);
            toast("Photo saved");
            showTab(3);
        } catch (Exception e) {
            toast("Could not save photo");
        }
    }

    private byte[] normalizeCameraJpeg(byte[] data) {
        try {
            Bitmap source = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (source == null) return data;
            boolean rotate = source.getWidth() > source.getHeight();
            Bitmap output = source;
            if (rotate) {
                Matrix matrix = new Matrix();
                matrix.postRotate(-90);
                output = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            output.compress(Bitmap.CompressFormat.JPEG, 92, buffer);
            if (output != source) source.recycle();
            output.recycle();
            return buffer.toByteArray();
        } catch (Exception e) {
            return data;
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void stopCameraPreview() {
        if (cameraCaptureView != null) {
            cameraCaptureView.stopCamera();
            cameraCaptureView = null;
        }
    }

    private void buildPhotoGallery() {
        stopCameraPreview();
        galleryOpen = true;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        Button back = actionButton("Back to camera");
        back.setOnClickListener(v -> showTab(3));
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
        page.addView(sectionTitle("All Photos"));

        List<JSONObject> photos = readArrayObjects("photos");
        for (JSONObject photo : photos) {
            PhotoFrameView frame = new PhotoFrameView(this);
            frame.setImageUri(Uri.parse(photo.optString("uri")));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(320));
            lp.setMargins(0, 0, 0, dp(12));
            page.addView(frame, lp);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView date = galleryMetaLine(photo.optString("date"));
            row.addView(date, new LinearLayout.LayoutParams(0, dp(46), 1));
            addGap(row, 8, true);
            Button delete = actionButton("Delete");
            delete.setTextColor(BLACK);
            delete.setBackground(makeRound(RED, dp(6), RED));
            String uri = photo.optString("uri");
            delete.setOnClickListener(v -> deletePhoto(uri));
            row.addView(delete, new LinearLayout.LayoutParams(dp(96), dp(46)));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(54));
            rowLp.setMargins(0, 0, 0, dp(8));
            page.addView(row, rowLp);
        }
        if (photos.isEmpty()) {
            page.addView(historyLine("No photos captured yet", "", ""));
        }
    }

    private void deletePhoto(String uri) {
        JSONArray kept = new JSONArray();
        JSONArray photos = readArray("photos");
        for (int i = 0; i < photos.length(); i++) {
            JSONObject photo = photos.optJSONObject(i);
            if (photo == null) continue;
            if (!uri.equals(photo.optString("uri"))) kept.put(photo);
        }
        prefs.edit().putString("photos", kept.toString()).apply();
        try {
            getContentResolver().delete(Uri.parse(uri), null, null);
        } catch (Exception ignored) {
        }
        toast("Photo deleted");
        buildPhotoGallery();
    }

    private void showSetHistoryModal() {
        Dialog dialog = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(makeRound(PANEL, dp(8), HAIRLINE));
        dialog.setContentView(box);

        box.addView(label(selectedExercise.name + " History", 22, INK, true));
        List<JSONObject> sets = exerciseSets(selectedExercise.name);
        for (int i = sets.size() - 1; i >= 0; i--) {
            JSONObject entry = sets.get(i);
            box.addView(historyLine(entry.optString("date"), entry.optString("weight") + " kg", entry.optString("reps") + " reps"));
        }
        if (sets.isEmpty()) box.addView(historyLine("No saved sets yet", "", ""));

        Button close = actionButton("Close");
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(46));
        closeLp.setMargins(0, dp(12), 0, 0);
        box.addView(close, closeLp);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -2);
        }
    }

    private void addCalendarRow(LinearLayout page) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(86));
        rowLp.setMargins(0, dp(18), 0, dp(8));
        page.addView(row, rowLp);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            Date date = cal.getTime();
            String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
            String day = new SimpleDateFormat("EEE", Locale.US).format(date).toUpperCase(Locale.US);
            String number = new SimpleDateFormat("d", Locale.US).format(date);
            boolean trained = trainedOn(key);
            String group = trainedGroupOn(key);
            String status = trained ? (group.isEmpty() ? "DONE" : group.toUpperCase(Locale.US)) : "\u00D7";

            TextView cell = label(day + "\n" + number + "\n" + status, 11, trained ? BLACK : INK, true);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(makeRound(trained ? RED : PANEL, dp(8), trained ? RED : HAIRLINE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            row.addView(cell, lp);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void addWeekDeck(LinearLayout page) {
        homeCarousel = new HorizontalScrollView(this);
        homeCarousel.setHorizontalScrollBarEnabled(false);
        homeCarousel.setOverScrollMode(View.OVER_SCROLL_NEVER);
        homeCarousel.setClipToPadding(false);
        homeCarouselRow = new LinearLayout(this);
        homeCarouselRow.setOrientation(LinearLayout.HORIZONTAL);
        homeCarouselRow.setGravity(Gravity.CENTER_VERTICAL);
        homeCarousel.addView(homeCarouselRow);
        LinearLayout.LayoutParams deckLp = new LinearLayout.LayoutParams(-1, dp(430));
        deckLp.setMargins(0, dp(16), 0, dp(6));
        page.addView(homeCarousel, deckLp);

        String todayName = currentDayName();
        String[] days = weekDays();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.max(dp(218), screenWidth - dp(170));
        int sideInset = Math.max(dp(36), (screenWidth - cardWidth) / 2 - dp(18));
        homeFocusedDay = todayName;
        Space lead = new Space(this);
        homeCarouselRow.addView(lead, new LinearLayout.LayoutParams(sideInset, 1));
        for (int i = 0; i < days.length; i++) {
            View card = homeDayCard(days[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth, dp(382));
            lp.setMargins(dp(8), dp(16), dp(8), dp(16));
            homeCarouselRow.addView(card, lp);
        }
        Space tail = new Space(this);
        homeCarouselRow.addView(tail, new LinearLayout.LayoutParams(sideInset, 1));
        homeCarousel.post(() -> {
            scrollHomeCarouselTo(todayName, false);
            updateHomeCarouselFocus();
        });
        homeCarousel.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> updateHomeCarouselFocus());
    }

    private TextView homeDayCard(String day) {
        List<String> groups = readPlanGroups(day);
        boolean planned = !groups.isEmpty();
        String detail = planned ? join(groups, " + ") : "REST";
        TextView card = label(day.toUpperCase(Locale.US) + "\n\n" + detail.toUpperCase(Locale.US), 24, planned ? BLACK : INK, true);
        card.setTag(day);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(16), dp(14), dp(16));
        card.setBackground(makeRound(planned ? RED : PANEL, dp(14), planned ? RED : HAIRLINE));
        card.setOnClickListener(v -> {
            if (!day.equals(homeFocusedDay)) {
                scrollHomeCarouselTo(day);
                return;
            }
            if (day.equals(currentDayName()) && planned) showActivityRecordPage(day);
            else showPlanGroupModal(day);
        });
        return card;
    }

    private void updateHomeCarouselFocus() {
        if (homeCarousel == null || homeCarouselRow == null) return;
        int center = homeCarousel.getScrollX() + homeCarousel.getWidth() / 2;
        int bestDistance = Integer.MAX_VALUE;
        TextView best = null;
        for (int i = 0; i < homeCarouselRow.getChildCount(); i++) {
            View child = homeCarouselRow.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            int childCenter = child.getLeft() + child.getWidth() / 2;
            int distance = Math.abs(childCenter - center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (TextView) child;
            }
        }
        if (best == null) return;
        homeFocusedDay = String.valueOf(best.getTag());
        for (int i = 0; i < homeCarouselRow.getChildCount(); i++) {
            View child = homeCarouselRow.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView card = (TextView) child;
            String day = String.valueOf(card.getTag());
            boolean focus = day.equals(homeFocusedDay);
            boolean planned = !readPlanGroups(day).isEmpty();
            card.setAlpha(focus ? 1f : 0.62f);
            card.setScaleX(focus ? 1f : 0.84f);
            card.setScaleY(focus ? 1f : 0.84f);
            card.setTranslationY(focus ? 0 : dp(34));
            if (Build.VERSION.SDK_INT >= 21) card.setElevation(focus ? dp(14) : dp(1));
            card.setTextSize(focus ? 26 : 19);
            String marker = day.equals(currentDayName()) ? "\nTODAY\n\n" : "\n\n";
            String detail = planned ? join(readPlanGroups(day), " + ") : "REST";
            card.setText(day.toUpperCase(Locale.US) + marker + detail.toUpperCase(Locale.US));
            card.setBackground(makeRound(planned ? RED : PANEL, dp(14), focus ? (planned ? RED : RED) : HAIRLINE));
            card.setTextColor(planned ? BLACK : INK);
        }
    }

    private void scrollHomeCarouselTo(String day) {
        scrollHomeCarouselTo(day, true);
    }

    private void scrollHomeCarouselTo(String day, boolean animate) {
        if (homeCarousel == null || homeCarouselRow == null) return;
        for (int i = 0; i < homeCarouselRow.getChildCount(); i++) {
            View child = homeCarouselRow.getChildAt(i);
            if (day.equals(String.valueOf(child.getTag()))) {
                int target = child.getLeft() - (homeCarousel.getWidth() - child.getWidth()) / 2;
                if (animate) homeCarousel.smoothScrollTo(Math.max(0, target), 0);
                else homeCarousel.scrollTo(Math.max(0, target), 0);
                return;
            }
        }
    }

    private TextView planDayCard(String day) {
        List<String> groups = readPlanGroups(day);
        boolean planned = !groups.isEmpty();
        String text = day.toUpperCase(Locale.US) + "\n\n" + (planned ? join(groups, "\n") : "Tap to plan");
        TextView card = label(text, 18, planned ? BLACK : INK, true);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(makeRound(planned ? RED : PANEL, dp(10), planned ? RED : HAIRLINE));
        card.setOnClickListener(v -> showPlanGroupModal(day));
        return card;
    }

    private void showActivityRecordPage(String day) {
        stopCameraPreview();
        exerciseDetailOpen = false;
        galleryOpen = false;
        activityRecordOpen = true;
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        Button back = actionButton("Back to week deck");
        back.setOnClickListener(v -> showTab(0));
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
        page.addView(label(day + " Activity", 30, INK, true));
        List<String> groups = readPlanGroups(day);
        page.addView(label(groups.isEmpty() ? "No plan saved" : join(groups, " + "), 13, RED, true));

        List<ExercisePlan> exercises = plannedExercisesForDay(day);
        if (exercises.isEmpty()) {
            page.addView(historyLine("No exercises selected", "Open Plan to choose exercises", ""));
            return;
        }
        page.addView(sectionTitle("Tap an exercise"));
        for (ExercisePlan exercise : exercises) {
            page.addView(exerciseRow(exercise));
        }
    }

    private void showPlanGroupModal(String day) {
        Dialog dialog = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(makeRound(PANEL, dp(12), HAIRLINE));
        dialog.setContentView(box);

        box.addView(label(day + " Plan", 24, INK, true));
        box.addView(label("Choose up to two muscle groups", 13, MUTED, false));
        List<String> selected = new ArrayList<>(readPlanGroups(day));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, -2);
        chipsLp.setMargins(0, dp(14), 0, dp(12));
        box.addView(chips, chipsLp);
        for (String group : muscleGroups) {
            TextView chip = planChoiceChip(group, selected);
            chips.addView(chip);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(actions, new LinearLayout.LayoutParams(-1, dp(50)));
        Button clear = actionButton("Skip Day");
        clear.setOnClickListener(v -> {
            savePlanGroups(day, new ArrayList<>());
            dialog.dismiss();
            showTab(currentTab);
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(actions, 10, true);
        Button next = actionButton("Next");
        next.setTextColor(BLACK);
        next.setBackground(makeRound(RED, dp(6), RED));
        next.setOnClickListener(v -> {
            if (selected.isEmpty()) {
                toast("Choose at least one muscle group");
                return;
            }
            dialog.dismiss();
            showPlanExerciseModal(day, selected);
        });
        actions.addView(next, new LinearLayout.LayoutParams(0, dp(46), 1));

        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -2);
    }

    private TextView planChoiceChip(String group, List<String> selected) {
        TextView chip = label(group, 16, selected.contains(group) ? BLACK : INK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setBackground(makeRound(selected.contains(group) ? RED : PANEL_2, dp(8), selected.contains(group) ? RED : HAIRLINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, 0, 0, dp(8));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            if (selected.contains(group)) selected.remove(group);
            else {
                if (selected.size() >= 2) {
                    toast("Pick up to two");
                    return;
                }
                selected.add(group);
            }
            chip.setTextColor(selected.contains(group) ? BLACK : INK);
            chip.setBackground(makeRound(selected.contains(group) ? RED : PANEL_2, dp(8), selected.contains(group) ? RED : HAIRLINE));
        });
        return chip;
    }

    private void showPlanExerciseModal(String day, List<String> selected) {
        Dialog dialog = new Dialog(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(makeRound(PANEL, dp(12), HAIRLINE));
        scroll.addView(box);
        dialog.setContentView(scroll);
        box.addView(label(day + " Exercises", 24, INK, true));
        box.addView(label(join(selected, " + "), 13, RED, true));
        List<String> selectedExercises = new ArrayList<>(readPlanExerciseNames(day));
        List<ExercisePlan> candidates = exercisesForGroups(selected);
        for (ExercisePlan exercise : candidates) {
            TextView chip = exerciseChoiceChip(exercise, selectedExercises);
            box.addView(chip);
        }
        Button save = actionButton("Save Plan");
        save.setTextColor(BLACK);
        save.setBackground(makeRound(RED, dp(6), RED));
        save.setOnClickListener(v -> {
            if (selectedExercises.isEmpty()) {
                toast("Choose at least one exercise");
                return;
            }
            savePlan(day, selected, selectedExercises);
            dialog.dismiss();
            showTab(currentTab);
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(48));
        saveLp.setMargins(0, dp(12), 0, 0);
        box.addView(save, saveLp);
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -2);
    }

    private TextView exerciseChoiceChip(ExercisePlan exercise, List<String> selectedExercises) {
        boolean active = selectedExercises.contains(exercise.name);
        TextView chip = label(exercise.name + "\n" + primaryMuscleGroup(exercise), 15, active ? BLACK : INK, true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(makeRound(active ? RED : PANEL_2, dp(8), active ? RED : HAIRLINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(64));
        lp.setMargins(0, dp(8), 0, 0);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            if (selectedExercises.contains(exercise.name)) selectedExercises.remove(exercise.name);
            else selectedExercises.add(exercise.name);
            boolean nowActive = selectedExercises.contains(exercise.name);
            chip.setTextColor(nowActive ? BLACK : INK);
            chip.setBackground(makeRound(nowActive ? RED : PANEL_2, dp(8), nowActive ? RED : HAIRLINE));
        });
        return chip;
    }

    private TextView exerciseRow(ExercisePlan exercise) {
        TextView row = label(exercise.name, 18, INK, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackground(makeRound(PANEL, dp(8), HAIRLINE));
        row.setOnClickListener(v -> showExerciseDetails(exercise));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(76));
        lp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView recordModeButton(String text, boolean dumbbell) {
        TextView button = label(text, 14, INK, true);
        button.setGravity(Gravity.CENTER);
        styleRecordModeButton(button, dumbbellMode == dumbbell);
        return button;
    }

    private void setRecordMode(boolean dumbbell, TextView barbellButton, TextView dumbbellButton) {
        if (dumbbellMode == dumbbell) return;
        dumbbellMode = dumbbell;
        styleRecordModeButton(barbellButton, !dumbbellMode);
        styleRecordModeButton(dumbbellButton, dumbbellMode);
        if (barbellView != null) barbellView.setMode(dumbbellMode);
        updateLoadText();
    }

    private void styleRecordModeButton(TextView button, boolean active) {
        button.setTextColor(active ? BLACK : INK);
        button.setBackground(makeRound(active ? RED : Color.TRANSPARENT, dp(18), Color.TRANSPARENT));
    }

    private FrameLayout videoPanel() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(makeRound(BLACK, dp(8), BLACK));
        WebView video = new WebView(this);
        WebSettings settings = video.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        video.setWebChromeClient(new WebChromeClient());
        video.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("youtube.com/watch")) {
                    view.evaluateJavascript(youtubeWatchPageCropScript(), null);
                }
            }
        });
        video.loadUrl(videoUrlForWebView(selectedExercise.videoUrl));
        panel.addView(video, new FrameLayout.LayoutParams(-1, -1));

        Button open = actionButton("Open Video");
        open.setTextColor(BLACK);
        open.setBackground(makeRound(RED, dp(18), RED));
        open.setOnClickListener(v -> openVideo(selectedExercise.videoUrl));
        FrameLayout.LayoutParams openLp = new FrameLayout.LayoutParams(dp(132), dp(42), Gravity.BOTTOM | Gravity.RIGHT);
        openLp.setMargins(0, 0, dp(10), dp(10));
        panel.addView(open, openLp);
        return panel;
    }

    private String videoUrlForWebView(String url) {
        if (url == null || url.trim().isEmpty()) return "https://m.youtube.com";
        String clean = url.trim();
        String videoId = youtubeVideoId(clean);
        if (!videoId.isEmpty()) return "https://m.youtube.com/watch?v=" + videoId;
        if (clean.contains("youtube.com/results") && clean.contains("search_query=")) {
            String query = youtubeSearchQuery(clean);
            try {
                return "https://m.youtube.com/results?search_query=" + URLEncoder.encode(URLDecoder.decode(query, "UTF-8"), "UTF-8");
            } catch (Exception ignored) {
                return "https://m.youtube.com/results?search_query=" + query;
            }
        }
        return clean.replace("www.youtube.com", "m.youtube.com");
    }

    private String youtubeWatchPageCropScript() {
        return "(function(){"
                + "var css='html,body{margin:0!important;padding:0!important;height:100%!important;overflow:hidden!important;background:#000!important;}'"
                + "+'ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer,ytm-single-column-watch-next-results-renderer,ytm-engagement-panel-section-list-renderer,#secondary,#below,#related,#comments,#meta,#info,#actions{display:none!important;}'"
                + "+'#player-container-id,#player-container,ytm-player,#player,.html5-video-player{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;max-height:none!important;background:#000!important;z-index:2147483647!important;}'"
                + "+'video{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;object-fit:contain!important;background:#000!important;}';"
                + "var style=document.getElementById('gymlevels-video-only-style');"
                + "if(!style){style=document.createElement('style');style.id='gymlevels-video-only-style';document.head.appendChild(style);}"
                + "style.textContent=css;"
                + "window.scrollTo(0,0);"
                + "})();";
    }

    private String youtubeVideoId(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            if (host.contains("youtu.be")) {
                String path = uri.getPath();
                return path == null ? "" : path.replace("/", "").trim();
            }
            if (host.contains("youtube.com")) {
                String watchId = uri.getQueryParameter("v");
                if (watchId != null && !watchId.trim().isEmpty()) return watchId.trim();
                List<String> segments = uri.getPathSegments();
                for (int i = 0; i < segments.size(); i++) {
                    String segment = segments.get(i);
                    if (("embed".equals(segment) || "shorts".equals(segment)) && i + 1 < segments.size()) {
                        return segments.get(i + 1).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String youtubeSearchQuery(String url) {
        try {
            Uri uri = Uri.parse(url);
            String query = uri.getQueryParameter("search_query");
            if (query != null && !query.trim().isEmpty()) return query.trim();
        } catch (Exception ignored) {
        }
        String query = url.substring(url.indexOf("search_query=") + "search_query=".length());
        int amp = query.indexOf('&');
        if (amp >= 0) query = query.substring(0, amp);
        return query;
    }

    private void addMuscleGroupPicker(LinearLayout page) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroller.addView(row);
        page.addView(scroller, new LinearLayout.LayoutParams(-1, dp(56)));
        for (String group : muscleGroups) {
            TextView chip = pill(group);
            boolean active = group.equalsIgnoreCase(selectedMuscleGroup);
            chip.setTextColor(active ? BLACK : INK);
            chip.setBackground(makeRound(active ? RED : PANEL_2, dp(18), active ? RED : HAIRLINE));
            chip.setOnClickListener(v -> {
                selectedMuscleGroup = group;
                prefs.edit().putString("selected_muscle_group", selectedMuscleGroup).apply();
                buildHomeScreenFresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.setMargins(0, dp(6), dp(8), dp(8));
            row.addView(chip, lp);
        }
    }

    private void buildHomeScreenFresh() {
        content.removeAllViews();
        buildHome();
    }

    private List<ExercisePlan> selectedRoutine() {
        List<ExercisePlan> todays = new ArrayList<>();
        for (ExercisePlan exercise : routine) {
            if (primaryMuscleGroup(exercise).equalsIgnoreCase(selectedMuscleGroup)) todays.add(exercise);
        }
        return todays.isEmpty() ? routine : todays;
    }

    private List<ExercisePlan> plannedExercisesForDay(String day) {
        List<String> names = readPlanExerciseNames(day);
        List<ExercisePlan> exercises = new ArrayList<>();
        for (ExercisePlan exercise : routine) {
            if (names.contains(exercise.name)) exercises.add(exercise);
        }
        if (!exercises.isEmpty()) return exercises;
        return exercisesForGroups(readPlanGroups(day));
    }

    private List<ExercisePlan> exercisesForGroups(List<String> groups) {
        List<ExercisePlan> exercises = new ArrayList<>();
        for (ExercisePlan exercise : routine) {
            for (String group : groups) {
                if (primaryMuscleGroup(exercise).equalsIgnoreCase(group)) {
                    exercises.add(exercise);
                    break;
                }
            }
        }
        return exercises;
    }

    private List<String> readPlanGroups(String day) {
        List<String> groups = new ArrayList<>();
        try {
            JSONArray array = new JSONObject(prefs.getString("weekly_plan", "{}")).optJSONArray(day);
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String group = array.optString(i, "");
                    if (!group.isEmpty()) groups.add(group);
                }
            }
        } catch (JSONException ignored) {
        }
        return groups;
    }

    private void savePlanGroups(String day, List<String> groups) {
        savePlan(day, groups, new ArrayList<>());
    }

    private List<String> readPlanExerciseNames(String day) {
        List<String> names = new ArrayList<>();
        try {
            JSONObject dayPlan = new JSONObject(prefs.getString("weekly_plan_details", "{}")).optJSONObject(day);
            JSONArray array = dayPlan == null ? null : dayPlan.optJSONArray("exercises");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String name = array.optString(i, "");
                    if (!name.isEmpty()) names.add(name);
                }
            }
        } catch (JSONException ignored) {
        }
        return names;
    }

    private void savePlan(String day, List<String> groups, List<String> exercises) {
        try {
            JSONObject plan = new JSONObject(prefs.getString("weekly_plan", "{}"));
            JSONArray array = new JSONArray();
            for (String group : groups) array.put(group);
            plan.put(day, array);
            JSONObject details = new JSONObject(prefs.getString("weekly_plan_details", "{}"));
            JSONObject dayPlan = new JSONObject();
            JSONArray groupArray = new JSONArray();
            for (String group : groups) groupArray.put(group);
            JSONArray exerciseArray = new JSONArray();
            for (String exercise : exercises) exerciseArray.put(exercise);
            dayPlan.put("groups", groupArray);
            dayPlan.put("exercises", exerciseArray);
            details.put(day, dayPlan);
            prefs.edit().putString("weekly_plan", plan.toString()).putString("weekly_plan_details", details.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private String currentDayName() {
        return new SimpleDateFormat("EEEE", Locale.US).format(new Date());
    }

    private String[] weekDays() {
        return new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    }

    private String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(delimiter);
            builder.append(value);
        }
        return builder.toString();
    }

    private boolean trainedOn(String date) {
        for (JSONObject entry : readArrayObjects("sets")) {
            if (date.equals(entry.optString("date"))) return true;
        }
        return false;
    }

    private String trainedGroupOn(String date) {
        String group = "";
        for (JSONObject entry : readArrayObjects("sets")) {
            if (!date.equals(entry.optString("date"))) continue;
            group = entry.optString("muscleGroup", "");
            if (group.isEmpty()) {
                String exerciseName = entry.optString("exercise");
                for (ExercisePlan exercise : routine) {
                    if (exercise.name.equals(exerciseName)) group = primaryMuscleGroup(exercise);
                }
            }
        }
        return group;
    }

    private String primaryMuscleGroup(ExercisePlan exercise) {
        String group = exercise.muscleGroup == null ? "" : exercise.muscleGroup.split(",")[0].trim();
        if (group.isEmpty()) return "Full Body";
        return capitalize(group);
    }

    private String capitalize(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String clean = value.trim().toLowerCase(Locale.US);
        return clean.substring(0, 1).toUpperCase(Locale.US) + clean.substring(1);
    }

    private List<JSONObject> exerciseSets(String exerciseName) {
        List<JSONObject> matches = new ArrayList<>();
        for (JSONObject entry : readArrayObjects("sets")) {
            if (exerciseName.equals(entry.optString("exercise"))) matches.add(entry);
        }
        return matches;
    }

    private String iconForTab(int index) {
        if (index == 0) return "\u2302";
        if (index == 1) return "\u25C7";
        if (index == 2) return "\u25CC";
        if (index == 3) return "\u25A3";
        return "\u2301";
    }

    private TextView navItem(String label, String icon) {
        TextView view = label(icon + "\n" + label, 11, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private String assetUrl(String path) {
        return "file:///android_asset/" + path;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void legacyCameraFallback() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "gymlevels_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GymLevels");
        }
        pendingPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingPhotoUri == null) {
            toast("Could not create photo file");
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
        if (intent.resolveActivity(getPackageManager()) == null) {
            toast("No camera app found");
            return;
        }
        startActivityForResult(intent, REQ_CAMERA);
    }

    private void playPhotos(PhotoFrameView frame, List<JSONObject> photos) {
        if (photos.size() < 2) {
            toast("Capture at least two photos");
            return;
        }
        final int[] index = {0};
        photoPlayer = new Runnable() {
            @Override
            public void run() {
                frame.setImageUri(Uri.parse(photos.get(index[0] % photos.size()).optString("uri")));
                index[0]++;
                handler.postDelayed(this, 350);
            }
        };
        handler.post(photoPlayer);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK && pendingPhotoUri != null) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("date", today());
                obj.put("uri", pendingPhotoUri.toString());
                append("photos", obj);
                toast("Photo saved");
                showTab(3);
            } catch (JSONException ignored) {
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showTab(3);
        }
        if (requestCode == REQ_STEPS_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (stepCounter != null) {
                sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            }
            showTab(4);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
        int totalSinceBoot = (int) event.values[0];
        String baselineKey = "step_baseline_" + today();
        int baseline = prefs.getInt(baselineKey, -1);
        if (baseline < 0) {
            baseline = totalSinceBoot;
            prefs.edit().putInt(baselineKey, baseline).apply();
        }
        latestSteps = Math.max(0, totalSinceBoot - baseline);
        prefs.edit().putInt("today_steps", latestSteps).apply();
        if (stepText != null) stepText.setText(stepLabel());
        if (stepRingView != null) {
            stepRingView.setSteps(latestSteps);
            stepRingView.invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private boolean hasActivityRecognition() {
        return Build.VERSION.SDK_INT < 29 || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStepPermission() {
        if (Build.VERSION.SDK_INT >= 29 && !hasActivityRecognition()) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ_STEPS_PERMISSION);
        } else {
            toast(stepCounter == null ? "No step sensor on this device" : "Step access is active");
        }
    }

    private String stepLabel() {
        int steps = latestSteps < 0 ? prefs.getInt("today_steps", 0) : latestSteps;
        return String.format(Locale.US, "%,d", steps);
    }

    private String exerciseCue(String exercise) {
        if ("Goblet Squat".equals(exercise)) return "3 sets • 8-12 reps • Control depth, drive through mid-foot";
        if ("Bench Press".equals(exercise)) return "3 sets • 6-10 reps • Shoulder blades locked, smooth bar path";
        if ("Lat Pulldown".equals(exercise)) return "3 sets • 10-12 reps • Pull elbows down, pause at chest";
        if ("Romanian Deadlift".equals(exercise)) return "3 sets • 8-10 reps • Hips back, neutral spine";
        if ("Seated Row".equals(exercise)) return "3 sets • 10-12 reps • Chest tall, squeeze shoulder blades";
        if ("Overhead Press".equals(exercise)) return "3 sets • 6-10 reps • Brace hard, finish overhead";
        if ("Leg Press".equals(exercise)) return "3 sets • 10-15 reps • Knees track over toes";
        return "3 rounds • 30-45 sec • Tight core, steady breathing";
    }

    private void loadRoutineConfig() {
        routine.clear();
        muscleGroups.clear();
        try {
            String json = readAssetText("config.json");
            JSONObject config = new JSONObject(json);
            Map<String, String> videoOverrides = new LinkedHashMap<>();
            JSONArray exercises = config.optJSONArray("exercises");
            if (exercises != null) {
                for (int i = 0; i < exercises.length(); i++) {
                    JSONObject item = exercises.optJSONObject(i);
                    if (item == null) continue;
                    String name = item.optString("name", "").trim().toLowerCase(Locale.US);
                    String videoUrl = item.optString("videoUrl", "").trim();
                    if (!name.isEmpty() && !youtubeVideoId(videoUrl).isEmpty()) {
                        videoOverrides.put(name, videoUrl);
                    }
                }
            }
            JSONObject grouped = config.optJSONObject("muscleGroups");
            if (grouped != null) {
                JSONArray names = grouped.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String groupName = capitalize(names.optString(i));
                        addMuscleGroup(groupName);
                        JSONArray items = grouped.optJSONArray(names.optString(i));
                        if (items == null) continue;
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.optJSONObject(j);
                            if (item != null) addExerciseFromJson(item, groupName, videoOverrides);
                        }
                    }
                }
            }
            if (grouped == null && exercises != null) {
                for (int i = 0; i < exercises.length(); i++) {
                    JSONObject item = exercises.optJSONObject(i);
                    if (item != null) addExerciseFromJson(item, "", videoOverrides);
                }
            }
        } catch (Exception ignored) {
        }
        if (routine.isEmpty()) {
            routine.add(new ExercisePlan("Goblet Squat", "Monday", 3, "8-12", "Control depth and drive through mid-foot.", "https://www.youtube.com/results?search_query=goblet+squat+proper+form", "quads, glutes, core"));
            routine.add(new ExercisePlan("Bench Press", "Monday", 3, "6-10", "Lock shoulder blades and keep a smooth bar path.", "https://www.youtube.com/results?search_query=bench+press+proper+form", "chest, triceps, shoulders"));
            routine.add(new ExercisePlan("Lat Pulldown", "Wednesday", 3, "10-12", "Pull elbows down and pause near the upper chest.", "https://www.youtube.com/results?search_query=lat+pulldown+proper+form", "back, biceps"));
        }
        for (ExercisePlan exercise : routine) addMuscleGroup(primaryMuscleGroup(exercise));
        if (muscleGroups.isEmpty()) addMuscleGroup("Full Body");
    }

    private void addExerciseFromJson(JSONObject item, String fallbackGroup) {
        addExerciseFromJson(item, fallbackGroup, new LinkedHashMap<>());
    }

    private void addExerciseFromJson(JSONObject item, String fallbackGroup, Map<String, String> videoOverrides) {
        String name = item.optString("name", "").trim();
        if (name.isEmpty()) return;
        String group = item.optString("muscleGroup", fallbackGroup).trim();
        if (group.isEmpty()) group = "full body";
        String configuredVideo = item.optString("videoUrl", "").trim();
        String directOverride = videoOverrides.get(name.toLowerCase(Locale.US));
        if (directOverride != null && !directOverride.isEmpty() && youtubeVideoId(configuredVideo).isEmpty()) {
            configuredVideo = directOverride;
        }
        routine.add(new ExercisePlan(
                name,
                item.optString("day", "Today"),
                item.optInt("sets", 3),
                item.optString("reps", "8-12"),
                item.optString("cue", "Move with control and stop if anything hurts."),
                configuredVideo,
                group
        ));
    }

    private void addMuscleGroup(String group) {
        if (group == null || group.trim().isEmpty()) return;
        for (String existing : muscleGroups) {
            if (existing.equalsIgnoreCase(group.trim())) return;
        }
        muscleGroups.add(group.trim());
    }

    private String readAssetText(String name) throws Exception {
        InputStream stream = getAssets().open(name);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        stream.close();
        return output.toString("UTF-8");
    }

    private void openVideo(String url) {
        if (url == null || url.trim().isEmpty()) {
            toast("No video link in config");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(getPackageManager()) == null) {
            toast("No app can open this video");
            return;
        }
        startActivity(intent);
    }

    private double totalLoad() {
        double total = 0;
        for (Map.Entry<Integer, Integer> entry : loadedPlates.entrySet()) {
            double kg = entry.getKey() / 10.0;
            total += kg * entry.getValue() * (dumbbellMode ? 1 : 2);
        }
        return total;
    }

    private void updateLoadText() {
        String suffix = dumbbellMode ? " kg per dumbbell" : " kg";
        loadText.setText(selectedExercise.name + "  |  " + loadLabel(totalLoad()) + suffix);
    }

    private String weightLabel(int unit) {
        return loadLabel(unit / 10.0) + " kg";
    }

    private String loadLabel(double value) {
        if (Math.abs(value - Math.round(value)) < 0.001) return String.valueOf((int) Math.round(value));
        return String.format(Locale.US, "%.1f", value);
    }

    private void append(String key, JSONObject obj) {
        JSONArray array = readArray(key);
        array.put(obj);
        prefs.edit().putString(key, array.toString()).apply();
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private List<JSONObject> readArrayObjects(String key) {
        JSONArray array = readArray(key);
        List<JSONObject> objects = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null) objects.add(obj);
        }
        return objects;
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(14), 0, dp(28));
        return page;
    }

    private int statusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) return getResources().getDimensionPixelSize(resourceId);
        return 0;
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text, 16, INK, true);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView historyLine(String left, String middle, String right) {
        String text = left + (middle.isEmpty() ? "" : "  |  " + middle) + (right.isEmpty() ? "" : "  |  " + right);
        TextView row = label(text, 14, INK, false);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(makeRound(PANEL, dp(8), HAIRLINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView galleryMetaLine(String text) {
        TextView row = label(text, 14, INK, false);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(makeRound(PANEL, dp(8), HAIRLINE));
        return row;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        view.setFontFeatureSettings("tnum");
        if (bold) view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        else view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    private TextView pill(String text) {
        TextView view = label(text, 12, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackground(makeRound(PANEL_2, dp(4), HAIRLINE));
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(INK);
        button.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        button.setBackground(makeRound(PANEL_2, dp(4), HAIRLINE));
        return button;
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextColor(INK);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(14);
        edit.setSingleLine(true);
        edit.setGravity(Gravity.CENTER);
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setBackground(makeRound(PANEL, dp(4), HAIRLINE));
        return edit;
    }

    private android.graphics.drawable.Drawable makeRound(int color, int radius, int stroke) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void addGap(LinearLayout parent, int widthDp, boolean horizontal) {
        Space space = new Space(this);
        parent.addView(space, horizontal ? new LinearLayout.LayoutParams(dp(widthDp), 1) : new LinearLayout.LayoutParams(1, dp(widthDp)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static class ExercisePlan {
        final String name;
        final String day;
        final int sets;
        final String reps;
        final String cue;
        final String videoUrl;
        final String muscleGroup;

        ExercisePlan(String name, String day, int sets, String reps, String cue, String videoUrl, String muscleGroup) {
            this.name = name;
            this.day = day;
            this.sets = sets;
            this.reps = reps;
            this.cue = cue;
            this.videoUrl = videoUrl;
            this.muscleGroup = muscleGroup;
        }

        String displayCue() {
            return sets + " sets | " + reps + " reps | " + cue;
        }
    }

    public class WeightModelView extends FrameLayout {
        private final WebView webView;
        private final BarbellView fallback;
        private LinkedHashMap<Integer, Integer> plates = new LinkedHashMap<>();
        private int[] colors = new int[0];
        private boolean dumbbellMode;

        public WeightModelView(Activity activity) {
            super(activity);
            setBackground(makeRound(PANEL, dp(18), HAIRLINE));
            webView = new WebView(activity);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            if (Build.VERSION.SDK_INT >= 16) {
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
            }
            addView(webView, new FrameLayout.LayoutParams(-1, -1));

            fallback = new BarbellView(activity);
            fallback.setVisibility(View.GONE);
            addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        }

        public void setPlateState(LinkedHashMap<Integer, Integer> plates, int[] colors, boolean dumbbellMode) {
            this.plates = plates;
            this.colors = colors;
            this.dumbbellMode = dumbbellMode;
            fallback.setPlateState(plates, colors);
            refreshLoad();
        }

        public void setMode(boolean dumbbellMode) {
            this.dumbbellMode = dumbbellMode;
            refreshLoad();
        }

        public void refreshLoad() {
            if (hasAsset("web/weight_model_viewer.html")) {
                String url = assetUrl("web/weight_model_viewer.html") + "?plates=" + urlEncode(plateQuery()) + "&total=" + urlEncode(loadLabel(totalLoad())) + "&mode=" + (dumbbellMode ? "dumbbell" : "barbell");
                webView.setVisibility(View.VISIBLE);
                fallback.setVisibility(View.GONE);
                webView.loadUrl(url);
            } else {
                webView.setVisibility(View.GONE);
                fallback.setVisibility(View.VISIBLE);
            }
        }

        private String plateQuery() {
            StringBuilder builder = new StringBuilder();
            int colorIndex = 0;
            for (Map.Entry<Integer, Integer> entry : plates.entrySet()) {
                if (builder.length() > 0) builder.append(",");
                int color = colors.length > colorIndex ? colors[colorIndex] : Color.LTGRAY;
                builder.append(loadLabel(entry.getKey() / 10.0)).append(":").append(entry.getValue()).append(":").append(String.format(Locale.US, "%06X", 0xFFFFFF & color));
                colorIndex++;
            }
            return builder.toString();
        }

        private boolean hasAsset(String path) {
            try {
                InputStream stream = getAssets().open(path);
                stream.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static class BarbellView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinkedHashMap<Integer, Integer> plates = new LinkedHashMap<>();
        private int[] colors = new int[0];
        private float rotationY = -16f;
        private float lastX;

        public BarbellView(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        public void setPlateState(LinkedHashMap<Integer, Integer> plates, int[] colors) {
            this.plates = plates;
            this.colors = colors;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                rotationY += (event.getX() - lastX) * 0.35f;
                rotationY = Math.max(-55f, Math.min(55f, rotationY));
                lastX = event.getX();
                invalidate();
                return true;
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            paint.setShader(new LinearGradient(0, 0, w, h, Color.rgb(252, 252, 250), Color.rgb(230, 230, 225), Shader.TileMode.CLAMP));
            RectF bg = new RectF(0, 0, w, h);
            canvas.drawRoundRect(bg, 18, 18, paint);
            paint.setShader(null);

            float cx = w / 2f;
            float cy = h * 0.55f;
            float tilt = rotationY / 55f;
            float leftDepth = 1f - tilt * 0.18f;
            float rightDepth = 1f + tilt * 0.18f;
            paint.setStrokeWidth(20);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(172, 176, 180));
            canvas.drawLine(w * 0.12f, cy + tilt * 22f, w * 0.88f, cy - tilt * 22f, paint);
            paint.setStrokeWidth(5);
            paint.setColor(Color.rgb(244, 245, 245));
            canvas.drawLine(w * 0.15f, cy - 10 + tilt * 20f, w * 0.85f, cy - 10 - tilt * 20f, paint);

            drawSleeve(canvas, w * 0.23f, cy + tilt * 15f, leftDepth);
            drawSleeve(canvas, w * 0.77f, cy - tilt * 15f, rightDepth);

            int colorIndex = 0;
            float leftX = w * 0.29f;
            float rightX = w * 0.71f;
            for (Map.Entry<Integer, Integer> entry : plates.entrySet()) {
                int count = entry.getValue();
                int color = colors.length > colorIndex ? colors[colorIndex] : Color.LTGRAY;
                float plateH = 46 + (entry.getKey() / 10f) * 2.8f;
                for (int i = 0; i < count; i++) {
                    drawPlate(canvas, leftX - i * 15, cy + tilt * 17f, plateH * leftDepth, color, false, leftDepth);
                    drawPlate(canvas, rightX + i * 15, cy - tilt * 17f, plateH * rightDepth, color, true, rightDepth);
                }
                leftX -= count * 15 + 4;
                rightX += count * 15 + 4;
                colorIndex++;
            }

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            paint.setTextSize(34);
            paint.setColor(INK);
            canvas.drawText("RECORD ACTIVITY", cx, h * 0.18f, paint);
            paint.setTextSize(14);
            paint.setColor(MUTED);
            canvas.drawText("DRAG TO ROTATE", cx, h * 0.25f, paint);
        }

        private void drawSleeve(Canvas canvas, float x, float cy, float scale) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(142, 153, 170));
            canvas.drawRoundRect(new RectF(x - 28 * scale, cy - 18 * scale, x + 28 * scale, cy + 18 * scale), 14, 14, paint);
            paint.setColor(Color.rgb(229, 235, 242));
            canvas.drawOval(new RectF(x - 30 * scale, cy - 21 * scale, x + 2, cy + 21 * scale), paint);
        }

        private void drawPlate(Canvas canvas, float x, float cy, float height, int color, boolean right, float scale) {
            float width = 19 * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(95, 0, 0, 0));
            canvas.drawOval(new RectF(x - width, cy + height / 2 - 4, x + width, cy + height / 2 + 10), paint);
            paint.setColor(color);
            RectF face = new RectF(x - width, cy - height / 2, x + width, cy + height / 2);
            canvas.drawOval(face, paint);
            paint.setColor(adjust(color, 0.65f));
            if (right) canvas.drawRect(x - width, cy - height / 2, x, cy + height / 2, paint);
            else canvas.drawRect(x, cy - height / 2, x + width, cy + height / 2, paint);
            paint.setColor(Color.argb(160, 255, 255, 255));
            paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawOval(new RectF(x - width + 5, cy - height / 2 + 7, x + width - 5, cy + height / 2 - 7), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private int adjust(int color, float factor) {
            return Color.rgb((int) (Color.red(color) * factor), (int) (Color.green(color) * factor), (int) (Color.blue(color) * factor));
        }
    }

    public static class SetProgressChartView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<JSONObject> entries = new ArrayList<>();

        public SetProgressChartView(Activity activity) {
            super(activity);
        }

        public void setEntries(List<JSONObject> entries) {
            this.entries = entries;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PANEL);
            canvas.drawRoundRect(new RectF(0, 0, w, h), 16, 16, paint);
            paint.setColor(HAIRLINE);
            paint.setStrokeWidth(2);
            for (int i = 1; i < 4; i++) {
                float y = h * i / 4f;
                canvas.drawLine(dpLocal(18), y, w - dpLocal(18), y, paint);
            }
            paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            paint.setTextSize(22);
            paint.setColor(INK);
            if (entries.isEmpty()) {
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Save a set to start progress", w / 2f, h / 2f, paint);
                return;
            }
            double min = Double.MAX_VALUE;
            double max = 0;
            for (JSONObject entry : entries) {
                double value = entry.optDouble("weight");
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            if (max - min < 5) max = min + 5;
            Path line = new Path();
            for (int i = 0; i < entries.size(); i++) {
                JSONObject entry = entries.get(i);
                double value = entry.optDouble("weight");
                float x = dpLocal(24) + (w - dpLocal(48)) * (entries.size() == 1 ? 0.5f : i / (float) (entries.size() - 1));
                float y = h - dpLocal(32) - (float) ((value - min) / (max - min)) * (h - dpLocal(76));
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(RED);
                canvas.drawCircle(x, y, 7, paint);
                paint.setTextSize(12);
                paint.setColor(MUTED);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(entry.optInt("reps") + "r", x, y - 12, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(BLACK);
            canvas.drawPath(line, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(15);
            paint.setColor(INK);
            JSONObject last = entries.get(entries.size() - 1);
            canvas.drawText(weightText(last.optDouble("weight")) + " kg / " + last.optInt("reps") + " reps", dpLocal(18), dpLocal(28), paint);
        }

        private int dpLocal(int value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }

        private String weightText(double value) {
            if (Math.abs(value - Math.round(value)) < 0.001) return String.valueOf((int) Math.round(value));
            return String.format(Locale.US, "%.1f", value);
        }
    }

    public static class WeightChartView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<JSONObject> entries = new ArrayList<>();

        public WeightChartView(Activity activity) {
            super(activity);
        }

        public void setEntries(List<JSONObject> entries) {
            this.entries = entries;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            paint.setColor(PANEL);
            canvas.drawRoundRect(new RectF(0, 0, w, h), 24, 24, paint);
            paint.setColor(Color.rgb(38, 44, 58));
            paint.setStrokeWidth(2);
            for (int i = 1; i < 5; i++) {
                float y = h * i / 5f;
                canvas.drawLine(dpLocal(18), y, w - dpLocal(18), y, paint);
            }
            paint.setTextSize(26);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setColor(WHITE);
            if (entries.isEmpty()) {
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Log weight to start chart", w / 2f, h / 2f, paint);
                return;
            }
            double min = Double.MAX_VALUE, max = 0;
            for (JSONObject entry : entries) {
                double value = entry.optDouble("weight");
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            if (max - min < 1) max = min + 1;
            Path line = new Path();
            for (int i = 0; i < entries.size(); i++) {
                double value = entries.get(i).optDouble("weight");
                float x = dpLocal(22) + (w - dpLocal(44)) * (entries.size() == 1 ? 1 : i / (float) (entries.size() - 1));
                float y = h - dpLocal(32) - (float) ((value - min) / (max - min)) * (h - dpLocal(70));
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
                paint.setColor(LIME);
                canvas.drawCircle(x, y, 6, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(7);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(CYAN);
            canvas.drawPath(line, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(18);
            paint.setColor(MUTED);
            canvas.drawText(String.format(Locale.US, "%.1f kg", entries.get(entries.size() - 1).optDouble("weight")), dpLocal(18), dpLocal(30), paint);
        }

        private int dpLocal(int value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    public class CameraCaptureView extends FrameLayout implements TextureView.SurfaceTextureListener {
        private final TextureView preview;
        private final TextView countdownText;
        private final TextView shutterButton;
        private Camera camera;
        private boolean ready;
        private boolean opening;
        private boolean countingDown;
        private SurfaceTexture activeTexture;

        public CameraCaptureView(Activity activity) {
            super(activity);
            preview = new TextureView(activity);
            addView(preview, new FrameLayout.LayoutParams(-1, -1));
            addView(new OutlineView(activity), new FrameLayout.LayoutParams(-1, -1));
            countdownText = label("", 72, WHITE, true);
            countdownText.setGravity(Gravity.CENTER);
            countdownText.setVisibility(View.GONE);
            countdownText.setBackground(makeRound(Color.argb(92, 0, 0, 0), dp(90), Color.TRANSPARENT));
            FrameLayout.LayoutParams countLp = new FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER);
            addView(countdownText, countLp);

            shutterButton = label("", 22, BLACK, true);
            shutterButton.setGravity(Gravity.CENTER);
            shutterButton.setBackground(makeRound(WHITE, dp(36), RED));
            shutterButton.setOnClickListener(v -> startCountdownCapture());
            FrameLayout.LayoutParams shutterLp = new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            shutterLp.setMargins(0, 0, 0, dp(20));
            addView(shutterButton, shutterLp);
            preview.setSurfaceTextureListener(this);
            if (preview.isAvailable()) startCamera(preview.getSurfaceTexture());
        }

        public void startCountdownCapture() {
            if (countingDown) return;
            countingDown = true;
            runCountdown(5);
        }

        private void runCountdown(int seconds) {
            if (seconds <= 0) {
                countdownText.setText("");
                countdownText.setVisibility(View.GONE);
                countingDown = false;
                capturePhotoNow();
                return;
            }
            countdownText.setText(String.valueOf(seconds));
            countdownText.setVisibility(View.VISIBLE);
            countdownText.animate().scaleX(1.22f).scaleY(1.22f).alpha(0.55f).setDuration(820).withEndAction(() -> {
                countdownText.setScaleX(1f);
                countdownText.setScaleY(1f);
                countdownText.setAlpha(1f);
            }).start();
            handler.postDelayed(() -> runCountdown(seconds - 1), 1000);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startCamera(surface);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            stopCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }

        private void startCamera(SurfaceTexture texture) {
            if (opening) return;
            opening = true;
            stopCamera();
            try {
                activeTexture = texture;
                camera = Camera.open(frontCameraId());
                camera.setDisplayOrientation(90);
                camera.setPreviewTexture(texture);
                Camera.Parameters parameters = camera.getParameters();
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
                Camera.Size size = choosePreviewSize(parameters);
                if (size != null) parameters.setPreviewSize(size.width, size.height);
                try {
                    camera.setParameters(parameters);
                } catch (Exception ignored) {
                }
                camera.startPreview();
                ready = true;
            } catch (Exception e) {
                ready = false;
                stopCamera();
            } finally {
                opening = false;
            }
        }

        public boolean capture() {
            if (!ready && activeTexture != null) startCamera(activeTexture);
            if (!ready || camera == null) return false;
            try {
                camera.takePicture(null, null, (data, cam) -> {
                    saveCameraBytes(data);
                    try {
                        cam.startPreview();
                    } catch (Exception ignored) {
                    }
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private Camera.Size choosePreviewSize(Camera.Parameters parameters) {
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            if (sizes == null || sizes.isEmpty()) return null;
            Camera.Size best = sizes.get(0);
            int target = 1280 * 720;
            int bestDelta = Math.abs(best.width * best.height - target);
            for (Camera.Size size : sizes) {
                int delta = Math.abs(size.width * size.height - target);
                if (delta < bestDelta) {
                    best = size;
                    bestDelta = delta;
                }
            }
            return best;
        }

        public void stopCamera() {
            ready = false;
            countingDown = false;
            if (camera != null) {
                try {
                    camera.stopPreview();
                } catch (Exception ignored) {
                }
                try {
                    camera.release();
                } catch (Exception ignored) {
                }
                camera = null;
            }
        }

        private int frontCameraId() {
            int fallback = 0;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i;
            }
            return fallback;
        }
    }

    public class PhotoFrameView extends FrameLayout {
        private final ImageView image;
        private final OutlineView outline;

        public PhotoFrameView(Activity activity) {
            super(activity);
            setBackground(makeRound(PANEL, dp(24), Color.rgb(46, 52, 68)));
            image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(image, new FrameLayout.LayoutParams(-1, -1));
            outline = new OutlineView(activity);
            addView(outline, new FrameLayout.LayoutParams(-1, -1));
        }

        public void setImageUri(Uri uri) {
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                image.setImageBitmap(bitmap);
            } catch (Exception e) {
                image.setImageURI(uri);
            }
        }
    }

    public static class OutlineView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public OutlineView(Activity activity) {
            super(activity);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(LIME);
            Path path = new Path();
            path.moveTo(w * 0.5f, h * 0.16f);
            path.cubicTo(w * 0.35f, h * 0.18f, w * 0.29f, h * 0.28f, w * 0.28f, h * 0.41f);
            path.cubicTo(w * 0.2f, h * 0.55f, w * 0.23f, h * 0.72f, w * 0.31f, h * 0.86f);
            path.moveTo(w * 0.5f, h * 0.16f);
            path.cubicTo(w * 0.65f, h * 0.18f, w * 0.71f, h * 0.28f, w * 0.72f, h * 0.41f);
            path.cubicTo(w * 0.8f, h * 0.55f, w * 0.77f, h * 0.72f, w * 0.69f, h * 0.86f);
            canvas.drawPath(path, paint);
            paint.setStrokeWidth(2);
            paint.setColor(Color.argb(130, 48, 213, 255));
            canvas.drawLine(w * 0.5f, h * 0.08f, w * 0.5f, h * 0.92f, paint);
            canvas.drawLine(w * 0.18f, h * 0.52f, w * 0.82f, h * 0.52f, paint);
        }
    }

    public static class StepRingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int steps;

        public StepRingView(Activity activity) {
            super(activity);
        }

        public void setSteps(int steps) {
            this.steps = steps;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float size = Math.min(w, h) * 0.72f;
            RectF oval = new RectF(w / 2f - size / 2, h / 2f - size / 2, w / 2f + size / 2, h / 2f + size / 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(28);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(34, 39, 52));
            canvas.drawArc(oval, 135, 270, false, paint);
            paint.setShader(new LinearGradient(oval.left, oval.top, oval.right, oval.bottom, LIME, CYAN, Shader.TileMode.CLAMP));
            float sweep = Math.min(270, steps / 10000f * 270f);
            canvas.drawArc(oval, 135, sweep, false, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(34);
            paint.setColor(WHITE);
            canvas.drawText("10K GOAL", w / 2f, h / 2f + 12, paint);
        }
    }
}

