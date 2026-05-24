package com.gymlevels.app;

import android.Manifest;
import android.app.Activity;
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
import android.view.View;
import android.view.Window;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int BLACK = Color.rgb(7, 8, 12);
    private static final int PANEL = Color.rgb(17, 19, 26);
    private static final int PANEL_2 = Color.rgb(25, 28, 38);
    private static final int WHITE = Color.rgb(247, 248, 250);
    private static final int MUTED = Color.rgb(150, 157, 170);
    private static final int LIME = Color.rgb(183, 255, 42);
    private static final int CYAN = Color.rgb(48, 213, 255);
    private static final int REQ_CAMERA = 17;
    private static final int REQ_CAMERA_PERMISSION = 18;
    private static final int REQ_STEPS_PERMISSION = 19;

    private final String[] tabs = {"Lift", "Body", "Photos", "Steps"};
    private final int[] plateWeights = {20, 15, 10, 5, 2};
    private final int[] plateColors = {
            Color.rgb(41, 175, 255), Color.rgb(242, 199, 68), Color.rgb(70, 235, 128),
            Color.rgb(255, 93, 93), Color.rgb(198, 207, 219)
    };

    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout tabRow;
    private FrameLayout content;
    private int currentTab;
    private final List<ExercisePlan> routine = new ArrayList<>();
    private ExercisePlan selectedExercise;
    private final LinkedHashMap<Integer, Integer> loadedPlates = new LinkedHashMap<>();
    private BarbellView barbellView;
    private TextView loadText;
    private Uri pendingPhotoUri;
    private Handler handler = new Handler();
    private Runnable photoPlayer;

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
        selectedExercise = routine.get(0);
        for (int weight : plateWeights) {
            loadedPlates.put(weight, 0);
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
        handler.removeCallbacksAndMessages(null);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(66)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        top.addView(titles, new LinearLayout.LayoutParams(0, -1, 1));

        TextView name = label("GYMLEVELS", 30, WHITE, true);
        titles.addView(name);
        TextView sub = label("LOCAL TRAINING OS", 11, MUTED, false);
        sub.setLetterSpacing(0.12f);
        titles.addView(sub);

        TextView badge = pill("NO LOGIN");
        top.addView(badge);

        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER);
        root.addView(tabRow, new LinearLayout.LayoutParams(-1, dp(48)));
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            TextView tab = pill(tabs[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> showTab(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            tabRow.addView(tab, lp);
        }

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showTab(int index) {
        currentTab = index;
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView tab = (TextView) tabRow.getChildAt(i);
            tab.setTextColor(i == currentTab ? BLACK : WHITE);
            tab.setBackground(makeRound(i == currentTab ? LIME : PANEL_2, dp(18), i == currentTab ? LIME : Color.rgb(45, 50, 64)));
        }
        content.removeAllViews();
        handler.removeCallbacksAndMessages(null);
        if (index == 0) buildLift();
        if (index == 1) buildBody();
        if (index == 2) buildPhotos();
        if (index == 3) buildSteps();
    }

    private void buildLift() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        TextView title = label("Today Routine", 24, WHITE, true);
        page.addView(title);
        TextView subtitle = label("Beginner strength split", 13, MUTED, false);
        page.addView(subtitle);

        HorizontalScrollView exerciseScroller = new HorizontalScrollView(this);
        exerciseScroller.setHorizontalScrollBarEnabled(false);
        LinearLayout exerciseRow = new LinearLayout(this);
        exerciseRow.setOrientation(LinearLayout.HORIZONTAL);
        exerciseScroller.addView(exerciseRow);
        page.addView(exerciseScroller, new LinearLayout.LayoutParams(-1, dp(56)));

        for (ExercisePlan exercise : routine) {
            TextView chip = pill(exercise.name);
            chip.setTextColor(exercise == selectedExercise ? BLACK : WHITE);
            chip.setBackground(makeRound(exercise == selectedExercise ? LIME : PANEL_2, dp(18), Color.rgb(48, 53, 68)));
            chip.setOnClickListener(v -> {
                selectedExercise = exercise;
                showTab(0);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.setMargins(0, dp(10), dp(8), 0);
            exerciseRow.addView(chip, lp);
        }

        TextView prescription = label(selectedExercise.displayCue(), 14, WHITE, false);
        prescription.setPadding(dp(14), 0, dp(14), 0);
        prescription.setGravity(Gravity.CENTER_VERTICAL);
        prescription.setBackground(makeRound(PANEL, dp(12), Color.rgb(46, 52, 68)));
        LinearLayout.LayoutParams rxLp = new LinearLayout.LayoutParams(-1, dp(54));
        rxLp.setMargins(0, dp(2), 0, 0);
        page.addView(prescription, rxLp);

        LinearLayout learningRow = new LinearLayout(this);
        learningRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams learnLp = new LinearLayout.LayoutParams(-1, dp(210));
        learnLp.setMargins(0, dp(12), 0, 0);
        page.addView(learningRow, learnLp);

        MuscleMapView muscleMap = new MuscleMapView(this);
        muscleMap.setMuscleGroup(selectedExercise.muscleGroup);
        learningRow.addView(muscleMap, new LinearLayout.LayoutParams(0, -1, 1));
        addGap(learningRow, 10, true);

        LinearLayout infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        infoPanel.setBackground(makeRound(PANEL, dp(14), Color.rgb(46, 52, 68)));
        learningRow.addView(infoPanel, new LinearLayout.LayoutParams(0, -1, 1));
        infoPanel.addView(label(selectedExercise.day, 12, CYAN, true));
        infoPanel.addView(label(selectedExercise.muscleGroup.toUpperCase(Locale.US), 16, WHITE, true));
        TextView cue = label(selectedExercise.cue, 13, MUTED, false);
        cue.setPadding(0, dp(8), 0, dp(10));
        infoPanel.addView(cue, new LinearLayout.LayoutParams(-1, 0, 1));
        Button video = actionButton("Watch Form");
        video.setTextColor(BLACK);
        video.setBackground(makeRound(CYAN, dp(14), CYAN));
        video.setOnClickListener(v -> openVideo(selectedExercise.videoUrl));
        infoPanel.addView(video, new LinearLayout.LayoutParams(-1, dp(44)));

        barbellView = new BarbellView(this);
        barbellView.setPlateState(loadedPlates, plateColors);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(290));
        barLp.setMargins(0, dp(16), 0, dp(8));
        page.addView(barbellView, barLp);

        loadText = label("", 16, WHITE, true);
        loadText.setGravity(Gravity.CENTER);
        page.addView(loadText, new LinearLayout.LayoutParams(-1, dp(34)));
        updateLoadText();

        LinearLayout plateRow = new LinearLayout(this);
        plateRow.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(plateRow, new LinearLayout.LayoutParams(-1, dp(58)));
        for (int i = 0; i < plateWeights.length; i++) {
            int weight = plateWeights[i];
            TextView plate = pill(weight + " kg");
            plate.setTextColor(BLACK);
            plate.setBackground(makeRound(plateColors[i], dp(16), Color.TRANSPARENT));
            plate.setOnClickListener(v -> {
                loadedPlates.put(weight, loadedPlates.get(weight) + 1);
                barbellView.invalidate();
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
            barbellView.invalidate();
            updateLoadText();
        });
        controls.addView(unload, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(controls, 8, true);

        EditText reps = input("Reps");
        controls.addView(reps, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(controls, 8, true);

        Button save = actionButton("Save Set");
        save.setTextColor(BLACK);
        save.setBackground(makeRound(LIME, dp(14), LIME));
        save.setOnClickListener(v -> saveSet(reps.getText().toString()));
        controls.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1));

        page.addView(sectionTitle("Recent Sets"));
        List<JSONObject> setEntries = readArrayObjects("sets");
        for (int i = setEntries.size() - 1; i >= 0; i--) {
            JSONObject entry = setEntries.get(i);
            page.addView(historyLine(entry.optString("exercise"), entry.optString("weight") + " kg x " + entry.optString("reps"), entry.optString("date")));
        }
    }

    private void buildBody() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = page();
        scroll.addView(page);
        content.addView(scroll);

        page.addView(label("Body Weight", 24, WHITE, true));
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
        save.setBackground(makeRound(CYAN, dp(14), CYAN));
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
                showTab(1);
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

        page.addView(label("Progress Photos", 24, WHITE, true));
        page.addView(label("Same outline, same distance, every day", 13, MUTED, false));

        PhotoFrameView frame = new PhotoFrameView(this);
        List<JSONObject> photos = readArrayObjects("photos");
        if (!photos.isEmpty()) frame.setImageUri(Uri.parse(photos.get(photos.size() - 1).optString("uri")));
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(-1, dp(380));
        frameLp.setMargins(0, dp(16), 0, dp(12));
        page.addView(frame, frameLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(actions, new LinearLayout.LayoutParams(-1, dp(56)));

        Button capture = actionButton("Capture Today");
        capture.setTextColor(BLACK);
        capture.setBackground(makeRound(LIME, dp(14), LIME));
        capture.setOnClickListener(v -> capturePhoto());
        actions.addView(capture, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(actions, 10, true);
        Button play = actionButton("Timelapse");
        play.setOnClickListener(v -> playPhotos(frame, photos));
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(46), 1));

        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout thumbs = new LinearLayout(this);
        thumbs.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(thumbs);
        page.addView(strip, new LinearLayout.LayoutParams(-1, dp(112)));
        for (JSONObject photo : photos) {
            Uri uri = Uri.parse(photo.optString("uri"));
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setImageURI(uri);
            thumb.setBackground(makeRound(PANEL_2, dp(10), Color.rgb(52, 58, 72)));
            thumb.setOnClickListener(v -> frame.setImageUri(uri));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(dp(82), dp(96));
            tLp.setMargins(0, dp(8), dp(8), dp(8));
            thumbs.addView(thumb, tLp);
        }
    }

    private void buildSteps() {
        LinearLayout page = page();
        content.addView(page);
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        page.addView(label("Steps", 24, WHITE, true));
        page.addView(label(stepCounter == null ? "No step counter sensor found" : "Native on-device sensor", 13, MUTED, false));

        stepRingView = new StepRingView(this);
        stepRingView.setSteps(latestSteps < 0 ? prefs.getInt("today_steps", 0) : latestSteps);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(-1, dp(340));
        ringLp.setMargins(0, dp(24), 0, dp(10));
        page.addView(stepRingView, ringLp);

        stepText = label(stepLabel(), 42, WHITE, true);
        stepText.setGravity(Gravity.CENTER);
        page.addView(stepText, new LinearLayout.LayoutParams(-1, dp(64)));

        Button permission = actionButton("Enable Step Access");
        permission.setTextColor(BLACK);
        permission.setBackground(makeRound(CYAN, dp(14), CYAN));
        permission.setOnClickListener(v -> requestStepPermission());
        page.addView(permission, new LinearLayout.LayoutParams(-1, dp(48)));
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
            obj.put("weight", totalLoad());
            obj.put("reps", reps);
            append("sets", obj);
            toast("Set saved");
            showTab(0);
        } catch (JSONException ignored) {
        }
    }

    private void capturePhoto() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
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
                showTab(2);
            } catch (JSONException ignored) {
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            capturePhoto();
        }
        if (requestCode == REQ_STEPS_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (stepCounter != null) {
                sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            }
            showTab(3);
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
        try {
            String json = readAssetText("config.json");
            JSONArray exercises = new JSONObject(json).optJSONArray("exercises");
            if (exercises != null) {
                for (int i = 0; i < exercises.length(); i++) {
                    JSONObject item = exercises.optJSONObject(i);
                    if (item == null) continue;
                    String name = item.optString("name", "").trim();
                    if (name.isEmpty()) continue;
                    routine.add(new ExercisePlan(
                            name,
                            item.optString("day", "Today"),
                            item.optInt("sets", 3),
                            item.optString("reps", "8-12"),
                            item.optString("cue", "Move with control and stop if anything hurts."),
                            item.optString("videoUrl", ""),
                            item.optString("muscleGroup", "full body")
                    ));
                }
            }
        } catch (Exception ignored) {
        }
        if (routine.isEmpty()) {
            routine.add(new ExercisePlan("Goblet Squat", "Monday", 3, "8-12", "Control depth and drive through mid-foot.", "https://www.youtube.com/results?search_query=goblet+squat+proper+form", "quads, glutes, core"));
            routine.add(new ExercisePlan("Bench Press", "Monday", 3, "6-10", "Lock shoulder blades and keep a smooth bar path.", "https://www.youtube.com/results?search_query=bench+press+proper+form", "chest, triceps, shoulders"));
            routine.add(new ExercisePlan("Lat Pulldown", "Wednesday", 3, "10-12", "Pull elbows down and pause near the upper chest.", "https://www.youtube.com/results?search_query=lat+pulldown+proper+form", "back, biceps"));
        }
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

    private int totalLoad() {
        int total = 20;
        for (Map.Entry<Integer, Integer> entry : loadedPlates.entrySet()) {
            total += entry.getKey() * entry.getValue() * 2;
        }
        return total;
    }

    private void updateLoadText() {
        loadText.setText(selectedExercise.name + "  |  " + totalLoad() + " kg");
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

    private TextView sectionTitle(String text) {
        TextView view = label(text, 16, WHITE, true);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView historyLine(String left, String middle, String right) {
        TextView row = label(left + (middle.isEmpty() ? "" : "  •  " + middle) + (right.isEmpty() ? "" : "  •  " + right), 14, WHITE, false);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(makeRound(PANEL, dp(10), Color.rgb(40, 44, 58)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView pill(String text) {
        TextView view = label(text, 12, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackground(makeRound(PANEL_2, dp(18), Color.rgb(48, 53, 68)));
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(WHITE);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackground(makeRound(PANEL_2, dp(14), Color.rgb(50, 55, 70)));
        return button;
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextColor(WHITE);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(14);
        edit.setSingleLine(true);
        edit.setGravity(Gravity.CENTER);
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setBackground(makeRound(PANEL, dp(14), Color.rgb(50, 55, 70)));
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

    public static class MuscleMapView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String muscleGroup = "";

        public MuscleMapView(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        public void setMuscleGroup(String muscleGroup) {
            this.muscleGroup = muscleGroup == null ? "" : muscleGroup.toLowerCase(Locale.US);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PANEL);
            canvas.drawRoundRect(new RectF(0, 0, w, h), 22, 22, paint);

            paint.setColor(Color.rgb(41, 47, 62));
            paint.setStrokeWidth(5);
            paint.setStrokeCap(Paint.Cap.ROUND);
            float cx = w / 2f;
            float headR = h * 0.075f;
            canvas.drawCircle(cx, h * 0.14f, headR, paint);
            canvas.drawRoundRect(new RectF(cx - w * 0.11f, h * 0.22f, cx + w * 0.11f, h * 0.55f), 30, 30, paint);
            canvas.drawLine(cx - w * 0.14f, h * 0.27f, cx - w * 0.3f, h * 0.49f, paint);
            canvas.drawLine(cx + w * 0.14f, h * 0.27f, cx + w * 0.3f, h * 0.49f, paint);
            canvas.drawLine(cx - w * 0.06f, h * 0.55f, cx - w * 0.17f, h * 0.86f, paint);
            canvas.drawLine(cx + w * 0.06f, h * 0.55f, cx + w * 0.17f, h * 0.86f, paint);

            highlight(canvas, "shoulders", new RectF(cx - w * 0.22f, h * 0.23f, cx - w * 0.08f, h * 0.34f));
            highlight(canvas, "shoulders", new RectF(cx + w * 0.08f, h * 0.23f, cx + w * 0.22f, h * 0.34f));
            highlight(canvas, "chest", new RectF(cx - w * 0.105f, h * 0.27f, cx + w * 0.105f, h * 0.39f));
            highlight(canvas, "back", new RectF(cx - w * 0.12f, h * 0.34f, cx + w * 0.12f, h * 0.5f));
            highlight(canvas, "core", new RectF(cx - w * 0.09f, h * 0.42f, cx + w * 0.09f, h * 0.58f));
            highlight(canvas, "biceps", new RectF(cx - w * 0.31f, h * 0.35f, cx - w * 0.22f, h * 0.53f));
            highlight(canvas, "biceps", new RectF(cx + w * 0.22f, h * 0.35f, cx + w * 0.31f, h * 0.53f));
            highlight(canvas, "triceps", new RectF(cx - w * 0.28f, h * 0.28f, cx - w * 0.19f, h * 0.48f));
            highlight(canvas, "triceps", new RectF(cx + w * 0.19f, h * 0.28f, cx + w * 0.28f, h * 0.48f));
            highlight(canvas, "quads", new RectF(cx - w * 0.17f, h * 0.58f, cx - w * 0.05f, h * 0.79f));
            highlight(canvas, "quads", new RectF(cx + w * 0.05f, h * 0.58f, cx + w * 0.17f, h * 0.79f));
            highlight(canvas, "hamstrings", new RectF(cx - w * 0.15f, h * 0.67f, cx - w * 0.05f, h * 0.86f));
            highlight(canvas, "hamstrings", new RectF(cx + w * 0.05f, h * 0.67f, cx + w * 0.15f, h * 0.86f));
            highlight(canvas, "glutes", new RectF(cx - w * 0.13f, h * 0.53f, cx + w * 0.13f, h * 0.66f));

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(24);
            paint.setColor(WHITE);
            canvas.drawText("TARGET", cx, h - 20, paint);
        }

        private void highlight(Canvas canvas, String key, RectF rect) {
            if (!muscleGroup.contains(key)) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, LIME, CYAN, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, 28, 28, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.argb(190, 255, 255, 255));
            canvas.drawRoundRect(rect, 28, 28, paint);
        }
    }

    public static class BarbellView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinkedHashMap<Integer, Integer> plates = new LinkedHashMap<>();
        private int[] colors = new int[0];

        public BarbellView(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        public void setPlateState(LinkedHashMap<Integer, Integer> plates, int[] colors) {
            this.plates = plates;
            this.colors = colors;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            paint.setShader(new LinearGradient(0, 0, w, h, Color.rgb(16, 20, 32), Color.rgb(4, 5, 9), Shader.TileMode.CLAMP));
            RectF bg = new RectF(0, 0, w, h);
            canvas.drawRoundRect(bg, 28, 28, paint);
            paint.setShader(null);

            paint.setColor(Color.rgb(28, 34, 48));
            for (int i = 0; i < 9; i++) {
                float y = h * 0.22f + i * h * 0.065f;
                canvas.drawLine(w * 0.12f, y, w * 0.88f, y + h * 0.08f, paint);
            }

            float cx = w / 2f;
            float cy = h * 0.55f;
            paint.setStrokeWidth(18);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(204, 212, 222));
            canvas.drawLine(w * 0.14f, cy, w * 0.86f, cy, paint);
            paint.setStrokeWidth(5);
            paint.setColor(Color.rgb(82, 93, 112));
            canvas.drawLine(w * 0.14f, cy - 12, w * 0.86f, cy - 12, paint);

            drawSleeve(canvas, w * 0.23f, cy);
            drawSleeve(canvas, w * 0.77f, cy);

            int colorIndex = 0;
            float leftX = w * 0.29f;
            float rightX = w * 0.71f;
            for (Map.Entry<Integer, Integer> entry : plates.entrySet()) {
                int count = entry.getValue();
                int color = colors.length > colorIndex ? colors[colorIndex] : Color.LTGRAY;
                float plateH = 46 + entry.getKey() * 2.8f;
                for (int i = 0; i < count; i++) {
                    drawPlate(canvas, leftX - i * 15, cy, plateH, color, false);
                    drawPlate(canvas, rightX + i * 15, cy, plateH, color, true);
                }
                leftX -= count * 15 + 4;
                rightX += count * 15 + 4;
                colorIndex++;
            }

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(34);
            paint.setColor(WHITE);
            canvas.drawText("LOADOUT", cx, h * 0.18f, paint);
        }

        private void drawSleeve(Canvas canvas, float x, float cy) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(142, 153, 170));
            canvas.drawRoundRect(new RectF(x - 26, cy - 18, x + 26, cy + 18), 14, 14, paint);
            paint.setColor(Color.rgb(229, 235, 242));
            canvas.drawOval(new RectF(x - 28, cy - 20, x + 2, cy + 20), paint);
        }

        private void drawPlate(Canvas canvas, float x, float cy, float height, int color, boolean right) {
            float width = 20;
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
