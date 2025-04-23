package com.example.dailyplanner;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    EditText inputPlan;
    Button generateBtn, showCalendarBtn;
    TextView outputPlan;
    CalendarView calendarView;
    CheckBox acceptPlanCheckbox;
    String selectedDate = "";

    final String API_KEY = "AIzaSyAKny3GMoq2wAof4dt_d6jWUjEXgIwQUt0"; // Replace with your actual key
    final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro-001:generateContent?key=" + API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputPlan = findViewById(R.id.inputPlan);
        generateBtn = findViewById(R.id.generateBtn);
        outputPlan = findViewById(R.id.outputPlan);
        calendarView = findViewById(R.id.calendarView);
        acceptPlanCheckbox = findViewById(R.id.acceptPlanCheckbox);
        showCalendarBtn = findViewById(R.id.showCalendarBtn);

        calendarView.setVisibility(View.GONE);

        showCalendarBtn.setOnClickListener(v -> {
            calendarView.setVisibility(View.VISIBLE);
        });

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            selectedDate = sdf.format(calendar.getTime());

            calendarView.setVisibility(View.GONE);
            showCalendarBtn.setText("Selected Date: " + selectedDate);
        });

        generateBtn.setOnClickListener(view -> {
            String userIdea = inputPlan.getText().toString().trim();
            if (userIdea.isEmpty()) {
                Toast.makeText(this, "Please enter your plan idea", Toast.LENGTH_SHORT).show();
            } else if (selectedDate.isEmpty()) {
                Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
            } else {
                String prompt = "Create a detailed timetable for the day based only on this idea: \"" + userIdea + "\" scheduled on " + selectedDate + ". Do not add any explanations, introductions, guidelines or extra text. Only return the schedule.";
                new GeminiTask().execute(prompt);
            }
        });

        // Load saved plan and date
        SharedPreferences prefs = getSharedPreferences("DailyPlannerPrefs", MODE_PRIVATE);
        String savedPlan = prefs.getString("savedPlan", null);
        String savedDate = prefs.getString("savedDate", null);

        if (savedPlan != null) {
            outputPlan.setText(savedPlan);
            acceptPlanCheckbox.setVisibility(View.VISIBLE);
        }

        if (savedDate != null) {
            showCalendarBtn.setText("Selected Date: " + savedDate);
            selectedDate = savedDate;
        }
    }

    private class GeminiTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            outputPlan.setText("Planning your day...");
            acceptPlanCheckbox.setVisibility(View.GONE);
        }

        @Override
        protected String doInBackground(String... prompts) {
            try {
                JSONObject input = new JSONObject();
                input.put("text", prompts[0]);

                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                contentObj.put("role", "user");
                contentObj.put("parts", new JSONArray().put(input));
                contents.put(contentObj);

                JSONObject body = new JSONObject();
                body.put("contents", contents);

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes());
                os.flush();
                os.close();

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode == 200) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                if (statusCode != 200) {
                    return "Error: " + response.toString();
                }

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray candidates = jsonResponse.getJSONArray("candidates");

                if (candidates.length() > 0) {
                    JSONObject firstCandidate = candidates.getJSONObject(0);
                    JSONObject contentObjResponse = firstCandidate.getJSONObject("content");
                    JSONArray parts = contentObjResponse.getJSONArray("parts");

                    return parts.getJSONObject(0).getString("text");
                } else {
                    return "No valid response from API.";
                }

            } catch (Exception e) {
                e.printStackTrace();
                return "Error generating plan: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.startsWith("Error:")) {
                outputPlan.setText("Failed to generate plan. Please try again.");
            } else {
                outputPlan.setText(result);
                acceptPlanCheckbox.setVisibility(View.VISIBLE);

                // Save plan and date
                SharedPreferences prefs = getSharedPreferences("DailyPlannerPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("savedPlan", result);
                editor.putString("savedDate", selectedDate);
                editor.apply();
            }
        }
    }
}